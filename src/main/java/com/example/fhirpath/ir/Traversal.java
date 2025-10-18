package com.example.fhirpath.ir;

import com.example.fhirpath.typing.Cardinality;
import com.example.fhirpath.typing.FieldSpec;
import com.example.fhirpath.typing.Shape;

import jakarta.annotation.Nonnull;

/**
 * Represents a field traversal in FHIRPath (e.g., Patient.name).
 *
 * <p>Result cardinality follows FHIRPath semantics:
 * <ul>
 *   <li>If target is MANY or field is MANY → result is MANY</li>
 *   <li>If both target and field are SINGLE → result is SINGLE</li>
 * </ul>
 */
public record Traversal(@Nonnull IRNode target, @Nonnull FieldSpec fieldSpec) implements IRNode {

    @Override
    @Nonnull
    public Shape getShape() {
        // Join cardinalities: MANY if either is MANY, otherwise SINGLE
        Cardinality resultCardinality = target.getCardinality().join(fieldSpec.getCardinality());
        return Shape.of(fieldSpec.getType(), resultCardinality);
    }

    @Override
    @Nonnull
    public <T> T accept(@Nonnull IRNodeVisitor<T> visitor) {
        return visitor.visitTraversal(this);
    }

    // Expose field name for visitor
    public String name() {
        return fieldSpec.getName();
    }
}
