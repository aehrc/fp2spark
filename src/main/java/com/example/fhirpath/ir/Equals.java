package com.example.fhirpath.ir;

import com.example.fhirpath.analyzer.FunctionSignature;
import com.example.fhirpath.analyzer.OverloadResolver;
import com.example.fhirpath.typing.Type;
import com.example.fhirpath.typing.TypeSystem;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.functions;

import java.util.List;

public record Equals(IRNode left, IRNode right) implements SingularIRNode {

    static final List<FunctionSignature> EQUALS_SIGNATURES = TypeSystem.allTypes()
            .map(t -> FunctionSignature.biOperator(t, Type.BOOLEAN))
            .toList();

    // Factory method for equality - accepts any types, always returns BOOLEAN
    public static Equals create(IRNode left, IRNode right) {
        OverloadResolver.ResolvedCall resolved =
                OverloadResolver.resolveBinary(EQUALS_SIGNATURES, left, right);
        return new Equals(resolved.left(), resolved.right());
    }

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
