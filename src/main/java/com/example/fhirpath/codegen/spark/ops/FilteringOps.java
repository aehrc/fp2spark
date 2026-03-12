package com.example.fhirpath.codegen.spark.ops;

import com.example.fhirpath.codegen.spark.SparkOperationRegistry;
import com.example.fhirpath.ir.Lambda;

/**
 * Filtering and conditional operation registrations (where, iif).
 *
 * <p>Delegates to package-private methods on SparkCodeGenerator for lambda evaluation.
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
        (args, nodes, type, gen) -> {
          if (!(nodes.get(1) instanceof Lambda lambda)) {
            throw new IllegalArgumentException(
                "where() requires a Lambda argument, got: " + nodes.get(1).getClass());
          }
          return gen.evaluateWhere(args.get(0), nodes.get(0).isSingular(), lambda);
        });

    registry.register(
        "iif",
        (args, nodes, type, gen) -> {
          if (!(nodes.get(1) instanceof Lambda criterion)) {
            throw new IllegalArgumentException(
                "iif() criterion must be a Lambda, got: " + nodes.get(1).getClass());
          }
          if (!(nodes.get(2) instanceof Lambda trueResult)) {
            throw new IllegalArgumentException(
                "iif() true-result must be a Lambda, got: " + nodes.get(2).getClass());
          }
          return gen.evaluateIif(args.get(0), criterion, trueResult);
        });
  }
}
