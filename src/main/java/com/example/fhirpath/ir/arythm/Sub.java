package com.example.fhirpath.ir.arythm;

import com.example.fhirpath.analyzer.FunctionSignature;
import com.example.fhirpath.ir.IRNode;
import com.example.fhirpath.typing.PrimitiveType;
import com.example.fhirpath.typing.Type;
import org.apache.spark.sql.Column;

import java.util.List;

import static com.example.fhirpath.typing.Type.INTEGER;

public record Sub(IRNode left, IRNode right) implements IRNode {

    // Allowed overloads for subtraction
    public static final List<FunctionSignature> SIGNATURES = List.of(
            FunctionSignature.biOperator(INTEGER),
            FunctionSignature.biOperator(Type.DECIMAL)
    );

    @Override
    public Type getType() {
        return left.getType();
    }

    @Override
    public Column eval() {
        return switch ((PrimitiveType) getType()) {
            case INTEGER, DECIMAL -> left.eval().minus(right.eval());
            default -> throw new IllegalArgumentException("Unsupported result type for Sub: " + getType());
        };
    }
}
