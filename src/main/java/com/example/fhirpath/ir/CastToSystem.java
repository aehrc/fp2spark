package com.example.fhirpath.ir;

import com.example.fhirpath.typing.Type;
import com.example.fhirpath.typing.fhir.FhirType;

import javax.annotation.Nonnull;

public record CastToSystem(IRNode child) implements IRNode {
    @Override
    @Nonnull
    public Type getType() {
        return ((FhirType) child.getType()).systemType();
    }

    @Override
    public boolean isSingular() {
        return child.isSingular();
    }

    @Override
    @Nonnull
    public <T> T accept(@Nonnull IRNodeVisitor<T> visitor) {
        return visitor.visitCastToSystem(this);
    }
}
