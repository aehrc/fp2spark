package com.example.fhirpath.ir;

import com.example.fhirpath.typing.Shape;
import com.example.fhirpath.typing.Type;
import jakarta.annotation.Nonnull;

/**
 * Represents a type cast operation. Preserves the cardinality of the child while changing the
 * element type.
 *
 * @param child the expression being cast
 * @param targetType the target element type
 */
public record Cast(IRNode child, Type targetType) implements IRNode {
  @Override
  @Nonnull
  public Shape getShape() {
    // Preserve cardinality from child, but change element type
    return Shape.of(targetType, child.getCardinality());
  }

  @Override
  @Nonnull
  public <T> T accept(@Nonnull final IRNodeVisitor<T> visitor) {
    return visitor.visitCast(this);
  }
}
