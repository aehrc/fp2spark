package com.example.fhirpath.ir;

import com.example.fhirpath.typing.Shape;

import jakarta.annotation.Nonnull;

/**
 * Represents a combine operation (;) in FHIRPath - ordered concatenation.
 * Result is always MANY cardinality (0..*).
 *
 * <p>Note: This is distinct from union (|) which has undefined order per FHIRPath spec.
 * The combine operator preserves left-to-right ordering.
 */
public record Combine(IRNode left, IRNode right) implements IRNode {

    @Override
    @Nonnull
    public Shape getShape() {
        // Combine always produces MANY cardinality
        // Element type from left operand (both sides must have same type)
        return Shape.many(left.getType());
    }

    @Override
    @Nonnull
    public <T> T accept(@Nonnull IRNodeVisitor<T> visitor) {
        return visitor.visitCombine(this);
    }
}
