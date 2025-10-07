package com.example.fhirpath.ir.builder;

import com.example.fhirpath.analyzer.FunctionSignature;
import com.example.fhirpath.ir.IRNode;

import javax.annotation.Nonnull;
import java.util.List;

public interface IRNodeBuilder {
    @Nonnull
    IRNode build(@Nonnull IRNode... children);

    List<FunctionSignature> getSignatures();

    @Nonnull
    static IRNodeBuilder forClass(@Nonnull final Class<? extends IRNode> irClass) {
        return new IRClassBuilder(irClass);
    }
}
