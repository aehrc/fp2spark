package com.example.fhirpath.codegen.spark.ops;

import static org.apache.spark.sql.functions.call_function;
import static org.apache.spark.sql.functions.coalesce;
import static org.apache.spark.sql.functions.concat;
import static org.apache.spark.sql.functions.lit;
import static org.apache.spark.sql.functions.regexp_replace;
import static org.apache.spark.sql.functions.when;

import jakarta.annotation.Nonnull;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.functions;

/** Spark column expression helpers for string operations. */
final class StringSupport {

  private StringSupport() {}

  /**
   * String concatenation using the {@code &} operator. Treats null/empty operands as empty strings,
   * per the FHIRPath spec.
   */
  @Nonnull
  static Column stringConcat(@Nonnull final Column left, @Nonnull final Column right) {
    return concat(coalesce(left, lit("")), coalesce(right, lit("")));
  }

  /**
   * Wraps a column value with {@code \Q...\E} to quote regex metacharacters, making it safe to use
   * as a literal pattern in Spark regex functions like {@code split()}.
   */
  @Nonnull
  static Column quoteLiteral(@Nonnull final Column pattern) {
    return concat(lit("\\Q"), pattern, lit("\\E"));
  }

  /**
   * FHIRPath indexOf: returns the 0-based index of the first occurrence of {@code substring} in
   * {@code input}, or -1 if not found. Adapts Spark's 1-based {@code locate()} by subtracting 1.
   */
  @Nonnull
  static Column indexOf(@Nonnull final Column input, @Nonnull final Column substring) {
    return call_function("locate", substring, input).minus(lit(1));
  }

  /**
   * FHIRPath substring: extracts a portion of {@code input} starting at 0-based {@code start}.
   *
   * <p>Handles FHIRPath edge cases:
   *
   * <ul>
   *   <li>Start outside string length → empty collection (null)
   *   <li>Length omitted (null column) → returns from start to end
   *   <li>Length zero or negative → empty string
   * </ul>
   */
  @Nonnull
  static Column substring(
      @Nonnull final Column input, @Nonnull final Column start, @Nonnull final Column len) {
    final Column strLen = functions.length(input);
    final Column outOfBounds = start.lt(lit(0)).or(start.geq(strLen));
    final Column sparkStart = start.plus(lit(1));
    final Column effectiveLen = when(len.isNull(), strLen.minus(start)).otherwise(len);

    return when(outOfBounds, lit(null))
        .when(effectiveLen.leq(lit(0)), lit(""))
        .otherwise(functions.substring(input, sparkStart, effectiveLen));
  }

  /**
   * FHIRPath replace: literal string replacement with empty-pattern special case.
   *
   * <p>When pattern is empty, surrounds each character with the substitution (e.g. {@code
   * 'abc'.replace('', 'x')} yields {@code 'xaxbxcx'}). Spark's {@code replace()} does not handle
   * this, so we use {@code regexp_replace} with a zero-width match for the empty-pattern case.
   */
  @Nonnull
  static Column replace(
      @Nonnull final Column input,
      @Nonnull final Column pattern,
      @Nonnull final Column substitution) {
    return when(
            functions.length(pattern).equalTo(lit(0)), regexp_replace(input, lit(""), substitution))
        .otherwise(functions.replace(input, pattern, substitution));
  }

  /**
   * FHIRPath toChars: splits a string into individual characters.
   *
   * <p>Returns null (empty collection) for empty strings. Uses a lookahead/lookbehind regex to
   * split between characters without producing empty leading/trailing elements.
   */
  @Nonnull
  static Column toChars(@Nonnull final Column input) {
    final Column result = functions.split(input, "(?<=.)(?=.)");
    return when(functions.length(input).equalTo(lit(0)), lit(null)).otherwise(result);
  }
}
