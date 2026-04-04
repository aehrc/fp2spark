package com.example.fhirpath.test;

import com.example.fhirpath.typing.SystemType;
import jakarta.annotation.Nonnull;

/**
 * Marker for a null value with known type, used in Map-based test data.
 *
 * <p>When a field is empty but has a known type (e.g., {@code stringEmpty("name")}), this marker
 * preserves the type information so that {@link ResourceTypeInference} can infer the correct field
 * type instead of falling back to {@link SystemType#NULL}.
 *
 * @param type the known primitive type of the empty field
 */
public record TypedNull(@Nonnull SystemType type) {

  public static final TypedNull STRING = new TypedNull(SystemType.STRING);
  public static final TypedNull INTEGER = new TypedNull(SystemType.INTEGER);
  public static final TypedNull DECIMAL = new TypedNull(SystemType.DECIMAL);
  public static final TypedNull BOOLEAN = new TypedNull(SystemType.BOOLEAN);
  public static final TypedNull DATE = new TypedNull(SystemType.DATE);
  public static final TypedNull DATE_TIME = new TypedNull(SystemType.DATE_TIME);
  public static final TypedNull TIME = new TypedNull(SystemType.TIME);
  public static final TypedNull QUANTITY = new TypedNull(SystemType.QUANTITY);
  public static final TypedNull CODING = new TypedNull(SystemType.CODING);
}
