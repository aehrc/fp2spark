package com.example.fhirpath.codegen.spark.ops;

import static org.apache.spark.sql.functions.coalesce;
import static org.apache.spark.sql.functions.concat;
import static org.apache.spark.sql.functions.floor;
import static org.apache.spark.sql.functions.lit;
import static org.apache.spark.sql.functions.when;

import com.example.fhirpath.codegen.spark.SparkOperationRegistry;
import com.example.fhirpath.typing.PrimitiveType;
import org.apache.spark.sql.Column;

/**
 * Arithmetic operator registrations.
 *
 * <p>Uses full registration for type-dispatched operations (add uses concat for strings). Division
 * by zero returns empty (null) per FHIRPath spec.
 */
public final class ArithmeticOps {

  private ArithmeticOps() {}

  /**
   * Wraps an expression with a division-by-zero guard. Returns null when the divisor is zero, per
   * FHIRPath spec.
   */
  private static Column guardDivisionByZero(final Column divisor, final Column result) {
    return when(divisor.notEqual(lit(0)), result).otherwise(lit(null));
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

    // Division always returns DECIMAL per FHIRPath spec.
    // Division by zero returns empty (null).
    // Cast to double to ensure decimal division (avoids integer truncation).
    registry.register(
        "divide",
        (args, nodes, type, gen) ->
            guardDivisionByZero(
                args.get(1), args.get(0).cast("double").divide(args.get(1).cast("double"))));

    // Modulo: division by zero returns empty (null).
    registry.register(
        "mod",
        (args, nodes, type, gen) -> guardDivisionByZero(args.get(1), args.get(0).mod(args.get(1))));

    // Integer division (truncated toward zero): division by zero returns empty (null).
    registry.register(
        "div",
        (args, nodes, type, gen) ->
            switch ((PrimitiveType) type) {
              case INTEGER ->
                  guardDivisionByZero(
                      args.get(1),
                      floor(args.get(0).cast("double").divide(args.get(1).cast("double")))
                          .cast("long"));
              case DECIMAL ->
                  guardDivisionByZero(args.get(1), floor(args.get(0).divide(args.get(1))));
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
