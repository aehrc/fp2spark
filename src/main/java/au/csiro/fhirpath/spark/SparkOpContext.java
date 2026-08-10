/*
 * Copyright © 2025-2026 Commonwealth Scientific and Industrial Research
 * Organisation (CSIRO) ABN 41 687 119 230.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package au.csiro.fhirpath.spark;

import au.csiro.fhirpath.ir.IRNode;
import au.csiro.fhirpath.ir.Lambda;
import au.csiro.fhirpath.ir.Literal;
import au.csiro.fhirpath.ir.Operation;
import au.csiro.fhirpath.ir.Resource;
import au.csiro.fhirpath.typing.FhirComplexType;
import au.csiro.fhirpath.typing.FhirPrimitiveType;
import au.csiro.fhirpath.typing.SystemType;
import au.csiro.fhirpath.typing.Type;
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
  public SystemType systemResultType() {
    final Type type = resultType();
    if (!(type instanceof SystemType pt)) {
      throw new IllegalArgumentException("Expected SystemType result, got: " + type.getClass());
    }
    return pt;
  }

  /**
   * Returns the type of argument at the given index as a {@link SystemType}, applying implicit
   * FHIR→System coercion per FHIRPath §5.3.
   *
   * <ul>
   *   <li>{@link FhirPrimitiveType} unwraps to its underlying System type (e.g. FHIR.string →
   *       System.String).
   *   <li>{@link FhirComplexType} whose HAPI class extends {@code Quantity} (Duration, Age, Count,
   *       Distance, Money, SimpleQuantity) maps to System.Quantity — all share the Quantity struct
   *       layout.
   * </ul>
   *
   * <p>Used by conversion functions (toString, toQuantity, …) that declare {@code ANY} parameters
   * and dispatch internally on the resolved System type.
   *
   * @param i the argument index
   * @return the argument type as a SystemType
   * @throws IllegalArgumentException if the argument type cannot be resolved to a SystemType
   */
  @Nonnull
  public SystemType systemArgType(final int i) {
    final Type type = argType(i);
    return switch (type) {
      case final SystemType pt -> pt;
      case final FhirPrimitiveType fpt -> fpt.getSystemType();
      case final FhirComplexType fct when fct.isQuantityCompatible() -> SystemType.QUANTITY;
      default ->
          throw new IllegalArgumentException(
              "Expected SystemType at argument " + i + ", got: " + type.getClass());
    };
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
