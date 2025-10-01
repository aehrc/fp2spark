package com.example.fhirpath.ast;

public record AstTraversal(String path, AstNode target) implements AstNode {
    // Constructor for traversals without a target (standalone field access)
    public AstTraversal(String path) {
        this(path, null);
    }
}
