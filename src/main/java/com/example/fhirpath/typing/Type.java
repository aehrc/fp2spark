package com.example.fhirpath.typing;

/**
 * Represents a FHIRPath element type.
 *
 * <p>In the element-first type model, Type represents the kind of element,
 * while {@link Cardinality} represents how many elements (0..1 or 0..*).
 *
 * <p>Types are combined with cardinality to form {@link Shape}s.
 */
public interface Type {
    String getName();

    boolean isPrimitive();

    boolean isComplex();
}
