package com.example.fhirpath.ir;

import com.example.fhirpath.typing.Type;

import javax.annotation.Nonnull;

public record Exists(IRNode child) implements IRNode {
    @Override
    @Nonnull
    public Type getType() {
        return Type.BOOLEAN;
    }

    @Override
    @Nonnull
    public <T> T accept(@Nonnull IRNodeVisitor<T> visitor) {
        return visitor.visitExists(this);
    }
}
