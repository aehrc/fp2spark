package com.example.fhirpath.codegen.spark.ops;

import static com.example.fhirpath.codegen.spark.SparkDefs.byResultType;
import static com.example.fhirpath.codegen.spark.SparkDefs.types;
import static com.example.fhirpath.codegen.spark.SparkDefs.unary;
import static com.example.fhirpath.codegen.spark.SparkTypeMapper.DECIMAL_TYPE;
import static com.example.fhirpath.typing.PrimitiveType.DECIMAL;
import static com.example.fhirpath.typing.PrimitiveType.INTEGER;
import static org.apache.spark.sql.functions.abs;
import static org.apache.spark.sql.functions.call_function;
import static org.apache.spark.sql.functions.ceil;
import static org.apache.spark.sql.functions.coalesce;
import static org.apache.spark.sql.functions.floor;
import static org.apache.spark.sql.functions.lit;
import static org.apache.spark.sql.functions.log;
import static org.apache.spark.sql.functions.pow;
import static org.apache.spark.sql.functions.signum;
import static org.apache.spark.sql.functions.sqrt;
import static org.apache.spark.sql.functions.when;

import com.example.fhirpath.codegen.spark.SparkOpContext;
import com.example.fhirpath.codegen.spark.SparkOperationRegistry;
import jakarta.annotation.Nonnull;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.functions;
import org.apache.spark.sql.types.DataTypes;

/**
 * Math function registrations (abs, ceiling, floor, round, truncate, exp, ln, log, power, sqrt).
 *
 * <p>All math functions operate on singular numeric inputs and return empty when the input is
 * empty. Spark's built-in null propagation handles the empty collection semantics automatically.
 */
public final class MathOps {

  private MathOps() {}

  /**
   * Registers all math functions into the given registry.
   *
   * @param registry the registry to register operations into
   */
  public static void register(@Nonnull final SparkOperationRegistry registry) {
    registry.register("abs", unary(functions::abs));
    registry.register("ceiling", unary(col -> ceil(col).cast(DataTypes.IntegerType)));
    registry.register("floor", unary(col -> floor(col).cast(DataTypes.IntegerType)));
    registry.register("truncate", unary(MathOps::truncateTowardZero));
    registry.register("round", MathOps::generateRound);
    registry.register("exp", unary(functions::exp));
    registry.register("ln", unary(MathOps::generateLn));
    registry.register("log", MathOps::generateLog);
    registry.register(
        "power",
        byResultType()
            .when(types(INTEGER), MathOps::generateIntegerPower)
            .when(types(DECIMAL), MathOps::generateDecimalPower));
    registry.register("sqrt", unary(MathOps::generateSqrt));
  }

  /**
   * Truncates a numeric value toward zero. Uses {@code signum(x) * floor(abs(x))} to handle
   * negative values correctly (e.g., -1.56 → -1, not -2).
   */
  @Nonnull
  private static Column truncateTowardZero(@Nonnull final Column value) {
    return signum(value).multiply(floor(abs(value))).cast(DataTypes.IntegerType);
  }

  /**
   * round([precision]): rounds to the specified number of decimal places (default 0). Uses Spark
   * SQL's round() via call_function to accept a Column precision argument. Always returns Decimal
   * per FHIRPath spec.
   */
  @Nonnull
  private static Column generateRound(@Nonnull final SparkOpContext ctx) {
    final Column value = ctx.arg(0).cast(DECIMAL_TYPE);
    final Column precision = coalesce(ctx.arg(1), lit(0));
    return call_function("round", value, precision);
  }

  /**
   * ln(): returns the natural logarithm. Returns empty for non-positive inputs (result cannot be
   * represented), since Spark's log() returns NaN for negative values and -Infinity for zero.
   */
  @Nonnull
  private static Column generateLn(@Nonnull final Column value) {
    return nanToNull(log(value));
  }

  /**
   * log(base): computes logarithm using change-of-base formula ln(value)/ln(base), since Spark's
   * log() function requires a double base, not a Column. Returns empty for invalid inputs.
   */
  @Nonnull
  private static Column generateLog(@Nonnull final SparkOpContext ctx) {
    return nanToNull(log(ctx.arg(0)).divide(log(ctx.arg(1))));
  }

  /**
   * power() for INTEGER result (both inputs are INTEGER). Cast pow() result to IntegerType since
   * Spark's pow() always returns double.
   */
  @Nonnull
  private static Column generateIntegerPower(@Nonnull final SparkOpContext ctx) {
    final Column result = pow(ctx.arg(0), ctx.arg(1));
    return when(result.isNaN(), lit(null).cast(DataTypes.IntegerType))
        .otherwise(result.cast(DataTypes.IntegerType));
  }

  /**
   * power() for DECIMAL result. Returns empty if the result cannot be represented (e.g., negative
   * base with fractional exponent produces NaN).
   */
  @Nonnull
  private static Column generateDecimalPower(@Nonnull final SparkOpContext ctx) {
    final Column result = pow(ctx.arg(0), ctx.arg(1));
    return nanToNull(result);
  }

  /**
   * sqrt(): returns empty for negative inputs (result cannot be represented). Equivalent to
   * power(0.5) per FHIRPath spec.
   */
  @Nonnull
  private static Column generateSqrt(@Nonnull final Column value) {
    return nanToNull(sqrt(value));
  }

  /** Converts NaN results to null (empty collection) per FHIRPath spec. */
  @Nonnull
  private static Column nanToNull(@Nonnull final Column value) {
    return when(value.isNaN(), lit(null).cast(DECIMAL_TYPE)).otherwise(value);
  }
}
