package com.example.fhirpath.ir;

import com.example.fhirpath.typing.Type;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.functions;

public class Literal implements IRNode {
    private final Object value;
    private final Type type;

    public Literal(Object value, Type type) {
        this.value = value;
        this.type = type;
    }

    public Object value() { return value; }

    @Override
    public Type getType() { return type; }

    @Override
    public Column eval() {
        return functions.lit(value);
    }
}

