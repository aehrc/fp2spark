package com.example.fhirpath.typing;

import jakarta.annotation.Nonnull;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
  INTEGER("Integer"),
  DECIMAL("Decimal"),
  BOOLEAN("Boolean"),
  STRING("String"),
  DATE("Date"),
  DATE_TIME("DateTime"),
  TIME("Time"),
  QUANTITY("Quantity"),
  CODING("Coding"),
  NULL("null"),
  ANY("unknown");

  private static final Map<String, PrimitiveType> BY_NAME =
      Stream.of(values())
          .filter(t -> t != NULL && t != ANY)
          .collect(Collectors.toUnmodifiableMap(PrimitiveType::getName, Function.identity()));

  private final String name;

  PrimitiveType(final String name) {
    this.name = name;
  }

  /**
   * Returns the PrimitiveType for the given name, if one exists.
   *
   * @param name the type name (e.g., "String", "Integer", "Coding")
   * @return the matching PrimitiveType, or empty if not found
   */
  @Nonnull
  public static Optional<PrimitiveType> fromName(@Nonnull final String name) {
    return Optional.ofNullable(BY_NAME.get(name));
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
