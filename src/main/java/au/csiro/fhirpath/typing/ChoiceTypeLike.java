/*
 * Copyright © 2025-2026 Commonwealth Scientific and Industrial Research
 * Organisation (CSIRO) ABN 41 687 119 230.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package au.csiro.fhirpath.typing;

import jakarta.annotation.Nonnull;
import java.util.List;
import java.util.Optional;

/**
 * Shared interface for types that represent FHIR choice elements (polymorphic fields).
 *
 * <p>Both HAPI-backed {@link ChoiceType} and inline {@link InlineChoiceType} implement this
 * interface, allowing the Analyzer to handle choice type operations uniformly.
 */
public interface ChoiceTypeLike extends Type {

  /**
   * Returns all variant field specifications for this choice type.
   *
   * <p>Each field spec represents a possible variant (e.g., "valueString", "valueQuantity") with
   * its column name and type.
   *
   * @return the list of all variant field specifications
   */
  @Nonnull
  List<FieldSpec> getVariants();

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

  /**
   * Builds the variant column name from the base element name and FHIR type name.
   *
   * <p>Example: {@code variantColumnName("value", "Quantity")} → {@code "valueQuantity"}.
   *
   * @param elementName the base element name (e.g., "value")
   * @param typeName the FHIR type name (e.g., "Quantity", "string")
   * @return the column name (e.g., "valueQuantity", "valueString")
   */
  @Nonnull
  static String variantColumnName(
      @Nonnull final String elementName, @Nonnull final String typeName) {
    return elementName + typeName.substring(0, 1).toUpperCase() + typeName.substring(1);
  }
}
