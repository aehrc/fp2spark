package com.example.fhirpath.ir;

import com.example.fhirpath.typing.Shape;
import com.example.fhirpath.typing.fhir.FhirType;

import jakarta.annotation.Nonnull;

/**
 * Represents a cast from FHIR type to system type (e.g., FhirString → String).
 * Preserves the cardinality of the child.
 */
public record CastToSystem(IRNode child) implements IRNode {
    @Override
    @Nonnull
    public Shape getShape() {
        // Extract system type from FHIR type, preserve cardinality
        return Shape.of(
            ((FhirType) child.getType()).systemType(),
            child.getCardinality()
        );
    }

    @Override
    @Nonnull
    public <T> T accept(@Nonnull IRNodeVisitor<T> visitor) {
        return visitor.visitCastToSystem(this);
    }
}
