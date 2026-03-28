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
   * abs() for Quantity: applies abs to the value field, preserving unit, system, and code. Per
   * FHIRPath spec: {@code (-5.5 'mg').abs() // 5.5 'mg'}.
   */
  @Nonnull
  static Column quantityAbs(@Nonnull final Column quantity) {
    return functions.struct(
        abs(quantity.getField("value")).as("value"),
        quantity.getField("unit").as("unit"),
        quantity.getField("system").as("system"),
        quantity.getField("code").as("code"));
  }

  /** ceiling(): returns the first integer greater than or equal to the input. */
  @Nonnull
  static Column ceiling(@Nonnull final Column value) {
    return ceil(value).cast(DataTypes.IntegerType);
  }

  /** floor(): returns the first integer less than or equal to the input. */
  @Nonnull
  static Column floor(@Nonnull final Column value) {
    return org.apache.spark.sql.functions.floor(value).cast(DataTypes.IntegerType);
  }

  /** truncate(): returns the integer portion of the input (truncation toward zero). */
  @Nonnull
  static Column truncate(@Nonnull final Column value) {
    return NumericalSupport.truncateTowardZero(value).cast(DataTypes.IntegerType);
  }

  /**
   * round(value, precision): rounds to the specified number of decimal places (default 0). Uses
   * Spark SQL's round() via call_function to accept a Column precision argument.
   */
  @Nonnull
  static Column round(@Nonnull final Column value, @Nonnull final Column precision) {
    return call_function("round", value.cast(DECIMAL_TYPE), coalesce(precision, lit(0)));
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
    return NumericalSupport.nanToNull(org.apache.spark.sql.functions.sqrt(value), DECIMAL_TYPE);
  }
}
