package com.example.fhirpath.codegen.spark.ops;

import com.example.fhirpath.codegen.spark.SparkOperationRegistry;
import com.example.fhirpath.ir.Lambda;

/**
 * Filtering and conditional operation registrations (where, iif).
 *
 * <p>Delegates to methods on SparkCodeGenerator for lambda evaluation.
 */
public final class FilteringOps {

  private FilteringOps() {}

  /**
   * Registers all filtering and conditional operators into the given registry.
   *
   * @param registry the registry to register operations into
   */
  public static void register(final SparkOperationRegistry registry) {
    registry.register(
        "where",
        ctx -> {
          if (!(ctx.argNode(1) instanceof Lambda lambda)) {
            throw new IllegalArgumentException(
                "where() requires a Lambda argument, got: " + ctx.argNode(1).getClass());
          }
          return ctx.generator().evaluateWhere(ctx.arg(0), ctx.argNode(0).isSingular(), lambda);
        });

    registry.register(
        "iif",
        ctx -> {
          if (!(ctx.argNode(1) instanceof Lambda criterion)) {
            throw new IllegalArgumentException(
                "iif() criterion must be a Lambda, got: " + ctx.argNode(1).getClass());
          }
          if (!(ctx.argNode(2) instanceof Lambda trueResult)) {
            throw new IllegalArgumentException(
                "iif() true-result must be a Lambda, got: " + ctx.argNode(2).getClass());
          }
          return ctx.generator().evaluateIif(ctx.arg(0), criterion, trueResult);
        });
  }
}
