package com.example.fhirpath.typing;

import jakarta.annotation.Nonnull;
import java.util.Optional;

/**
 * Shared interface for types that represent FHIR choice elements (polymorphic fields).
 *
 * <p>Both HAPI-backed {@link ChoiceType} and inline {@link InlineChoiceType} implement this
 * interface, allowing the Analyzer to handle choice type operations uniformly.
 */
public interface ChoiceTypeLike extends Type {

  /**
   * Resolves a specific variant by FHIR type name.
   *
   * <p>The column name is formed by concatenating the element name with the capitalized type name
   * (e.g., "value" + "Quantity" → "valueQuantity").
   *
   * @param typeName the FHIR type name (e.g., "Quantity", "string", "boolean")
   * @return the field specification for the variant, or empty if the type is not a valid variant
   */
  @Nonnull
  Optional<FieldSpec> resolveVariant(@Nonnull String typeName);
}
