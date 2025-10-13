package com.example.fhirpath.ir;

import com.example.fhirpath.typing.Type;

import jakarta.annotation.Nonnull;

public record Cast(IRNode child, Type targetType) implements IRNode {
    @Override
    @Nonnull
    public Type getType() {
        return targetType;
    }

    @Override
    public boolean isSingular() {
        return !targetType.isCollection();
    }

    @Override
    @Nonnull
    public <T> T accept(@Nonnull IRNodeVisitor<T> visitor) {
        return visitor.visitCast(this);
    }
}
