package com.example.fhirpath.codegen.spark.ops;

import static com.example.fhirpath.codegen.spark.SparkDefs.binary;
import static com.example.fhirpath.codegen.spark.SparkDefs.ternary;
import static com.example.fhirpath.codegen.spark.SparkDefs.unary;
import static org.apache.spark.sql.functions.call_function;
import static org.apache.spark.sql.functions.lit;
import static org.apache.spark.sql.functions.regexp_replace;
import static org.apache.spark.sql.functions.when;

import com.example.fhirpath.codegen.spark.SparkOpContext;
import com.example.fhirpath.codegen.spark.SparkOperationRegistry;
import jakarta.annotation.Nonnull;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.functions;

/**
 * String function registrations (length, upper, lower, trim, startsWith, endsWith, contains,
 * indexOf, substring, replace, matches, replaceMatches, split, join, toChars).
 */
public final class StringOps {

  private StringOps() {}

  /** Registers all string functions into the given registry. */
  public static void register(@Nonnull final SparkOperationRegistry registry) {
    // Simple unary functions
    registry.register("length", unary(functions::length));
    registry.register("upper", unary(functions::upper));
    registry.register("lower", unary(functions::lower));
    registry.register("trim", unary(functions::trim));

    // Binary functions returning Boolean
    registry.register("startsWith", binary(Column::startsWith));
    registry.register("endsWith", binary(Column::endsWith));
    registry.register("contains", binary(Column::contains));

    // indexOf: 0-based; Spark locate() is 1-based and returns 0 for not-found
    registry.register(
        "indexOf", ctx -> call_function("locate", ctx.arg(1), ctx.arg(0)).minus(lit(1)));

    // substring(start, [length]): 0-based start; Spark substring is 1-based
    registry.register("substring", StringOps::generateSubstring);

    // replace(pattern, substitution): literal string replacement
    // FHIRPath spec: empty pattern surrounds each char with substitution
    // ('abc'.replace('','x')→'xaxbxcx')
    // Spark's replace() doesn't handle empty pattern this way, so we use regexp_replace with
    // a quoted (literal) pattern, but special-case empty pattern via a zero-width match.
    registry.register("replace", StringOps::generateReplace);

    // matches(regex): partial match using rlike
    registry.register("matches", binary(functions::rlike));

    // replaceMatches(regex, substitution): regex replacement
    registry.register("replaceMatches", ternary(functions::regexp_replace));

    // split(separator): returns *STRING
    // FHIRPath spec treats separator as a literal string, not a regex.
    // Spark's split() interprets the pattern as a regex, so we quote it with \Q...\E.
    registry.register(
        "split", ctx -> functions.split(ctx.arg(0), StringSupport.quoteLiteral(ctx.arg(1))));

    // join([separator]): *STRING → ?STRING
    registry.register("join", StringOps::generateJoin);

    // toChars(): returns *STRING (split into individual characters)
    registry.register("toChars", StringOps::generateToChars);
  }

  /**
   * substring(start, [length]): FHIRPath uses 0-based start, Spark uses 1-based.
   *
   * <p>Per spec:
   *
   * <ul>
   *   <li>If start is outside string length, returns empty ({})
   *   <li>If length is empty (omitted), returns from start to end
   *   <li>If length is zero or negative, returns empty string ('')
   * </ul>
   */
  @Nonnull
  private static Column generateSubstring(@Nonnull final SparkOpContext ctx) {
    final Column input = ctx.arg(0);
    final Column start = ctx.arg(1);
    final Column len = ctx.arg(2); // null when omitted (variadic padding)
    final Column strLen = functions.length(input);

    // start < 0 or start >= length → empty collection (null)
    final Column outOfBounds = start.lt(lit(0)).or(start.geq(strLen));

    // Spark's substring is 1-based, so add 1
    final Column sparkStart = start.plus(lit(1));

    // When length is omitted (null), use remaining length
    final Column effectiveLen = when(len.isNull(), strLen.minus(start)).otherwise(len);

    // length <= 0 → empty string
    return when(outOfBounds, lit(null))
        .when(effectiveLen.leq(lit(0)), lit(""))
        .otherwise(functions.substring(input, sparkStart, effectiveLen));
  }

  /**
   * join([separator]): joins a string collection into a single string.
   *
   * <p>For arrays: uses Spark's array_join(). For singular: returns the value as-is.
   */
  @Nonnull
  private static Column generateJoin(@Nonnull final SparkOpContext ctx) {
    final Column sep = functions.coalesce(ctx.arg(1), lit("")); // default to empty string
    return ctx.collectionArg(0).apply(c -> call_function("array_join", c, sep), c -> c);
  }

  /**
   * replace(pattern, substitution): FHIRPath literal string replacement.
   *
   * <p>Special case: empty pattern surrounds each character with the substitution, e.g. {@code
   * 'abc'.replace('', 'x')} yields {@code 'xaxbxcx'}. Spark's {@code replace()} does not handle
   * this, so we use {@code regexp_replace} with a zero-width match for the empty-pattern case.
   */
  @Nonnull
  private static Column generateReplace(@Nonnull final SparkOpContext ctx) {
    final Column input = ctx.arg(0);
    final Column pattern = ctx.arg(1);
    final Column substitution = ctx.arg(2);

    // Empty pattern: use regexp_replace with a zero-width match to insert between every char
    // Non-empty pattern: use Spark's literal replace
    return when(
            functions.length(pattern).equalTo(lit(0)), regexp_replace(input, lit(""), substitution))
        .otherwise(functions.replace(input, pattern, substitution));
  }

  /**
   * toChars(): splits a string into individual characters.
   *
   * <p>Returns empty ({}) when input is empty. Uses a lookahead/lookbehind regex to split between
   * characters without producing empty leading/trailing elements.
   */
  @Nonnull
  private static Column generateToChars(@Nonnull final SparkOpContext ctx) {
    final Column input = ctx.arg(0);
    final Column result = functions.split(input, "(?<=.)(?=.)");
    // Empty string → empty collection (null)
    return when(functions.length(input).equalTo(lit(0)), lit(null)).otherwise(result);
  }
}
