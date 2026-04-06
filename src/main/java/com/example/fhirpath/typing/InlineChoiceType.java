package com.example.fhirpath.typing;

import jakarta.annotation.Nonnull;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
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
    this.variants = Collections.unmodifiableMap(new LinkedHashMap<>(variants));
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

  /**
   * Direct field traversal is disallowed on choice types. Users must narrow via {@code ofType()},
   * {@code is}, or {@code as} first.
   */
  @Override
  public Optional<FieldSpec> resolveField(@Nonnull final String fieldName) {
    return Optional.empty();
  }

  @Override
  @Nonnull
  public List<FieldSpec> getVariants() {
    return List.copyOf(variants.values());
  }

  @Override
  @Nonnull
  public Optional<FieldSpec> resolveVariant(@Nonnull final String typeName) {
    final String columnName = ChoiceTypeLike.variantColumnName(elementName, typeName);
    return Optional.ofNullable(variants.get(columnName));
  }

  @Override
  public String toString() {
    return getName();
  }
}
