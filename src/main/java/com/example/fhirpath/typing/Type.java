package com.example.fhirpath.typing;

public interface Type {
    String getName();

    boolean isPrimitive();

    boolean isComplex();

    boolean isCollection();

    default Type effectiveType() {
        return this;
    }
}
