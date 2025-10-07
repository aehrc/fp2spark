package com.example.fhirpath.ir;

import com.example.fhirpath.analyzer.FunctionSignature;
import com.example.fhirpath.typing.Type;
import com.example.fhirpath.typing.TypeSystem;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.functions;

import java.util.List;

public record Equals(IRNode left, IRNode right) implements IRNode {

    public static final List<FunctionSignature> SIGNATURES = TypeSystem.allTypes()
            .map(t -> FunctionSignature.biOperator(t, Type.BOOLEAN))
            .toList();

    @Override
    public Type getType() {
        return Type.BOOLEAN;
    }

    @Override
    public Column eval() {
        Type leftType = left.getType();
        Type rightType = right.getType();
        // If types differ, return false immediately
        if (leftType == Type.NULL || rightType == Type.NULL) {
            return functions.lit(null);
        }
        if (leftType != rightType) {
            return functions.lit(false);
        }
        // If types are the same, perform actual equality comparison
        return left.eval().equalTo(right.eval());
    }
}
