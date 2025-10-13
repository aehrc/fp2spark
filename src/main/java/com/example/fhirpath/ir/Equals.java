package com.example.fhirpath.ir;

import com.example.fhirpath.analyzer.FunctionSignature;
import com.example.fhirpath.typing.Type;
import com.example.fhirpath.typing.TypeSystem;

import javax.annotation.Nonnull;
import java.util.List;

public record Equals(IRNode left, IRNode right) implements IRNode {

    public static final List<FunctionSignature> SIGNATURES = TypeSystem.allTypes()
            .map(t -> FunctionSignature.biOperator(t, Type.BOOLEAN))
            .toList();

    @Override
    @Nonnull
    public Type getType() {
        return Type.BOOLEAN;
    }

    @Override
    @Nonnull
    public <T> T accept(@Nonnull IRNodeVisitor<T> visitor) {
        return visitor.visitEquals(this);
    }
}
