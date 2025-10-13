package com.example.fhirpath.ir;

import com.example.fhirpath.typing.CollectionType;
import com.example.fhirpath.typing.FieldSpec;
import com.example.fhirpath.typing.Type;

import javax.annotation.Nonnull;

public record Traversal(@Nonnull IRNode target, @Nonnull FieldSpec fieldSpec) implements IRNode {

    @Override
    public boolean isSingular() {
        return fieldSpec.isSingular() && target.isSingular();
    }

    @Override
    @Nonnull
    public Type getType() {
        return target.isSingular()
                ? fieldSpec.getType()
                : new CollectionType(fieldSpec.getType());
    }

    @Override
    @Nonnull
    public <T> T accept(@Nonnull IRNodeVisitor<T> visitor) {
        return visitor.visitTraversal(this);
    }

    // Expose field name for visitor
    public String name() {
        return fieldSpec.getName();
    }
}
