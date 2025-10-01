package com.example.fhirpath.ir;

import com.example.fhirpath.typing.Type;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.functions;

public record Literal(Object value, Type type) implements SingularIRNode {
    @Override
    public Type getType() { return type; }

    @Override
    public Column eval() {
        return functions.lit(value);
    }
}
