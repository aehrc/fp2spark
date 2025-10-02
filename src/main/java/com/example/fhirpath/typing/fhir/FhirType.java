package com.example.fhirpath.typing.fhir;

import com.example.fhirpath.typing.PrimitiveType;
import com.example.fhirpath.typing.Type;

import javax.annotation.Nonnull;

public record FhirType(@Nonnull PrimitiveType systemType) implements Type {

    @Override
    public String getName() {
        return "FHIR." + systemType.name().toLowerCase();
    }

    @Override
    public boolean isPrimitive() {
        return systemType.isPrimitive();
    }

    @Override
    public boolean isComplex() {
        return systemType.isComplex();
    }

    @Override
    public boolean isCollection() {
        return systemType.isCollection();
    }
}
