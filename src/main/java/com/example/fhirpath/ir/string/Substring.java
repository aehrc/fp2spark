package com.example.fhirpath.ir.string;

import com.example.fhirpath.analyzer.FunctionSignature;
import com.example.fhirpath.ir.IRNode;
import com.example.fhirpath.typing.PrimitiveType;
import com.example.fhirpath.typing.Type;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.functions;

import javax.annotation.Nullable;
import java.util.List;

import static org.apache.spark.sql.functions.lit;

public record Substring(IRNode target, IRNode pos, @Nullable IRNode length) implements IRNode {

    public static final List<FunctionSignature> SIGNATURES = List.of(
            //  add minimal arrity 2 (STRING, INTEGER)
            new FunctionSignature(List.of(Type.STRING, Type.INTEGER, Type.INTEGER),
                    Type.STRING, 2)
    );

    @Override
    public Type getType() {
        return Type.STRING;
    }

    @Override
    public Column eval() {
        return switch ((PrimitiveType) getType()) {
            case STRING -> length != null
                    ? functions.substr(target.eval(), pos.eval().plus(lit(1)), length.eval())
                    : functions.substr(target.eval(), pos.eval().plus(lit(1)));
            default -> throw new IllegalArgumentException("Unsupported result type for Substring: " + getType());
        };
    }
}
