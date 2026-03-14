package com.example.fhirpath.typing;

import java.util.Optional;

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

  /**
   * Resolves a field specification for the given field name.
   *
   * <p>Complex types override this to provide field resolution. Primitive types, {@link
   * FhirPrimitiveType}, and {@link LambdaType} return empty by default.
   *
   * @param fieldName the name of the field to resolve
   * @return the field specification, or empty if the field cannot be resolved
   */
  default Optional<FieldSpec> resolveField(final String fieldName) {
    return Optional.empty();
  }
}
