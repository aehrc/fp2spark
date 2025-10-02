package com.example.fhirpath.ir;

import com.example.fhirpath.typing.FieldSpec;
import com.example.fhirpath.typing.Type;
import org.apache.spark.sql.Column;

import javax.annotation.Nonnull;

public record Traversal(@Nonnull IRNode target, @Nonnull FieldSpec fieldSpec) implements IRNode {

    @Override
    public boolean isSingular() {
        return fieldSpec.isSingular();
    }

    @Override
    public Type getType() {
        return fieldSpec.getType();
    }

    @Override
    public Column eval() {
        return target.eval().getField(fieldSpec.getName());
    }
}
