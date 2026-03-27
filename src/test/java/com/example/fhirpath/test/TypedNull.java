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
public record TypedNull(@Nonnull PrimitiveType type) {}
