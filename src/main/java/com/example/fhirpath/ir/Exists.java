package com.example.fhirpath.ir;

import com.example.fhirpath.typing.Type;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.functions;

public record Exists(IRNode child) implements SingularIRNode {
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
