package com.example.fhirpath.analyzer;

import com.example.fhirpath.typing.Cardinality;
import com.example.fhirpath.typing.Shape;
import com.example.fhirpath.typing.Type;
import jakarta.annotation.Nonnull;

/**
 * Result type specification for Phase 1: simple type + cardinality.
 *
 * <p>In Phase 1, result types are statically known (no type variables).
 * The result has a concrete type and cardinality.
 *
 * <p>Phase 2 will add support for type variables and dynamic cardinality resolution.
 */
public record ResultTypeSpec(
    @Nonnull Type type,
    @Nonnull Cardinality cardinality
) {
    /**
     * Creates a result spec for a SINGLE cardinality result (?T).
     */
    @Nonnull
    public static ResultTypeSpec single(@Nonnull Type type) {
        return new ResultTypeSpec(type, Cardinality.SINGLE);
    }

    /**
     * Creates a result spec for a MANY cardinality result (*T).
     */
    @Nonnull
    public static ResultTypeSpec many(@Nonnull Type type) {
        return new ResultTypeSpec(type, Cardinality.MANY);
    }

    /**
     * Converts this result type spec to a Shape.
     */
    @Nonnull
    public Shape toShape() {
        return Shape.of(type, cardinality);
    }

    @Override
    public String toString() {
        return (cardinality == Cardinality.SINGLE ? "?" : "*") + type.getName();
    }
}
