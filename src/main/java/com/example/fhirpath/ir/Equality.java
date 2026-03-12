package com.example.fhirpath.ir;

import com.example.fhirpath.typing.Shape;
import com.example.fhirpath.typing.Types;
import jakarta.annotation.Nonnull;

/**
 * Represents an equality comparison ({@code =} or {@code !=}) in FHIRPath. Always returns single
 * BOOLEAN.
 *
 * @param operator the equality operator (EQUALS or NOT_EQUALS)
 * @param left the left operand
 * @param right the right operand
 */
public record Equality(EqualityOperator operator, IRNode left, IRNode right) implements IRNode {

  @Override
  @Nonnull
  public Shape getShape() {
    return Shape.single(Types.BOOLEAN);
  }

  @Override
  @Nonnull
  public <T> T accept(@Nonnull final IRNodeVisitor<T> visitor) {
    return visitor.visitEquality(this);
  }
}
