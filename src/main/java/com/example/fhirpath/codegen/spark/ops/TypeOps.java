package com.example.fhirpath.codegen.spark.ops;

import com.example.fhirpath.codegen.spark.SparkOperationRegistry;

/**
 * Spark code generation for type testing operations.
 *
 * <p>The {@code is} operator checks whether a value is non-null (used after narrowing a choice type
 * to a specific variant column).
 *
 * <p>The {@code ofType} and {@code as} operators are resolved to {@link
 * com.example.fhirpath.ir.Traversal} nodes by the Analyzer, so they use existing traversal code
 * generation and don't need Spark operation registrations.
 */
public final class TypeOps {

  private TypeOps() {}

  /**
   * Registers type testing operations into the given registry.
   *
   * @param registry the registry to register operations into
   */
  public static void register(final SparkOperationRegistry registry) {
    // is: check if the variant column is non-null
    registry.unary("is", col -> col.isNotNull());
  }
}
