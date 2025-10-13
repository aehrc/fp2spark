package com.example.fhirpath.ir;

import com.example.fhirpath.typing.Type;
import org.apache.spark.sql.Column;

import javax.annotation.Nonnull;

public record Literal(Object value, Type type) implements IRNode {
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
