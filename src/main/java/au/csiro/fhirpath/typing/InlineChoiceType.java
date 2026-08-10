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
