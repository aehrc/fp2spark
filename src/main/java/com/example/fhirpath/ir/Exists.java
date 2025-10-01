package com.example.fhirpath.ir;

import com.example.fhirpath.typing.Type;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.functions;

public class Exists implements IRNode {
    private final IRNode child;

    public Exists(IRNode child) {
        this.child = child;
    }

    public IRNode child() {
        return child;
    }

    @Override
    public Type getType() {
        return Type.BOOLEAN;
    }

    @Override
    public Column eval() {
        return functions.when(child.eval().isNotNull(), functions.lit(true))
                .otherwise(functions.lit(false));
    }
}

