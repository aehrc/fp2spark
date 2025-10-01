package com.example.fhirpath.ast;

public class AstLiteral implements AstNode {
    private final Object value;

    public AstLiteral(Object value) {
        this.value = value;
    }

    public Object getValue() { return value; }

    @Override
    public int getId() { return System.identityHashCode(this); }
}

