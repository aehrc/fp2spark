package com.example.fhirpath.ir;

import com.example.fhirpath.typing.CollectionType;
import com.example.fhirpath.typing.FieldSpec;
import com.example.fhirpath.typing.Type;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.functions;

import javax.annotation.Nonnull;

public record Traversal(@Nonnull IRNode target, @Nonnull FieldSpec fieldSpec) implements IRNode {

    @Override
    public boolean isSingular() {
        return fieldSpec.isSingular() && target.isSingular();
    }

    @Override
    public Type getType() {
        return target.isSingular()
                ? fieldSpec.getType()
                : new CollectionType(fieldSpec.getType());
    }

    @Override
    public Column eval() {
        // we may need depending on the situation
        // - filter out nulls
        // - flatten the array
        Column result = target.eval().getField(fieldSpec.getName());
        if (!target.isSingular()) {
            result = functions.filter(result, Column::isNotNull);
            // and
            if (!fieldSpec.isSingular()) {
                result = functions.flatten(result);
            }
        }
        return result;
    }
}
