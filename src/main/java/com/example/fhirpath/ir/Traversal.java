package com.example.fhirpath.ir;

import com.example.fhirpath.typing.Type;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.functions;

public record Traversal(String path) implements IRNode {
    @Override
    public Type getType() { return Type.UNKNOWN; }

    @Override
    public Column eval() {
        return functions.col(path);
    }
}
