package com.example.fhirpath.analyzer;

import com.example.fhirpath.typing.Cardinality;
import com.example.fhirpath.typing.Type;
import jakarta.annotation.Nonnull;

/**
 * Parameter specification for Phase 1: simple type + cardinality.
 *
 * <p>In Phase 1, we enumerate types explicitly without type variables.
 * Each parameter has a concrete type and cardinality.
 *
 * <p>Phase 2 will add support for type variables and constraints.
 */
public record ParamSpec(
    @Nonnull Type type,
    @Nonnull Cardinality cardinality
) {
    /**
     * Creates a parameter spec for a SINGLE cardinality parameter (?T).
     */
    @Nonnull
    public static ParamSpec single(@Nonnull Type type) {
        return new ParamSpec(type, Cardinality.SINGLE);
    }

    /**
     * Creates a parameter spec for a MANY cardinality parameter (*T).
     */
    @Nonnull
    public static ParamSpec many(@Nonnull Type type) {
        return new ParamSpec(type, Cardinality.MANY);
    }

    @Override
    public String toString() {
        return (cardinality == Cardinality.SINGLE ? "?" : "*") + type.getName();
    }
}
