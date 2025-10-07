package com.example.fhirpath.ir.math;

import com.example.fhirpath.analyzer.FunctionSignature;
import com.example.fhirpath.ir.IRNode;
import com.example.fhirpath.typing.PrimitiveType;
import com.example.fhirpath.typing.Type;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.functions;

import java.util.List;
import java.util.stream.Stream;

import static com.example.fhirpath.sql.Quantity.quantity;
import static com.example.fhirpath.typing.Type.INTEGER;

public record Abs(IRNode target) implements IRNode {

    // Allowed overloads for subtraction
    public static final List<FunctionSignature> SIGNATURES = Stream.of(
            INTEGER, Type.DECIMAL, Type.QUANTITY
    ).map(t -> new FunctionSignature(List.of(t), t)).toList();


    @Override
    public Type getType() {
        return target.getType();
    }

    @Override
    public Column eval() {
        // maybe I do not need all this and I couild just registre singantures with lambdas
        // register(Type.INTEGER, functions::abs);
        // register(Type.DECIMAL, functions::abs);
        // register(Type.QUANTITY, bind(Quantity::abs));
        return switch ((PrimitiveType) getType()) {
            case INTEGER, DECIMAL -> functions.abs(target.eval());
            case QUANTITY -> quantity(target.eval()).abs();
            default -> throw new IllegalArgumentException("Unsupported result type for Abs: " + getType());
        };
    }
}
