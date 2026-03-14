package com.example.fhirpath.codegen.spark.ops;

import static org.apache.spark.sql.functions.lit;
import static org.apache.spark.sql.functions.when;

import com.example.fhirpath.codegen.spark.SparkOperationRegistry;

/**
 * FHIR-specific function registrations (getValue, hasValue).
 *
 * <p>These functions are defined in the FHIR-specific FHIRPath binding.
 */
public final class FhirOps {

  private FhirOps() {}

  /**
   * Registers all FHIR-specific functions into the given registry.
   *
   * @param registry the registry to register operations into
   */
  public static void register(final SparkOperationRegistry registry) {
    // getValue() — returns the value of a FHIR primitive (identity for our encoding)
    registry.unary("getValue", col -> col);

    // hasValue() — returns true if the element has a value (non-null check)
    registry.unary("hasValue", col -> when(col.isNotNull(), lit(true)).otherwise(lit(false)));
  }
}
