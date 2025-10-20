package com.example.fhirpath.ir;

import com.example.fhirpath.typing.Shape;
import com.example.fhirpath.typing.Types;

import jakarta.annotation.Nonnull;

/**
 * Represents an equality comparison (=) in FHIRPath.
 * Always returns single BOOLEAN.
 */
public record Equals(IRNode left, IRNode right) implements IRNode {

    @Override
    @Nonnull
    public Shape getShape() {
        return Shape.single(Types.BOOLEAN);
    }

    @Override
    @Nonnull
    public <T> T accept(@Nonnull IRNodeVisitor<T> visitor) {
        return visitor.visitEquals(this);
    }
}
