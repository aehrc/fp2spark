package com.example.fhirpath.ast;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public record AstTraversal(String path, @Nullable AstNode target) implements WithTarget<AstTraversal> {
    // Constructor for traversals without a target (standalone field access)
    public AstTraversal(String path) {
        this(path, null);
    }

    /**
     * Create a new AstTraversal with a different target.
     *
     * @param newTarget the new target node
     * @return a new AstTraversal instance with the updated target
     */
    @Nonnull
    @Override
    public AstTraversal withTarget(@Nonnull AstNode newTarget) {
        return new AstTraversal(this.path, newTarget);
    }
}
