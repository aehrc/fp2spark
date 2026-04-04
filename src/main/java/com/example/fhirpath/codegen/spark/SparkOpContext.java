package com.example.fhirpath.codegen.spark;

import com.example.fhirpath.ir.IRNode;
import com.example.fhirpath.ir.Lambda;
import com.example.fhirpath.ir.Literal;
import com.example.fhirpath.ir.Operation;
import com.example.fhirpath.ir.Resource;
import com.example.fhirpath.typing.SystemType;
import com.example.fhirpath.typing.Type;
import jakarta.annotation.Nonnull;
import java.util.List;
import java.util.function.UnaryOperator;
import org.apache.spark.sql.Column;

/**
 * Context for Spark code generation of a FHIRPath operation.
 *
 * <p>Bundles evaluated argument columns, original IR nodes (for metadata), the full {@link
 * Operation} IR node, and the code generator reference. Provides convenience accessors to reduce
 * boilerplate in operation implementations.
 *
 * @param args evaluated argument columns (null for Lambda args)
 * @param argNodes original IR nodes for type/cardinality metadata
 * @param operation the full Operation IR node
 * @param generator the code generator (for lambda evaluation)
 */
public record SparkOpContext(
    @Nonnull List<Column> args,
    @Nonnull List<IRNode> argNodes,
    @Nonnull Operation operation,
    @Nonnull SparkCodeGenerator generator) {

  /** Returns the operation name. */
  @Nonnull
  public String name() {
    return operation.name();
  }

  /** Returns the result type of the operation. */
  @Nonnull
  public Type resultType() {
    return operation.getType();
  }

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

  /**
   * Returns the result type as a {@link SystemType}, throwing if it is not one.
   *
   * @return the result type as a SystemType
   * @throws IllegalArgumentException if the result type is not a SystemType
   */
  @Nonnull
  public SystemType primitiveResultType() {
    final Type type = resultType();
    if (!(type instanceof SystemType pt)) {
      throw new IllegalArgumentException("Expected SystemType result, got: " + type.getClass());
    }
    return pt;
  }

  /**
   * Returns the type of argument at the given index as a {@link SystemType}, throwing if it is not
   * one.
   *
   * @param i the argument index
   * @return the argument type as a SystemType
   * @throws IllegalArgumentException if the argument type is not a SystemType
   */
  @Nonnull
  public SystemType primitiveArgType(final int i) {
    final Type type = argType(i);
    if (!(type instanceof SystemType pt)) {
      throw new IllegalArgumentException(
          "Expected SystemType at argument " + i + ", got: " + type.getClass());
    }
    return pt;
  }

  /**
   * Returns the Resource at the given argument index, throwing if the argument is not a Resource.
   *
   * @param i the argument index
   * @return the Resource at the given index
   * @throws IllegalArgumentException if the argument is not a Resource
   */
  @Nonnull
  public Resource resourceArg(final int i) {
    if (!(argNodes.get(i) instanceof Resource resource)) {
      throw new IllegalArgumentException(
          "Expected a Resource at argument " + i + ", got: " + argNodes.get(i).getClass());
    }
    return resource;
  }

  /**
   * Returns the Literal at the given argument index, throwing if the argument is not a Literal.
   *
   * @param i the argument index
   * @return the Literal at the given index
   * @throws IllegalArgumentException if the argument is not a Literal
   */
  @Nonnull
  public Literal literalArg(final int i) {
    if (!(argNodes.get(i) instanceof Literal literal)) {
      throw new IllegalArgumentException(
          "Expected a Literal at argument " + i + ", got: " + argNodes.get(i).getClass());
    }
    return literal;
  }

  /**
   * Returns the Lambda at the given argument index, throwing if the argument is not a Lambda.
   *
   * @param i the argument index
   * @return the Lambda at the given index
   * @throws IllegalArgumentException if the argument is not a Lambda
   */
  @Nonnull
  public Lambda lambdaArg(final int i) {
    if (!(argNodes.get(i) instanceof Lambda lambda)) {
      throw new IllegalArgumentException(
          "Expected a Lambda at argument " + i + ", got: " + argNodes.get(i).getClass());
    }
    return lambda;
  }

  /**
   * Evaluates a lambda body with {@code $this} bound to the given column.
   *
   * @param thisBinding the column to bind as {@code $this}
   * @param lambda the lambda whose body to evaluate
   * @return the Spark Column produced by evaluating the lambda body
   */
  @Nonnull
  public Column evaluateLambda(@Nonnull final Column thisBinding, @Nonnull final Lambda lambda) {
    return lambda.body().accept(generator.withThisColumn(thisBinding));
  }

  /**
   * Returns a function that evaluates the lambda at the given argument index with a bound {@code
   * $this} column.
   *
   * <p>This binds the lambda argument to a reusable {@code UnaryOperator<Column>}, eliminating the
   * need to pass both a {@link Lambda} and a {@link SparkOpContext} through helper methods.
   *
   * @param i the argument index of the Lambda
   * @return a function mapping a {@code $this} binding column to the lambda's evaluated result
   */
  @Nonnull
  public UnaryOperator<Column> lambdaEvaluator(final int i) {
    final Lambda lambda = lambdaArg(i);
    return thisBinding -> evaluateLambda(thisBinding, lambda);
  }
}
