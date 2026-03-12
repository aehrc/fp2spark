package com.example.fhirpath.codegen.spark.ops;

import com.example.fhirpath.codegen.spark.SparkOperationRegistry;
import com.example.fhirpath.typing.PrimitiveType;

/**
 * Comparison operator registrations.
 *
 * <p>Uses input type from first argument (not result type, which is always Boolean).
 */
public final class ComparisonOps {

  private ComparisonOps() {}

  /**
   * Registers all comparison operators into the given registry.
   *
   * @param registry the registry to register operations into
   */
  public static void register(final SparkOperationRegistry registry) {
    registry.register(
        "gt",
        (args, nodes, type, gen) ->
            switch ((PrimitiveType) nodes.get(0).getType()) {
              case INTEGER, DECIMAL, STRING -> args.get(0).gt(args.get(1));
              default ->
                  throw new IllegalArgumentException(
                      "Unsupported input type for gt: " + nodes.get(0).getType());
            });

    registry.register(
        "lt",
        (args, nodes, type, gen) ->
            switch ((PrimitiveType) nodes.get(0).getType()) {
              case INTEGER, DECIMAL, STRING -> args.get(0).lt(args.get(1));
              default ->
                  throw new IllegalArgumentException(
                      "Unsupported input type for lt: " + nodes.get(0).getType());
            });

    registry.register(
        "geq",
        (args, nodes, type, gen) ->
            switch ((PrimitiveType) nodes.get(0).getType()) {
              case INTEGER, DECIMAL, STRING -> args.get(0).geq(args.get(1));
              default ->
                  throw new IllegalArgumentException(
                      "Unsupported input type for geq: " + nodes.get(0).getType());
            });

    registry.register(
        "leq",
        (args, nodes, type, gen) ->
            switch ((PrimitiveType) nodes.get(0).getType()) {
              case INTEGER, DECIMAL, STRING -> args.get(0).leq(args.get(1));
              default ->
                  throw new IllegalArgumentException(
                      "Unsupported input type for leq: " + nodes.get(0).getType());
            });
  }
}
