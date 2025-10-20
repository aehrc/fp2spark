package com.example.fhirpath.ir;

import com.example.fhirpath.typing.Shape;

import jakarta.annotation.Nonnull;

/**
 * Represents a union operation (|) in FHIRPath.
 * Result is always MANY cardinality (0..*).
 */
public record Union(IRNode left, IRNode right) implements IRNode {

    @Override
    @Nonnull
    public Shape getShape() {
        // Union always produces MANY cardinality
        // Element type from left operand (both sides must have same type)
        return Shape.many(left.getType());
    }

    @Override
    @Nonnull
    public <T> T accept(@Nonnull IRNodeVisitor<T> visitor) {
        return visitor.visitUnion(this);
    }
}
