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
        ctx ->
            switch ((PrimitiveType) ctx.argType(0)) {
              case INTEGER, DECIMAL, STRING -> ctx.arg(0).gt(ctx.arg(1));
              default ->
                  throw new IllegalArgumentException(
                      "Unsupported input type for gt: " + ctx.argType(0));
            });

    registry.register(
        "lt",
        ctx ->
            switch ((PrimitiveType) ctx.argType(0)) {
              case INTEGER, DECIMAL, STRING -> ctx.arg(0).lt(ctx.arg(1));
              default ->
                  throw new IllegalArgumentException(
                      "Unsupported input type for lt: " + ctx.argType(0));
            });

    registry.register(
        "geq",
        ctx ->
            switch ((PrimitiveType) ctx.argType(0)) {
              case INTEGER, DECIMAL, STRING -> ctx.arg(0).geq(ctx.arg(1));
              default ->
                  throw new IllegalArgumentException(
                      "Unsupported input type for geq: " + ctx.argType(0));
            });

    registry.register(
        "leq",
        ctx ->
            switch ((PrimitiveType) ctx.argType(0)) {
              case INTEGER, DECIMAL, STRING -> ctx.arg(0).leq(ctx.arg(1));
              default ->
                  throw new IllegalArgumentException(
                      "Unsupported input type for leq: " + ctx.argType(0));
            });
  }
}
