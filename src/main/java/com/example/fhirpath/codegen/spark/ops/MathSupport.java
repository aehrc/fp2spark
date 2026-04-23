package com.example.fhirpath.codegen.spark.ops;

import static com.example.fhirpath.codegen.spark.SparkTypeMapper.DECIMAL_TYPE;
import static org.apache.spark.sql.functions.abs;
import static org.apache.spark.sql.functions.call_function;
import static org.apache.spark.sql.functions.ceil;
import static org.apache.spark.sql.functions.coalesce;
import static org.apache.spark.sql.functions.lit;
import static org.apache.spark.sql.functions.log;
import static org.apache.spark.sql.functions.pow;

import jakarta.annotation.Nonnull;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.functions;
import org.apache.spark.sql.types.DataTypes;

/** Spark column expression helpers for FHIRPath math functions. */
final class MathSupport {

  private MathSupport() {}

  /**
   * abs() for Quantity: applies abs to the value field, preserving unit, system, and code. Uses
   * {@code withField} so the implementation is resilient to Quantity schema changes.
   */
  @Nonnull
  static Column quantityAbs(@Nonnull final Column quantity) {
    return quantity.withField("value", abs(quantity.getField("value")));
  }

  /** ceiling(): returns the first integer greater than or equal to the input. */
  @Nonnull
  static Column ceiling(@Nonnull final Column value) {
    return ceil(value).cast(DataTypes.IntegerType);
  }

  /** floor(): returns the first integer less than or equal to the input. */
  @Nonnull
  static Column floor(@Nonnull final Column value) {
    return functions.floor(value).cast(DataTypes.IntegerType);
  }

  /** truncate(): returns the integer portion of the input (truncation toward zero). */
  @Nonnull
  static Column truncate(@Nonnull final Column value) {
    return NumericalSupport.truncateTowardZero(value).cast(DataTypes.IntegerType);
  }

  /**
   * round(value, precision): rounds to the specified number of decimal places (default 0).
   *
   * <p>Spark's built-in {@code round(col, scale)} requires its {@code scale} argument to be a
   * foldable (compile-time constant) integer, so it cannot be used directly when {@code precision}
   * is a column reference. We expand the operation manually as {@code round(value * 10^precision) /
   * 10^precision}, which works uniformly for literal and column-valued precision. The
   * single-argument Spark {@code round()} (implicit scale 0) is still foldable and retains HALF_UP
   * semantics matching FHIRPath.
   *
   * <p>See issue #247.
   */
  @Nonnull
  static Column round(@Nonnull final Column value, @Nonnull final Column precision) {
    final Column scale = pow(lit(10), coalesce(precision, lit(0)));
    final Column scaled = value.cast(DECIMAL_TYPE).multiply(scale);
    return call_function("round", scaled).divide(scale).cast(DECIMAL_TYPE);
  }

  /**
   * ln(): returns the natural logarithm. Returns empty for non-positive inputs since Spark's log()
   * returns NaN for negative values.
   */
  @Nonnull
  static Column ln(@Nonnull final Column value) {
    return NumericalSupport.nanToNull(log(value), DECIMAL_TYPE);
  }

  /**
   * log(value, base): computes logarithm using change-of-base formula ln(value)/ln(base), since
   * Spark's log() function requires a double base, not a Column.
   */
  @Nonnull
  static Column logarithm(@Nonnull final Column value, @Nonnull final Column base) {
    return NumericalSupport.nanToNull(log(value).divide(log(base)), DECIMAL_TYPE);
  }

  /**
   * power() for INTEGER result. Cast pow() result to IntegerType since Spark's pow() always returns
   * double.
   */
  @Nonnull
  static Column integerPower(@Nonnull final Column base, @Nonnull final Column exponent) {
    return NumericalSupport.nanToNull(pow(base, exponent), DataTypes.IntegerType)
        .cast(DataTypes.IntegerType);
  }

  /**
   * power() for DECIMAL result. Returns empty if the result cannot be represented (e.g., negative
   * base with fractional exponent produces NaN).
   */
  @Nonnull
  static Column decimalPower(@Nonnull final Column base, @Nonnull final Column exponent) {
    return NumericalSupport.nanToNull(pow(base, exponent), DECIMAL_TYPE);
  }

  /** sqrt(): returns empty for negative inputs (result cannot be represented). */
  @Nonnull
  static Column sqrt(@Nonnull final Column value) {
    return NumericalSupport.nanToNull(functions.sqrt(value), DECIMAL_TYPE);
  }
}
