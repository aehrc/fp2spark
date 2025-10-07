package com.example.fhirpath.ir;

import com.example.fhirpath.analyzer.FunctionSignature;
import com.example.fhirpath.analyzer.OverloadResolver;
import com.example.fhirpath.typing.PrimitiveType;
import com.example.fhirpath.typing.Type;
import org.apache.spark.sql.Column;

import java.util.List;

import static com.example.fhirpath.typing.Type.INTEGER;

public record Sub(IRNode left, IRNode right, Type resultType) implements IRNode {

    // Allowed overloads for subtraction
    public static final List<FunctionSignature> SUB_SIGNATURES = List.of(
            FunctionSignature.biOperator(INTEGER),
            FunctionSignature.biOperator(Type.DECIMAL)
    );

    // Factory to resolve overloads and perform necessary implicit casts
    public static Sub create(IRNode left, IRNode right) {
        OverloadResolver.ResolvedCall resolved =
                OverloadResolver.resolveBinary(SUB_SIGNATURES, left, right);
        return new Sub(resolved.left(), resolved.right(), resolved.resultType());
    }

    @Override
    public Type getType() {
        return resultType;
    }

    @Override
    public Column eval() {
        return switch ((PrimitiveType) resultType) {
            case INTEGER, DECIMAL -> left.eval().minus(right.eval());
            default -> throw new IllegalArgumentException("Unsupported result type for Sub: " + resultType);
        };
    }
}
