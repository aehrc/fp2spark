package com.example.fhirpath.ast;

import jakarta.annotation.Nullable;

public record AstLiteral(@Nullable Object value) implements AstNode {

    /**
     * Singleton instance representing null/empty collection literal.
     * Used for padding optional parameters in variadic functions.
     */
    public static final AstLiteral NULL = new AstLiteral(null);
}
