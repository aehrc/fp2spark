package com.example.fhirpath.codegen.spark.ops;

import static org.apache.spark.sql.functions.coalesce;
import static org.apache.spark.sql.functions.exists;
import static org.apache.spark.sql.functions.lit;
import static org.apache.spark.sql.functions.when;

import com.example.fhirpath.analyzer.UnsupportedFeatureException;
import com.example.fhirpath.codegen.spark.SparkOpContext;
import com.example.fhirpath.codegen.spark.SparkOperationRegistry;
import com.example.fhirpath.codegen.spark.udf.MemberOf;
import com.example.fhirpath.terminology.NoTerminologyService;
import com.example.fhirpath.terminology.TerminologyServiceFactory;
import com.example.fhirpath.typing.FhirComplexType;
import com.example.fhirpath.typing.SystemType;
import com.example.fhirpath.typing.Type;
import jakarta.annotation.Nonnull;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.expressions.UserDefinedFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Terminology function registrations, defined in the FHIR-specific FHIRPath binding.
 *
 * <p>Unlike the other {@code *Ops} classes these registrations are configuration-dependent: they
 * close over a {@link TerminologyServiceFactory} so the generated UDF can reach a terminology
 * server on Spark executors.
 *
 * @see <a href="https://hl7.org/fhir/R4/fhirpath.html#functions">FHIR specification — Additional
 *     functions</a>
 */
public final class TerminologyOps {

  private static final Logger log = LoggerFactory.getLogger(TerminologyOps.class);

  /** Name of the {@code CodeableConcept} field holding its codings. */
  private static final String CODING_FIELD = "coding";

  private TerminologyOps() {}

  /**
   * Registers the terminology functions into the given registry.
   *
   * @param registry the registry to register operations into
   * @param terminologyServiceFactory the factory used to reach a terminology server
   */
  public static void register(
      @Nonnull final SparkOperationRegistry registry,
      @Nonnull final TerminologyServiceFactory terminologyServiceFactory) {
    // The UDF is built once per registry rather than per expression, so a single instance (and
    // therefore a single captured factory) is shared by every memberOf() call site.
    final UserDefinedFunction memberOf = MemberOf.udf(terminologyServiceFactory);
    final boolean terminologyConfigured =
        terminologyServiceFactory != NoTerminologyService.INSTANCE;

    // memberOf(valueSet) — tests a Coding or CodeableConcept for value set membership
    registry.register("memberOf", ctx -> generateMemberOf(ctx, memberOf, terminologyConfigured));
  }

  /**
   * Warns that a {@code memberOf()} call site was compiled with no terminology server, and will
   * therefore report every value set as unresolvable.
   *
   * <p>Emitted at code generation time — once per compiled call site, not per row. An empty result
   * is what the specification requires for an unresolvable value set, so this cannot be an error;
   * but empty is also falsy inside {@code where()}, so the visible symptom is a query that silently
   * returns nothing. The javadoc on {@link NoTerminologyService} does not help someone reading that
   * output, and this message does.
   */
  private static void warnTerminologyNotConfigured() {
    log.warn(
        "memberOf() was compiled without a terminology server, so every value set is reported as"
            + " unresolvable and the result is empty for every input. Inside where() this silently"
            + " excludes all elements. Supply a TerminologyServiceFactory via CompilationOptions —"
            + " for example FhirPath.toColumn(expression, context, resourceType,"
            + " CompilationOptions.defaults().withTerminologyServiceFactory("
            + "DefaultTerminologyServiceFactory.forServer(url))).");
  }

