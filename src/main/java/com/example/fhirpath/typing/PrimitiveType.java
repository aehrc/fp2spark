package com.example.fhirpath.typing;

import java.util.Optional;

/**
 * Enumeration of FHIRPath primitive types.
 *
 * <p>Primitive types represent the fundamental value types in FHIRPath such as integers, strings,
 * booleans, and temporal types.
 *
 * <p>In the element-first type system, PrimitiveType represents the element type, while {@link
 * Cardinality} specifies how many elements (0..1 or 0..*).
 */
public enum PrimitiveType implements Type {
  INTEGER("integer"),
  DECIMAL("decimal"),
  BOOLEAN("boolean"),
  STRING("string"),
  DATE("date"),
  DATE_TIME("dateTime"),
  TIME("time"),
  QUANTITY("quantity"),
  CODING("Coding"),
  NULL("null"),
  ANY("unknown");

  private final String name;

  PrimitiveType(final String name) {
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

  @Override
  public Optional<FieldSpec> resolveField(final String fieldName) {
    if (this == CODING) {
      return CodingValue.resolveField(fieldName);
    }
    return Optional.empty();
  }
}
