package com.example.fhirpath.codegen.spark.ops;

import com.example.fhirpath.codegen.spark.SparkOperationRegistry;
import jakarta.annotation.Nonnull;

/**
 * Utility function registrations (FHIRPath §5.9).
 *
 * <p>Currently only {@code trace()}, which returns the column of its input collection untouched.
 * See {@link com.example.fhirpath.operation.signature.Signatures#diagnosticPassThrough} for why.
 */
public final class UtilityOps {

  private UtilityOps() {}

  /**
   * Registers all utility functions into the given registry.
   *
   * @param registry the registry to register operations into
   */
  public static void register(@Nonnull final SparkOperationRegistry registry) {
    // trace(name [, projection]) returns the input collection unaltered.
    registry.register("trace", ctx -> ctx.arg(0));
  }
}
