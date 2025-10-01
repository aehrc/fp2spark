package com.example.fhirpath.ast;

public class AstTraversal implements AstNode {
    private final String path;

    public AstTraversal(String path) {
        this.path = path;
    }

    public String getPath() { return path; }

    @Override
    public int getId() { return System.identityHashCode(this); }
}

