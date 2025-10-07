package com.example.fhirpath.ir.arythm;

import com.example.fhirpath.analyzer.FunctionSignature;
import com.example.fhirpath.ir.IRNode;
import com.example.fhirpath.typing.PrimitiveType;
import com.example.fhirpath.typing.Type;
import org.apache.spark.sql.Column;

import java.util.List;

import static com.example.fhirpath.analyzer.FunctionSignature.biOperator;
import static com.example.fhirpath.sql.Quantity.quantity;

// This requires singular arguments
public record Divide(IRNode left, IRNode right) implements IRNode {

    // Static signatures for Add operation
    public static final List<FunctionSignature> SIGNATURES = List.of(
            // INTEGER is supported by implicit cast to DECIMAL
            biOperator(Type.DECIMAL),
            biOperator(Type.QUANTITY)
    );

    @Override
    public Type getType() {
        return left.getType();
    }

    @Override
    public Column eval() {
        return switch ((PrimitiveType) getType()) {
            case DECIMAL -> left.eval().divide(right.eval());
            case QUANTITY -> quantity(left.eval()).divide(quantity(right.eval()));
            default -> throw new IllegalArgumentException("Unsupported result type for divide: " + getType());
        };
    }
}
