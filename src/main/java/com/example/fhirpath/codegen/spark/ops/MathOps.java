package com.example.fhirpath.codegen.spark.ops;

import static com.example.fhirpath.codegen.spark.SparkDefs.byResultType;
import static com.example.fhirpath.codegen.spark.SparkDefs.types;
import static com.example.fhirpath.codegen.spark.SparkDefs.unary;
import static com.example.fhirpath.codegen.spark.SparkTypeMapper.DECIMAL_TYPE;
import static com.example.fhirpath.typing.PrimitiveType.DECIMAL;
import static com.example.fhirpath.typing.PrimitiveType.INTEGER;
import static org.apache.spark.sql.functions.call_function;
import static org.apache.spark.sql.functions.ceil;
import static org.apache.spark.sql.functions.coalesce;
import static org.apache.spark.sql.functions.floor;
import static org.apache.spark.sql.functions.lit;
import static org.apache.spark.sql.functions.log;
import static org.apache.spark.sql.functions.pow;
import static org.apache.spark.sql.functions.sqrt;

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
    registry.register(
        "truncate",
        unary(col -> NumericalSupport.truncateTowardZero(col).cast(DataTypes.IntegerType)));
    registry.register("round", MathOps::generateRound);
    registry.register("exp", unary(functions::exp));
    registry.register("ln", unary(col -> NumericalSupport.nanToNull(log(col), DECIMAL_TYPE)));
    registry.register("log", MathOps::generateLog);
    registry.register(
        "power",
        byResultType()
            .when(types(INTEGER), MathOps::generateIntegerPower)
            .when(types(DECIMAL), MathOps::generateDecimalPower));
    registry.register("sqrt", unary(col -> NumericalSupport.nanToNull(sqrt(col), DECIMAL_TYPE)));
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
   * log(base): computes logarithm using change-of-base formula ln(value)/ln(base), since Spark's
   * log() function requires a double base, not a Column. Returns empty for invalid inputs.
   */
  @Nonnull
  private static Column generateLog(@Nonnull final SparkOpContext ctx) {
    return NumericalSupport.nanToNull(log(ctx.arg(0)).divide(log(ctx.arg(1))), DECIMAL_TYPE);
  }

  /**
   * power() for INTEGER result (both inputs are INTEGER). Cast pow() result to IntegerType since
   * Spark's pow() always returns double.
   */
  @Nonnull
  private static Column generateIntegerPower(@Nonnull final SparkOpContext ctx) {
    final Column result = pow(ctx.arg(0), ctx.arg(1));
    return NumericalSupport.nanToNull(result, DataTypes.IntegerType).cast(DataTypes.IntegerType);
  }

  /**
   * power() for DECIMAL result. Returns empty if the result cannot be represented (e.g., negative
   * base with fractional exponent produces NaN).
   */
  @Nonnull
  private static Column generateDecimalPower(@Nonnull final SparkOpContext ctx) {
    return NumericalSupport.nanToNull(pow(ctx.arg(0), ctx.arg(1)), DECIMAL_TYPE);
  }
}
