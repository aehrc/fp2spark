package com.example.fhirpath.codegen.spark.ops;

import com.example.fhirpath.codegen.spark.SparkOperationRegistry;

/**
 * Utility function registrations (FHIRPath §5.9).
 *
 * <p>Currently only {@code trace()}, which is a pure pass-through: it returns the column of its
 * input collection untouched, so type and cardinality are preserved exactly.
 *
 * <p>The diagnostic side channel that gives {@code trace()} its purpose is deliberately NOT
 * implemented. Emitting diagnostics requires an evaluation context to carry a sink (Pathling uses a
 * {@code TraceCollector} on its {@code EvaluationContext}; fhirpath.js uses a host-supplied {@code
 * traceFn}); fp2sql's public surface compiles an expression to SQL and has nowhere for such a sink
 * to live. Defining that contract is tracked by #277; until then the projection argument is
 * type-checked and then discarded.
 */
public final class UtilityOps {

  private UtilityOps() {}

  /**
   * Registers all utility functions into the given registry.
   *
   * @param registry the registry to register operations into
   */
  public static void register(final SparkOperationRegistry registry) {
    // trace(name [, projection]) returns the input collection unaltered.
    registry.register("trace", ctx -> ctx.arg(0));
  }
}
