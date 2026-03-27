package com.example.fhirpath.test;

import com.example.fhirpath.typing.PrimitiveType;
import jakarta.annotation.Nonnull;

/**
 * Marker for a null value with known type, used in Map-based test data.
 *
 * <p>When a field is empty but has a known type (e.g., {@code stringEmpty("name")}), this marker
 * preserves the type information so that {@link ResourceTypeInference} can infer the correct field
 * type instead of falling back to {@link PrimitiveType#NULL}.
 *
 * @param type the known primitive type of the empty field
 */
public record TypedNull(@Nonnull PrimitiveType type) {

  public static final TypedNull STRING = new TypedNull(PrimitiveType.STRING);
  public static final TypedNull INTEGER = new TypedNull(PrimitiveType.INTEGER);
  public static final TypedNull DECIMAL = new TypedNull(PrimitiveType.DECIMAL);
  public static final TypedNull BOOLEAN = new TypedNull(PrimitiveType.BOOLEAN);
  public static final TypedNull DATE = new TypedNull(PrimitiveType.DATE);
  public static final TypedNull DATE_TIME = new TypedNull(PrimitiveType.DATE_TIME);
  public static final TypedNull TIME = new TypedNull(PrimitiveType.TIME);
  public static final TypedNull QUANTITY = new TypedNull(PrimitiveType.QUANTITY);
  public static final TypedNull CODING = new TypedNull(PrimitiveType.CODING);
}
