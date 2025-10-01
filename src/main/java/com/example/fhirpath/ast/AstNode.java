package com.example.fhirpath.ast;

public interface AstNode {
    default int getId() {
        return System.identityHashCode(this);
    }
}

