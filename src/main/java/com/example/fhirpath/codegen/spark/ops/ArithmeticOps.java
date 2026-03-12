package com.example.fhirpath.codegen.spark.ops;

import static org.apache.spark.sql.functions.concat;

import com.example.fhirpath.codegen.spark.SparkOperationRegistry;
import com.example.fhirpath.typing.PrimitiveType;

/**
 * Arithmetic operator registrations.
 *
 * <p>Uses full registration for type-dispatched operations (add uses concat for strings).
 */
public final class ArithmeticOps {

  private ArithmeticOps() {}

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

    registry.register(
        "divide",
        (args, nodes, type, gen) ->
            switch ((PrimitiveType) type) {
              case INTEGER, DECIMAL -> args.get(0).divide(args.get(1));
              default ->
                  throw new IllegalArgumentException("Unsupported result type for divide: " + type);
            });

    registry.register("mod", (args, nodes, type, gen) -> args.get(0).mod(args.get(1)));
  }
}
