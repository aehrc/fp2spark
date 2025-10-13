package com.example.fhirpath.ir;

import com.example.fhirpath.typing.ResourceType;
import com.example.fhirpath.typing.Type;

import javax.annotation.Nonnull;

public record Resource(ResourceType type) implements IRNode {
    @Override
    @Nonnull
    public Type getType() {
        return type;
    }

    @Override
    @Nonnull
    public <T> T accept(@Nonnull IRNodeVisitor<T> visitor) {
        return visitor.visitResource(this);
    }
}
