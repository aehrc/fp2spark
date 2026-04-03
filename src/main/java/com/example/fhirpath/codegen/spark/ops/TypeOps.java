package com.example.fhirpath.codegen.spark.ops;

import static org.apache.spark.sql.functions.when;

import com.example.fhirpath.codegen.spark.SparkOperationRegistry;

/**
 * Spark code generation for type testing operations.
 *
 * <p>The {@code is} operator has two forms:
 *
 * <ul>
 *   <li><b>Unary (choice type):</b> checks whether a variant column is non-null. Created by {@code
 *       Analyzer.resolveChoiceTypeOperation()}.
 *   <li><b>Binary (non-choice type):</b> null-propagating static type match. The first argument is
 *       the value, the second is a boolean literal indicating the static match result. Created by
 *       {@code Analyzer.resolveNonChoiceTypeOperation()}.
 * </ul>
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
    registry.register(
        "is",
        ctx -> {
          if (ctx.args().size() == 2) {
            // Non-choice: CASE WHEN value IS NOT NULL THEN match_result ELSE NULL END
            return when(ctx.arg(0).isNotNull(), ctx.arg(1));
          }
          // Choice type: variant column null check
          return ctx.arg(0).isNotNull();
        });
  }
}
