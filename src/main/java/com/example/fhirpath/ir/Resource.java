package com.example.fhirpath.ir;

import com.example.fhirpath.typing.ResourceType;
import com.example.fhirpath.typing.Shape;
import jakarta.annotation.Nonnull;

/**
 * Represents a resource reference in FHIRPath. Resources always have single cardinality.
 *
 * @param type the resource type
 */
public record Resource(ResourceType type) implements IRNode {
  @Override
  @Nonnull
  public Shape getShape() {
    return Shape.single(type);
  }

  @Override
  @Nonnull
  public <T> T accept(@Nonnull final IRNodeVisitor<T> visitor) {
    return visitor.visitResource(this);
  }
}
