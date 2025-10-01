package com.example.fhirpath.ir;

import com.example.fhirpath.typing.Type;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.functions;

public class Exists implements IRNode {
    private final IRNode child;

    public Exists(IRNode child) {
        this.child = child;
    }

    public IRNode child() { return child; }

    @Override
    public Type getType() { return Type.BOOLEAN; }

    @Override
    public Column eval() {
        // Best-effort: if child yields an array column, use size > 0, otherwise cast to boolean.
        Column c = child.eval();
        return functions.size(c).gt(0);
    }
}

