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

/**
 * A resource type with explicitly defined fields stored in a map.
 *
 * <p>Extends {@link InlineComplexType} with the {@link ResourceType} marker. Used for tests and
 * explicit resource type definitions.
 */
public final class InlineResourceType extends InlineComplexType implements ResourceType {

  /** Sentinel value representing an empty/unspecified resource type. */
  public static final InlineResourceType EMPTY = new InlineResourceType("<empty>", List.of());

  /**
   * Constructs a resource type with the given name and field specifications.
   *
   * @param name the resource type name (e.g., "Patient")
   * @param fieldSpecs the list of field specifications
   */
  public InlineResourceType(@Nonnull final String name, @Nonnull final List<FieldSpec> fieldSpecs) {
    super(name, fieldSpecs);
  }

  /**
   * Convenience constructor for varargs field specifications.
   *
   * @param name the resource type name
   * @param fieldSpecs the field specifications
   */
  public InlineResourceType(@Nonnull final String name, @Nonnull final FieldSpec... fieldSpecs) {
    this(name, List.of(fieldSpecs));
  }
}
