package com.example.fhirpath.ir;

import com.example.fhirpath.typing.Type;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.functions;

public class Count implements IRNode {
    private final IRNode child;

    public Count(IRNode child) {
        this.child = child;
    }

    public IRNode child() {
        return child;
    }

    @Override
    public Type getType() {
        return Type.INTEGER;
    }

    @Override
    public Column eval() {
        return functions.when(child.eval().isNotNull(), functions.lit(1))
                .otherwise(functions.lit(0));
    }
}

