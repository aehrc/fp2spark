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

/**
 * Marker interface for FHIR resource types.
 *
 * <p>Distinguishes resources from data types, which is needed for {@code ofType()}, {@code
 * resolve()}, and Analyzer root identification.
 *
 * <p>Implementations:
 *
 * <ul>
 *   <li>{@link FhirResourceType} — HAPI FHIR-backed resource with lazy field resolution
 *   <li>{@link InlineResourceType} — explicit field definitions (for tests and inline use)
 * </ul>
 */
public sealed interface ResourceType extends ComplexType
    permits FhirResourceType, InlineResourceType {

  /** Returns the resource name (same as {@link #getName()}). */
  default String getResourceName() {
    return getName();
  }
}
