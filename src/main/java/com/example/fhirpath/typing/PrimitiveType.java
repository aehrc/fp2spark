package com.example.fhirpath.typing;

/**
 * Enumeration of FHIRPath primitive types.
 *
 * <p>Primitive types represent the fundamental value types in FHIRPath
 * such as integers, strings, booleans.
 *
 * <p>Phase 1 supports System types only (INTEGER, DECIMAL, BOOLEAN, STRING).
 * FHIR-specific types (Date, DateTime, Time, Quantity) are deferred to Phase 2.
 *
 * <p>In the element-first type system, PrimitiveType represents the element type,
 * while {@link Cardinality} specifies how many elements (0..1 or 0..*).
 */
public enum PrimitiveType implements Type {
    INTEGER("integer"),
    DECIMAL("decimal"),
    BOOLEAN("boolean"),
    STRING("string"),
    NULL("null"),
    ANY("unknown");

    private final String name;

    PrimitiveType(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public boolean isPrimitive() {
        return true;
    }

    @Override
    public boolean isComplex() {
        return false;
    }
}
