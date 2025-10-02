package com.example.fhirpath.ast;

import javax.annotation.Nonnull;

public record AstVariable(@Nonnull String name) implements AstNode {

    public static final String CONTEXT_VARIABLE = "%context";
    public static final String RESOURCE_VARIABLE = "%resource";

    public AstVariable {
        if (!name.startsWith("%")) {
            throw new IllegalArgumentException("Variable name must start with %: " + name);
        }
    }

    @Nonnull
    public static AstVariable contextVariable() {
        return new AstVariable(CONTEXT_VARIABLE);
    }

    @Nonnull
    public static AstVariable resourceVariable() {
        return new AstVariable(RESOURCE_VARIABLE);
    }
}
