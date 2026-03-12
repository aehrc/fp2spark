package com.example.fhirpath.typing;

/**
 * Represents a FHIRPath element type.
 *
 * <p>In the element-first type model, Type represents the kind of element, while {@link
 * Cardinality} represents how many elements (0..1 or 0..*).
 *
 * <p>Types are combined with cardinality to form {@link Shape}s.
 */
public interface Type {
  /** Returns the name of this type. */
  String getName();

  /** Returns whether this type is a primitive type. */
  boolean isPrimitive();

  /** Returns whether this type is a complex type. */
  boolean isComplex();
}
