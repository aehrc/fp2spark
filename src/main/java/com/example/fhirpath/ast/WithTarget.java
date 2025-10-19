package com.example.fhirpath.ast;

import jakarta.annotation.Nullable;
import jakarta.annotation.Nonnull;

/**
 * Interface for AST nodes that have an optional target expression.
 * When target is null, an implicit target is used (either $this in lambda context or %context otherwise).
 *
 * @param <T> The concrete type implementing this interface (for fluent API)
 */
public interface WithTarget<T extends WithTarget<T>> extends AstNode {

    /**
     * Returns the target expression, or null if using implicit target.
     */
    @Nullable
    AstNode target();

    /**
     * Creates a copy of this node with the specified target.
     * Used to resolve implicit targets during analysis.
     */
    @Nonnull
    T withTarget(@Nonnull AstNode target);
}
