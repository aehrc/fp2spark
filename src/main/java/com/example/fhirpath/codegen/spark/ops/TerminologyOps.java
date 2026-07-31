package com.example.fhirpath.codegen.spark.ops;

import static org.apache.spark.sql.functions.exists;
import static org.apache.spark.sql.functions.lit;
import static org.apache.spark.sql.functions.transform;
import static org.apache.spark.sql.functions.when;

import com.example.fhirpath.codegen.spark.SparkOpContext;
import com.example.fhirpath.codegen.spark.SparkOperationRegistry;
import com.example.fhirpath.codegen.spark.udf.MemberOf;
import com.example.fhirpath.terminology.TerminologyServiceFactory;
import com.example.fhirpath.typing.FhirComplexType;
import com.example.fhirpath.typing.SystemType;
import com.example.fhirpath.typing.Type;
import jakarta.annotation.Nonnull;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.expressions.UserDefinedFunction;

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

  /** FHIR type name of a single coded value. */
  private static final String CODING_TYPE_NAME = "Coding";

  /** FHIR type name of a concept, which carries zero or more codings. */
  private static final String CODEABLE_CONCEPT_TYPE_NAME = "CodeableConcept";

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

    // memberOf(valueSet) — tests a Coding or CodeableConcept for value set membership
    registry.register("memberOf", ctx -> generateMemberOf(ctx, memberOf));
  }

  /**
   * Generates {@code memberOf(valueSet)}.
   *
   * <p>A {@code Coding} input is tested directly. A {@code CodeableConcept} input is true if any of
   * its codings is a member, evaluated with Spark's {@code exists} over the {@code coding} array.
   * {@code exists} uses three-valued logic, so an unresolvable value set — for which every element
   * yields null — correctly propagates as an empty result rather than as false.
   */
  @Nonnull
  private static Column generateMemberOf(
      @Nonnull final SparkOpContext ctx, @Nonnull final UserDefinedFunction memberOf) {
    final Column input = ctx.arg(0);
    final Column valueSetUrl = ctx.arg(1);
    final Type inputType = ctx.argType(0);

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
      final Column memberships =
          transform(codings, coding -> applyToCoding(memberOf, coding, valueSetUrl));
      return when(input.isNull(), lit(null))
          // A concept carrying no codings has no code that could be a member.
          .when(codings.isNull(), lit(false))
          .otherwise(exists(memberships, membership -> membership));
    }

    throw new IllegalArgumentException(
        "memberOf() requires a Coding or CodeableConcept input, but got: " + inputType);
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
        || (type instanceof final FhirComplexType complex
            && CODING_TYPE_NAME.equals(complex.getName()));
  }

  /** Returns true if the type is a FHIR {@code CodeableConcept}. */
  private static boolean isCodeableConcept(@Nonnull final Type type) {
    return type instanceof final FhirComplexType complex
        && CODEABLE_CONCEPT_TYPE_NAME.equals(complex.getName());
  }
}
