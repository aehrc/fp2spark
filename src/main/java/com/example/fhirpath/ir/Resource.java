package com.example.fhirpath.ir;

import com.example.fhirpath.typing.ResourceType;
import com.example.fhirpath.typing.Type;
import org.apache.spark.sql.Column;

import static org.apache.spark.sql.functions.col;
import static org.apache.spark.sql.functions.lit;

public record Resource(ResourceType type) implements SingularIRNode {
    @Override
    public Type getType() {
        return type;
    }

    @Override
    public Column eval() {
        return type != ResourceType.EMPTY ? col(type.getResourceName()) : lit(null);
    }
}
