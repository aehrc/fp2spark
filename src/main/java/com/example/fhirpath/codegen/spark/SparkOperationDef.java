package com.example.fhirpath.codegen.spark;

import com.example.fhirpath.ir.IRNode;
import com.example.fhirpath.typing.Type;
import jakarta.annotation.Nonnull;
import java.util.List;
import org.apache.spark.sql.Column;

/**
 * Functional interface for Spark code generation of a FHIRPath operation.
 *
 * <p>Each operation is a pure function that takes evaluated argument columns, the original IR nodes
 * (for metadata like type/cardinality), the result type, and a reference to the code generator (for
 * operations like where/iif that need to evaluate lambda bodies).
 */
@FunctionalInterface
public interface SparkOperationDef {

  /**
   * Generate a Spark Column expression for this operation.
   *
   * @param args evaluated argument columns (null for Lambda args)
   * @param argNodes original IR nodes for type/cardinality metadata
   * @param resultType the result type of the operation
   * @param generator the code generator (for lambda evaluation)
   * @return the generated Spark Column
   */
  @Nonnull
  Column generate(
      @Nonnull List<Column> args,
      @Nonnull List<IRNode> argNodes,
      @Nonnull Type resultType,
      @Nonnull SparkCodeGenerator generator);
}
