package com.example.fhirpath.typing;

import jakarta.annotation.Nonnull;
import java.util.stream.Stream;

/**
 * Utilities for working with the FHIRPath type system.
 * <p>
 * Phase 1: Only System types (INTEGER, DECIMAL, BOOLEAN, STRING) are supported.
 * FHIR types (Date, DateTime, Time, Quantity, FhirType wrapper) are deferred to Phase 2.
 */
public final class TypeSystem {
    private TypeSystem() {
    }

    /**
     * Checks if a value of type {@code from} can be adapted/cast to type {@code to}.
     *
     * <p>Implements the adaptation rules from the FHIRPath type system:
     * <ul>
     *   <li>INTEGER → DECIMAL</li>
     *   <li>NULL → any type</li>
     * </ul>
     * <p>
     * Phase 1: Only INTEGER → DECIMAL adaptation is supported.
     * Additional adaptations (DECIMAL → QUANTITY, DATE → DATE_TIME, FhirType unwrapping)
     * are deferred to Phase 2.
     */
    public static boolean canCast(Type from, Type to) {
        if (from == to) return true;
        if (from == PrimitiveType.NULL) return true;

        // Primitive casts (adaptation rules) - Phase 1: only INTEGER → DECIMAL
        if (from == PrimitiveType.INTEGER && to == PrimitiveType.DECIMAL) return true;

        return false;
    }

    @Nonnull
    public static Stream<Type> allTypes() {
        return Stream.of(PrimitiveType.values());
    }

    @Nonnull
    public static Stream<Type> definedTypes() {
        return Stream.of(PrimitiveType.values())
                .map(Type.class::cast)
                .filter(t -> t != PrimitiveType.ANY);
    }
}