  /**
   * Generates {@code memberOf(valueSet)}.
   *
   * <p>A {@code Coding} input is tested directly. A {@code CodeableConcept} input is true if any of
   * its codings is a member — see {@link #anyCodingIsMember} for how an unresolvable value set
   * correctly propagates as an empty result rather than as false.
   */
  @Nonnull
  private static Column generateMemberOf(
      @Nonnull final SparkOpContext ctx,
      @Nonnull final UserDefinedFunction memberOf,
      final boolean terminologyConfigured) {
    final Column input = ctx.arg(0);
    final Column valueSetUrl = ctx.arg(1);
    final Type inputType = ctx.argType(0);

    if (!terminologyConfigured) {
      warnTerminologyNotConfigured();
    }

    if (inputType == SystemType.NULL) {
      // An empty input collection yields an empty result, with no membership test to perform.
      return lit(null);
    }
    if (isCoding(inputType)) {
      // A null Coding is an empty collection, which yields an empty result.
      return when(input.isNotNull(), applyToCoding(memberOf, input, valueSetUrl));
    }
    if (isCodeableConcept(inputType)) {
      final Column codings = input.getField(CODING_FIELD);
      // The empty-argument check must precede the coding-less branch below: an empty value set
      // yields empty regardless of the input, and unlike the has-codings path that branch never
      // reaches the UDF, where the argument would otherwise be checked. The argument is tested by
      // column rather than by static type because the analyzer coerces an empty literal to the
      // declared STRING parameter type, so it does not arrive typed NULL.
      return when(input.isNull().or(valueSetUrl.isNull()), lit(null))
          // A concept carrying no codings has no code that could be a member, so "any code in the
          // concept is a member" is vacuously false. Pathling yields empty here instead — see
          // SPEC_DIVERGENCES P2. HAPI's encoder writes an absent coding list as null rather than an
          // empty array, so this branch is what a text-only concept actually hits.
          .when(codings.isNull(), lit(false))
          .otherwise(anyCodingIsMember(memberOf, codings, valueSetUrl));
    }

    // The specification also defines memberOf() on a bare string/code, where the answer depends on
    // the value set containing exactly one code system. That requires ValueSet expansion
    // introspection and is deliberately not implemented yet — see #279.
    //
    // This throw is itself a known gap: memberOf()'s signature is registered as ANY (see
    // OperationRegistry), so an unsupported input type like this reaches code generation instead
    // of being rejected during analysis, against ARCHITECTURE.md Principle 5 — see #284.
    throw new UnsupportedFeatureException(
        "memberOf() on " + inputType + " input",
        "only Coding and CodeableConcept input is supported; code- and string-valued input is"
            + " tracked by #279",
        null);
  }

  /**
   * Tests whether any coding in {@code codings} is a member of the value set, propagating an
   * unresolvable value set (every coding's result is null) as null rather than false.
   *
   * <p>This does not use a single {@code exists(codings, coding -> applyToCoding(...))} call,
   * because {@code exists()}'s null-propagation is ambiguous — and governed by {@code
   * spark.sql.legacy.followThreeValuedLogicInArrayExists} — only when its predicate itself can
   * evaluate to null. Splitting into two boolean-only predicates ({@code coalesce(...)} and {@code
   * isNull()} can each only yield true or false, never null) sidesteps that ambiguity entirely, so
   * the result no longer depends on a Spark session config this library doesn't control.
   *
   * <p>{@code anyTrue} keeps {@code exists()}'s short-circuit on the first matching coding, so a
   * concept whose first coding matches still costs one terminology lookup. {@code anyNull} only
   * runs a second pass when no coding matched, and by then {@code CachingTerminologyService} has
   * already cached every coding's result from the first pass.
   */
  @Nonnull
  private static Column anyCodingIsMember(
      @Nonnull final UserDefinedFunction memberOf,
      @Nonnull final Column codings,
      @Nonnull final Column valueSetUrl) {
    final Column anyTrue =
        exists(
            codings, coding -> coalesce(applyToCoding(memberOf, coding, valueSetUrl), lit(false)));
    final Column anyNull =
        exists(codings, coding -> applyToCoding(memberOf, coding, valueSetUrl).isNull());
    return when(anyTrue, lit(true)).when(anyNull, lit(null)).otherwise(lit(false));
  }

  /** Applies the membership UDF to a single Coding struct column. */
  @Nonnull
  private static Column applyToCoding(
      @Nonnull final UserDefinedFunction memberOf,
      @Nonnull final Column coding,
      @Nonnull final Column valueSetUrl) {
    return memberOf.apply(
        coding.getField("system"),
        coding.getField("code"),
        coding.getField("version"),
        valueSetUrl);
  }

  /**
   * Returns true if the type is a single coded value. Coding literals resolve to {@link
   * SystemType#CODING}, while traversal into a FHIR resource yields a {@link FhirComplexType} —
   * both share the {@code (system, code, version, …)} struct layout.
   */
  private static boolean isCoding(@Nonnull final Type type) {
    return type == SystemType.CODING
        || (type instanceof final FhirComplexType complex && complex.isCodingCompatible());
  }

  /** Returns true if the type is a FHIR {@code CodeableConcept}. */
  private static boolean isCodeableConcept(@Nonnull final Type type) {
    return type instanceof final FhirComplexType complex && complex.isCodeableConcept();
  }
}
