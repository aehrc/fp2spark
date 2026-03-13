package com.example.fhirpath.codegen.spark;

import com.example.fhirpath.ir.IRNode;
import com.example.fhirpath.typing.Type;
import jakarta.annotation.Nonnull;
import java.util.List;
import org.apache.spark.sql.Column;

/**
 * Context for Spark code generation of a FHIRPath operation.
 *
 * <p>Bundles evaluated argument columns, original IR nodes (for metadata), result type, and the
 * code generator reference. Provides convenience accessors to reduce boilerplate in operation
 * implementations.
 *
 * @param args evaluated argument columns (null for Lambda args)
 * @param argNodes original IR nodes for type/cardinality metadata
 * @param resultType the result type of the operation
 * @param generator the code generator (for lambda evaluation)
 */
public record SparkOpContext(
    @Nonnull List<Column> args,
    @Nonnull List<IRNode> argNodes,
    @Nonnull Type resultType,
    @Nonnull SparkCodeGenerator generator) {

  /** Returns the evaluated column for argument at the given index. */
  @Nonnull
  public Column arg(final int i) {
    return args.get(i);
  }

  /** Returns the IR node for argument at the given index. */
  @Nonnull
  public IRNode argNode(final int i) {
    return argNodes.get(i);
  }

  /** Returns the type of argument at the given index. */
  @Nonnull
  public Type argType(final int i) {
    return argNodes.get(i).getType();
  }

  /** Returns a {@link CollectionValue} wrapping the column and cardinality for argument i. */
  @Nonnull
  public CollectionValue collectionArg(final int i) {
    return new CollectionValue(args.get(i), argNodes.get(i).isSingular());
  }
}
