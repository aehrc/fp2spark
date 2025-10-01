package com.example.fhirpath.ir;

public interface SingularIRNode extends IRNode {
    @Override
    default boolean isSingular() {
        return true;
    }
}
