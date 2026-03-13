package com.example.fhirpath.codegen.spark;

import jakarta.annotation.Nonnull;
import org.apache.spark.sql.Column;

/**
 * Functional interface for Spark code generation of a FHIRPath operation.
 *
 * <p>Each operation is a pure function that takes a {@link SparkOpContext} containing evaluated
 * argument columns, the original IR nodes (for metadata like type/cardinality), the result type,
 * and a reference to the code generator (for operations like where/iif that need to evaluate lambda
 * bodies).
 */
@FunctionalInterface
public interface SparkOperationDef {

  /**
   * Generate a Spark Column expression for this operation.
   *
   * @param ctx the operation context containing arguments, metadata, and generator
   * @return the generated Spark Column
   */
  @Nonnull
  Column generate(@Nonnull SparkOpContext ctx);
}
