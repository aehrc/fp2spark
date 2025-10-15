package com.example.fhirpath.ir;

import com.example.fhirpath.typing.Type;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

public record Literal(@Nullable Object value, @Nonnull Type type) implements IRNode {
    @Override
    @Nonnull
    public Type getType() {
        return type;
    }

    @Override
    @Nonnull
    public <T> T accept(@Nonnull IRNodeVisitor<T> visitor) {
        return visitor.visitLiteral(this);
    }
}
