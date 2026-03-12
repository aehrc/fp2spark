package com.example.fhirpath.ir;

import com.example.fhirpath.typing.Shape;
import com.example.fhirpath.typing.Type;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * Represents a literal value in the FHIRPath IR. Literals always have single cardinality (0..1).
 *
 * @param value the literal value, or null for an empty collection
 * @param type the element type of the literal
 */
public record Literal(@Nullable Object value, @Nonnull Type type) implements IRNode {
  @Override
  @Nonnull
  public Shape getShape() {
    return Shape.single(type);
  }

  @Override
  @Nonnull
  public <T> T accept(@Nonnull final IRNodeVisitor<T> visitor) {
    return visitor.visitLiteral(this);
  }
}
