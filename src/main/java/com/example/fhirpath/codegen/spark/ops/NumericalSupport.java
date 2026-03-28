package com.example.fhirpath.codegen.spark.ops;

import static com.example.fhirpath.codegen.spark.SparkTypeMapper.DECIMAL_TYPE;
import static org.apache.spark.sql.functions.abs;
import static org.apache.spark.sql.functions.floor;
import static org.apache.spark.sql.functions.lit;
import static org.apache.spark.sql.functions.signum;
import static org.apache.spark.sql.functions.when;

import jakarta.annotation.Nonnull;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.types.DataType;
import org.apache.spark.sql.types.DataTypes;

/**
 * Spark column expression helpers for numeric operations.
 *
 * <p>Provides division-by-zero guarding, truncation-toward-zero, and NaN-to-null conversion for
 * FHIRPath numeric operators and math functions. All operations return {@code null} (empty) on
 * division by zero or unrepresentable results per the FHIRPath spec.
 */
final class NumericalSupport {

  private NumericalSupport() {}

  /**
   * Numeric division: both operands cast to DECIMAL, guarded against division by zero. Per FHIRPath
   * spec, {@code /} always returns DECIMAL even for integer operands.
   */
  @Nonnull
  static Column division(@Nonnull final Column left, @Nonnull final Column right) {
    return guardDivisionByZero(right, left.cast(DECIMAL_TYPE).divide(right.cast(DECIMAL_TYPE)));
  }

  /** Numeric modulo: guarded against division by zero. */
  @Nonnull
  static Column modulo(@Nonnull final Column left, @Nonnull final Column right) {
    return guardDivisionByZero(right, left.mod(right));
  }

  /**
   * Integer division (truncated toward zero) for INTEGER result type. Both operands cast to DECIMAL
   * for the division, then truncated and cast back to IntegerType.
   */
  @Nonnull
  static Column integerDivision(@Nonnull final Column left, @Nonnull final Column right) {
    return guardDivisionByZero(
        right,
        truncateTowardZero(left.cast(DECIMAL_TYPE).divide(right.cast(DECIMAL_TYPE)))
            .cast(DataTypes.IntegerType));
  }

  /**
   * Integer division (truncated toward zero) for DECIMAL result type. Result is truncated and cast
   * back to DECIMAL.
   */
  @Nonnull
  static Column decimalDivision(@Nonnull final Column left, @Nonnull final Column right) {
    return guardDivisionByZero(right, truncateTowardZero(left.divide(right)).cast(DECIMAL_TYPE));
  }

  /**
   * Truncates a numeric value toward zero. Uses {@code signum(x) * floor(abs(x))} to avoid overflow
   * that would occur with an integer cast for large decimal values.
   */
  @Nonnull
  static Column truncateTowardZero(@Nonnull final Column value) {
    return signum(value).multiply(floor(abs(value)));
  }

  /**
   * Converts NaN results to null (empty collection) per FHIRPath spec. Used by math functions that
   * can produce NaN for invalid inputs (e.g., sqrt of negative, log of negative).
   *
   * @param value the column expression to guard
   * @param nullType the data type to cast the null literal to
   */
  @Nonnull
  static Column nanToNull(@Nonnull final Column value, @Nonnull final DataType nullType) {
    return when(value.isNaN(), lit(null).cast(nullType)).otherwise(value);
  }

  /**
   * Wraps an expression with a division-by-zero guard. Returns null when the divisor is zero, per
   * FHIRPath spec.
   */
  @Nonnull
  private static Column guardDivisionByZero(
      @Nonnull final Column divisor, @Nonnull final Column result) {
    return when(divisor.cast(DECIMAL_TYPE).notEqual(lit(0)), result).otherwise(lit(null));
  }
}
