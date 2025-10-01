package com.example.fhirpath.ir;

import com.example.fhirpath.typing.Type;
import org.apache.spark.sql.Column;

public record Add(IRNode left, IRNode right) implements IRNode {
    @Override
    public Type getType() { return Type.DECIMAL; }

    @Override
    public Column eval() {
        return left.eval().plus(right.eval());
    }
}
