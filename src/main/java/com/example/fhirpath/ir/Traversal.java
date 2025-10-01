package com.example.fhirpath.ir;

import com.example.fhirpath.typing.Type;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.functions;

public class Traversal implements IRNode {
    private final String path;

    public Traversal(String path) {
        this.path = path;
    }

    public String path() { return path; }

    @Override
    public Type getType() { return Type.UNKNOWN; }

    @Override
    public Column eval() {
        return functions.col(path);
    }
}

