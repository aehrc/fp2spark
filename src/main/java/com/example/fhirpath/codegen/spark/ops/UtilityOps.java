package com.example.fhirpath.codegen.spark.ops;

import com.example.fhirpath.codegen.spark.SparkOperationRegistry;

/**
 * Utility function registrations (FHIRPath §5.9).
 *
 * <p>Currently only {@code trace()}, which returns the column of its input collection untouched.
 * Its diagnostic side channel is deliberately not implemented and the projection argument is
 * discarded after type checking — see {@link
 * com.example.fhirpath.operation.signature.Signatures#diagnosticPassThrough} and #277.
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
