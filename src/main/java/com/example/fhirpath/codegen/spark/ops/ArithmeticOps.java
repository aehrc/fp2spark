package com.example.fhirpath.codegen.spark.ops;

import static com.example.fhirpath.codegen.spark.SparkTypeMapper.DECIMAL_TYPE;
import static org.apache.spark.sql.functions.abs;
import static org.apache.spark.sql.functions.coalesce;
import static org.apache.spark.sql.functions.concat;
import static org.apache.spark.sql.functions.floor;
import static org.apache.spark.sql.functions.lit;
import static org.apache.spark.sql.functions.signum;
import static org.apache.spark.sql.functions.when;

import com.example.fhirpath.codegen.spark.SparkOperationRegistry;
import com.example.fhirpath.typing.PrimitiveType;
import jakarta.annotation.Nonnull;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.types.DataTypes;

/**
 * Arithmetic operator registrations.
 *
 * <p>Uses full registration for type-dispatched operations (add uses concat for strings). Division
 * by zero returns empty (null) per FHIRPath spec.
 */
public final class ArithmeticOps {

  private ArithmeticOps() {}

  /**
   * Truncates a numeric value toward zero. Uses {@code signum(x) * floor(abs(x))} to avoid overflow
   * that would occur with an integer cast for large decimal values.
   */
  @Nonnull
  private static Column truncateTowardZero(@Nonnull final Column value) {
    return signum(value).multiply(floor(abs(value)));
  }

  /**
   * Wraps an expression with a division-by-zero guard. Returns null when the divisor is zero, per
   * FHIRPath spec. Casts the divisor to DECIMAL for the comparison to handle both Integer and
   * Decimal types uniformly.
   */
  @Nonnull
  private static Column guardDivisionByZero(
      @Nonnull final Column divisor, @Nonnull final Column result) {
    return when(divisor.cast(DECIMAL_TYPE).notEqual(lit(0)), result).otherwise(lit(null));
  }

  /**
   * Registers all arithmetic operators into the given registry.
   *
   * @param registry the registry to register operations into
   */
  public static void register(final SparkOperationRegistry registry) {
    registry.register(
        "add",
        (args, nodes, type, gen) ->
            switch ((PrimitiveType) type) {
              case INTEGER, DECIMAL -> args.get(0).plus(args.get(1));
              case STRING -> concat(args.get(0), args.get(1));
              default ->
                  throw new IllegalArgumentException("Unsupported result type for add: " + type);
            });

    registry.register(
        "sub",
        (args, nodes, type, gen) ->
            switch ((PrimitiveType) type) {
              case INTEGER, DECIMAL -> args.get(0).minus(args.get(1));
              default ->
                  throw new IllegalArgumentException("Unsupported result type for sub: " + type);
            });

    registry.register(
        "multiply",
        (args, nodes, type, gen) ->
            switch ((PrimitiveType) type) {
              case INTEGER, DECIMAL -> args.get(0).multiply(args.get(1));
              default ->
                  throw new IllegalArgumentException(
                      "Unsupported result type for multiply: " + type);
            });

    // Division always returns DECIMAL per FHIRPath spec (divisionOp signature enforces this).
    // No type-dispatch needed since both Integer and Decimal inputs produce Decimal output.
    // Division by zero returns empty (null).
    registry.register(
        "divide",
        (args, nodes, type, gen) ->
            guardDivisionByZero(
                args.get(1),
                args.get(0).cast(DECIMAL_TYPE).divide(args.get(1).cast(DECIMAL_TYPE))));

    // Modulo: division by zero returns empty (null).
    registry.register(
        "mod",
        (args, nodes, type, gen) -> guardDivisionByZero(args.get(1), args.get(0).mod(args.get(1))));

    // Integer division (truncated toward zero): division by zero returns empty (null).
    // Uses integer cast which truncates toward zero per JVM/Spark semantics,
    // matching the FHIRPath spec ("the division that ignores any remainder").
    registry.register(
        "div",
        (args, nodes, type, gen) ->
            switch ((PrimitiveType) type) {
              case INTEGER ->
                  guardDivisionByZero(
                      args.get(1),
                      truncateTowardZero(
                              args.get(0).cast(DECIMAL_TYPE).divide(args.get(1).cast(DECIMAL_TYPE)))
                          .cast(DataTypes.IntegerType));
              case DECIMAL ->
                  guardDivisionByZero(
                      args.get(1),
                      truncateTowardZero(args.get(0).divide(args.get(1))).cast(DECIMAL_TYPE));
              default ->
                  throw new IllegalArgumentException("Unsupported result type for div: " + type);
            });

    // String concatenation (&): treats null/empty as empty string.
    registry.register(
        "stringConcat",
        (args, nodes, type, gen) ->
            concat(coalesce(args.get(0), lit("")), coalesce(args.get(1), lit(""))));

    // Unary plus: identity operation.
    registry.unary("unaryPlus", col -> col);

    // Unary minus: negation.
    registry.unary("unaryMinus", col -> col.multiply(lit(-1)));
  }
}
