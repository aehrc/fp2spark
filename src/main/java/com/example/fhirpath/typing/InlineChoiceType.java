package com.example.fhirpath.typing;

import jakarta.annotation.Nonnull;
import java.util.Map;
import java.util.Optional;

/**
 * An inline choice type for test data where variant fields are explicitly enumerated.
 *
 * <p>This mirrors {@link ChoiceType} but without HAPI dependencies — variant resolution is driven
 * by a pre-built map of column names to field specs.
 *
 * <p>Example: for a choice element "value" with variants valueString, valueInteger, valueQuantity,
 * the variants map contains {"valueString" → FieldSpec(...), "valueInteger" → FieldSpec(...), ...}.
 */
public final class InlineChoiceType implements ChoiceTypeLike {

  private final String elementName;
  private final Map<String, FieldSpec> variants;

  /**
   * Constructs an inline choice type.
   *
   * @param elementName the base element name (e.g., "value", "deceased")
   * @param variants map from column name (e.g., "valueString") to field specification
   */
  public InlineChoiceType(
      @Nonnull final String elementName, @Nonnull final Map<String, FieldSpec> variants) {
    this.elementName = elementName;
    this.variants = Map.copyOf(variants);
  }

  @Override
  public String getName() {
    return "Choice(" + elementName + ")";
  }

  @Override
  public boolean isPrimitive() {
    return false;
  }

  @Override
  public boolean isComplex() {
    return false;
  }

  @Override
  @Nonnull
  public Optional<FieldSpec> resolveVariant(@Nonnull final String typeName) {
    final String columnName =
        elementName + typeName.substring(0, 1).toUpperCase() + typeName.substring(1);
    return Optional.ofNullable(variants.get(columnName));
  }

  @Override
  public String toString() {
    return getName();
  }
}
