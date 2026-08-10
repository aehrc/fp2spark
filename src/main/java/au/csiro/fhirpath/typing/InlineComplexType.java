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
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * A complex type with explicitly defined fields stored in a map.
 *
 * <p>Used for tests and explicit type definitions where fields are known at construction time.
 * Supports anonymous types (unnamed) via the default name "ComplexType".
 */
public non-sealed class InlineComplexType implements ComplexType {

  private final String name;
  private final Map<String, FieldSpec> fields;

  /**
   * Constructs a named complex type with the given field specifications.
   *
   * @param name the type name (e.g., "HumanName", "Coding")
   * @param fieldSpecs the list of field specifications
   */
  public InlineComplexType(@Nonnull final String name, @Nonnull final List<FieldSpec> fieldSpecs) {
    this.name = name;
    this.fields =
        fieldSpecs.stream().collect(Collectors.toMap(FieldSpec::getName, Function.identity()));
  }

  /**
   * Constructs a complex type with the given field specifications and a default name.
   *
   * @param fieldSpecs the list of field specifications
   */
  public InlineComplexType(@Nonnull final List<FieldSpec> fieldSpecs) {
    this("ComplexType", fieldSpecs);
  }

  /**
   * Convenience constructor accepting varargs field specifications.
   *
   * @param fieldSpecs the field specifications
   */
  public InlineComplexType(@Nonnull final FieldSpec... fieldSpecs) {
    this(List.of(fieldSpecs));
  }

  /**
   * Constructs a named complex type with no fields.
   *
   * @param name the type name (e.g., "HumanName")
   */
  public InlineComplexType(@Nonnull final String name) {
    this(name, List.of());
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  @Nonnull
  public Optional<FieldSpec> resolveField(@Nonnull final String fieldName) {
    return Optional.ofNullable(fields.get(fieldName));
  }

  /** Returns the set of field names defined on this complex type. */
  @Nonnull
  public Set<String> getFieldNames() {
    return fields.keySet();
  }

  /** Returns all field specifications defined on this complex type. */
  @Nonnull
  public List<FieldSpec> getFields() {
    return List.copyOf(fields.values());
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    return name.equals(((InlineComplexType) o).name);
  }

  @Override
  public int hashCode() {
    return name.hashCode();
  }

  @Override
  public String toString() {
    return name;
  }
}
