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
package au.csiro.fhirpath.operation.signature;

/**
 * Defines how $this is bound when analyzing lambda parameters.
 *
 * <p>Different FHIRPath functions bind $this at different levels: - Element-wise: $this refers to
 * individual collection elements - Collection-wise: $this refers to the entire collection
 */
public enum LambdaBindingStrategy {
  /**
   * Lambda operates on individual elements. {@code $this = targetType.effectiveType()}.
   *
   * <p>Examples:
   *
   * <ul>
   *   <li>{@code Collection<T>.where(criteria)} - $this is T
   *   <li>{@code Collection<T>.select(projection)} - $this is T
   *   <li>{@code Collection<T>.exists(criteria)} - $this is T (element-wise check)
   * </ul>
   */
  ELEMENT_WISE,

  /**
   * Lambda operates on entire collection. {@code $this = targetType} (the full collection).
   *
   * <p>Examples:
   *
   * <ul>
   *   <li>{@code Collection<T>.iif(criteria, result)} - $this is {@code Collection<T>}
   * </ul>
   */
  COLLECTION_WISE
}
