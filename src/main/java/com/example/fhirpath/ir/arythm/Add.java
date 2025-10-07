package com.example.fhirpath.ir.arythm;

import com.example.fhirpath.analyzer.FunctionSignature;
import com.example.fhirpath.ir.IRNode;
import com.example.fhirpath.typing.PrimitiveType;
import com.example.fhirpath.typing.Type;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.functions;

import java.util.List;

import static com.example.fhirpath.analyzer.FunctionSignature.biOperator;
import static com.example.fhirpath.analyzer.FunctionSignature.biOperatorLeft;
import static com.example.fhirpath.sql.DateTime.dateTime;
import static com.example.fhirpath.sql.Quantity.quantity;

// This requires singular arguments
public record Add(IRNode left, IRNode right) implements IRNode {

    // Static signatures for Add operation
    public static final List<FunctionSignature> SIGNATURES = List.of(
            biOperator(Type.INTEGER),
            biOperator(Type.DECIMAL),
            biOperator(Type.QUANTITY),
            biOperatorLeft(Type.DATE_TIME, Type.QUANTITY),
            biOperator(Type.STRING)
            // maybe I can just add date/time arythmetics here
            // as the result DATE/TIME is only allowed for DATE + TIME QUANTITY (indeed DATETIME + TIME QUANTITY)
            // as implict cast is available from DATETIME to DATE
    );

    @Override
    public Type getType() {
        return left.getType();
    }

    @Override
    public Column eval() {
        return switch ((PrimitiveType) getType()) {
            case STRING -> functions.concat(left.eval(), right.eval());
            case INTEGER, DECIMAL -> left.eval().plus(right.eval());
            case DATE_TIME -> dateTime(left.eval()).plus(quantity(right.eval()));
            case QUANTITY -> quantity(left.eval()).plus(quantity(right.eval()));
            default -> throw new IllegalArgumentException("Unsupported result type for Add: " + getType());
        };
    }
}
