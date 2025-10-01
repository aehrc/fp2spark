package com.example.fhirpath.ir;

import com.example.fhirpath.analyzer.FunctionSignature;
import com.example.fhirpath.analyzer.OverloadResolver;
import com.example.fhirpath.typing.Type;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.functions;

import java.util.List;

public record Add(IRNode left, IRNode right, Type resultType) implements IRNode {

    // Static signatures for Add operation
    public static final List<FunctionSignature> ADD_SIGNATURES = List.of(
            FunctionSignature.biOperator(Type.INTEGER),
            FunctionSignature.biOperator(Type.DECIMAL),
            FunctionSignature.biOperator(Type.STRING)
    );

    // Factory method to resolve and create Add with proper types
    public static Add create(IRNode left, IRNode right) {
        OverloadResolver.ResolvedCall resolved =
                OverloadResolver.resolveBinary(ADD_SIGNATURES, left, right);
        return new Add(resolved.left(), resolved.right(), resolved.resultType());
    }

    @Override
    public Type getType() {
        return resultType;
    }

    @Override
    public Column eval() {
        return switch (resultType) {
            case STRING -> functions.concat(left.eval(), right.eval());
            case INTEGER, DECIMAL -> left.eval().plus(right.eval());
            default -> throw new IllegalArgumentException("Unsupported result type for Add: " + resultType);
        };
    }
}
