package com.example.fhirpath.ir;

import com.example.fhirpath.typing.Type;
import org.apache.spark.sql.Column;

public class Add implements IRNode {
    private final IRNode left;
    private final IRNode right;

    public Add(IRNode left, IRNode right) {
        this.left = left;
        this.right = right;
    }

    public IRNode left() { return left; }
    public IRNode right() { return right; }

    @Override
    public Type getType() { return Type.DECIMAL; }

    @Override
    public Column eval() {
        return left.eval().plus(right.eval());
    }
}

