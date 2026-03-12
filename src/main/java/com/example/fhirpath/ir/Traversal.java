package com.example.fhirpath.ir;

import com.example.fhirpath.typing.Cardinality;
import com.example.fhirpath.typing.FieldSpec;
import com.example.fhirpath.typing.Shape;
import jakarta.annotation.Nonnull;

/**
 * Represents a field traversal in FHIRPath (e.g., Patient.name).
 *
 * <p>Result cardinality follows FHIRPath semantics:
 *
 * <ul>
 *   <li>If target is MANY or field is MANY → result is MANY
 *   <li>If both target and field are SINGLE → result is SINGLE
 * </ul>
 *
 * @param target the target expression being traversed
 * @param fieldSpec the field specification describing the traversed field
 */
public record Traversal(@Nonnull IRNode target, @Nonnull FieldSpec fieldSpec) implements IRNode {

  @Override
  @Nonnull
  public Shape getShape() {
    // Join cardinalities: MANY if either is MANY, otherwise SINGLE
    final Cardinality resultCardinality = target.getCardinality().join(fieldSpec.getCardinality());
    return Shape.of(fieldSpec.getType(), resultCardinality);
  }

  @Override
  @Nonnull
  public <T> T accept(@Nonnull final IRNodeVisitor<T> visitor) {
    return visitor.visitTraversal(this);
  }

  /** Returns the field name, exposed for visitor access. */
  public String name() {
    return fieldSpec.getName();
  }
}
