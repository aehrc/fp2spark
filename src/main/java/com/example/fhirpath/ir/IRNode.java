package com.example.fhirpath.ir;

import com.example.fhirpath.typing.Cardinality;
import com.example.fhirpath.typing.Shape;
import com.example.fhirpath.typing.Type;
import jakarta.annotation.Nonnull;

/**
 * Base interface for all IR nodes in the FHIRPath expression tree. IRNodes are target-agnostic and
 * represent typed FHIRPath expressions.
 *
 * <p>Code generation is delegated to target-specific visitors via the accept() method.
 *
 * <p>Each IR node has a {@link Shape} (element type + cardinality) representing the type and
 * cardinality of values it produces.
 */
@SuppressWarnings("checkstyle:AbbreviationAsWordInName")
public sealed interface IRNode
    permits Operation,
        Literal,
        Traversal,
        Cast,
        Resource,
        Combine,
        Equality,
        Lambda,
        ThisReference {

  /**
   * Returns the shape of this expression (element type + cardinality). For operations, this is the
   * result shape from the resolved signature.
   */
  @Nonnull
  Shape getShape();

  /**
   * Returns the element type of this expression. Convenience method equivalent to {@code
   * getShape().elementType()}.
   */
  @Nonnull
  default Type getType() {
    return getShape().elementType();
  }

  /**
   * Returns the cardinality of this expression. Convenience method equivalent to {@code
   * getShape().cardinality()}.
   */
  @Nonnull
  default Cardinality getCardinality() {
    return getShape().cardinality();
  }

  /** Returns whether this expression is singular (single cardinality, 0..1). */
  default boolean isSingular() {
    return getShape().isSingle();
  }

  /**
   * Accepts a visitor for target-specific code generation.
   *
   * @param visitor The visitor to accept
   * @param <T> The return type of the visitor (e.g., Column for Spark, String for SQL)
   * @return The result of visiting this node
   */
  @Nonnull
  <T> T accept(@Nonnull IRNodeVisitor<T> visitor);
}
