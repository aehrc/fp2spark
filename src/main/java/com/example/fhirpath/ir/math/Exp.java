package com.example.fhirpath.ir.math;

import com.example.fhirpath.analyzer.FunctionSignature;
import com.example.fhirpath.ir.IRNode;
import com.example.fhirpath.typing.PrimitiveType;
import com.example.fhirpath.typing.Type;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.functions;

import java.util.List;

public record Exp(IRNode target) implements IRNode {

    // Allowed overloads for subtraction
    public static final List<FunctionSignature> SIGNATURES = List.of(
            new FunctionSignature(List.of(Type.DECIMAL), Type.DECIMAL)
    );

    @Override
    public Type getType() {
        return Type.DECIMAL;
    }

    @Override
    public Column eval() {
        return switch ((PrimitiveType) getType()) {
            case DECIMAL -> functions.exp(target.eval());
            default -> throw new IllegalArgumentException("Unsupported result type for Exp: " + getType());
        };
    }
}
