package com.example.fhirpath.ir;

import com.example.fhirpath.analyzer.FunctionSignature;
import com.example.fhirpath.typing.CollectionType;
import com.example.fhirpath.typing.Type;
import com.example.fhirpath.typing.TypeSystem;

import javax.annotation.Nonnull;
import java.util.List;

public record Union(IRNode left, IRNode right) implements IRNode {

    // Static signatures for Union operation - overloaded for all defined types
    public static final List<FunctionSignature> SIGNATURES =
            TypeSystem.definedTypes()
                    .map(CollectionType::new)
                    .map(FunctionSignature::biOperator)
                    .toList();

    @Override
    @Nonnull
    public Type getType() {
        // we may get singluar value here collection but the result is always a collection
        // except for null cases.
        return new CollectionType(left.getType());
    }

    @Override
    @Nonnull
    public <T> T accept(@Nonnull IRNodeVisitor<T> visitor) {
        return visitor.visitUnion(this);
    }
}
