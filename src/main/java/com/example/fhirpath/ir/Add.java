package com.example.fhirpath.ir;

import com.example.fhirpath.analyzer.FunctionSignature;
import com.example.fhirpath.analyzer.OverloadResolver;
import com.example.fhirpath.typing.PrimitiveType;
import com.example.fhirpath.typing.Type;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.functions;

import java.util.List;

// This requires singular arguments
public record Add(IRNode left, IRNode right, Type resultType) implements IRNode {

    // Static signatures for Add operation
    public static final List<FunctionSignature> ADD_SIGNATURES = List.of(
            FunctionSignature.biOperator(Type.INTEGER),
            FunctionSignature.biOperator(Type.DECIMAL),
            FunctionSignature.biOperator(Type.STRING)
            // maybe I can just add date/time arythmetics here
            // as the result DATE/TIME is only allowed for DATE + TIME QUANTITY (indeed DATETIME + TIME QUANTITY)
            // as implict cast is available from DATETIME to DATE
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
        return switch ((PrimitiveType) resultType) {
            case STRING -> functions.concat(left.eval(), right.eval());
            case INTEGER, DECIMAL -> left.eval().plus(right.eval());
            //case DATE_TIME -> DateTimeSql.add(left.eval(), right.eval());
            //case QUANTITY -> QuantitySql.add(left.eval(), right.eval());
            default -> throw new IllegalArgumentException("Unsupported result type for Add: " + resultType);
        };
    }
}
