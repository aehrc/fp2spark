package com.example.fhirpath.ir;

import com.example.fhirpath.typing.Shape;
import jakarta.annotation.Nonnull;

/**
 * IR node representing $this reference in lambda expressions. $this refers to the current value
 * being evaluated in lambda expressions.
 *
 * <p>The shape (type + cardinality) of $this depends on the lambda binding strategy:
 *
 * <ul>
 *   <li><b>ELEMENT_WISE</b>: $this is a single element (e.g., in where())
 *   <li><b>COLLECTION_WISE</b>: $this is the entire collection (e.g., in iif())
 * </ul>
 *
 * <p>Example (ELEMENT_WISE): name.where(use = 'official')
 *
 * <pre>
 * The expression "use = 'official'" is analyzed as:
 *   Operation("equals", [Traversal(ThisReference(HumanName, SINGLE), "use"), Literal("official")])
 * ThisReference holds single element of type HumanName
 * </pre>
 *
 * <p>Example (COLLECTION_WISE): (1 | 2 | 3).iif(exists(), $this)
 *
 * <pre>
 * The expression "$this" refers to the entire collection:
 *   ThisReference(INTEGER, MANY)
 * </pre>
 *
 * <p>During code generation, ThisReference is substituted with the actual variable passed to the
 * Spark lambda function.
 *
 * @param shape the shape (type + cardinality) of the $this reference
 */
public record ThisReference(@Nonnull Shape shape) implements IRNode {

  @Override
  @Nonnull
  public Shape getShape() {
    return shape;
  }

  @Override
  @Nonnull
  public <T> T accept(@Nonnull final IRNodeVisitor<T> visitor) {
    return visitor.visitThisReference(this);
  }

  @Override
  public String toString() {
    return "$this : " + shape;
  }
}
