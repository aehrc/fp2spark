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
 * Represents a complex FHIRPath type with named fields (e.g., a FHIR resource or data type).
 *
 * <p>This is a sealed interface with two implementation strategies:
 *
 * <ul>
 *   <li>{@link FhirComplexType} — resolves fields lazily from HAPI FHIR runtime definitions
 *   <li>{@link InlineComplexType} — stores fields explicitly in a map (for tests and inline
 *       definitions)
 * </ul>
 *
 * <p>Resource types extend this interface via {@link ResourceType} marker interface with
 * corresponding implementations {@link FhirResourceType} and {@link InlineResourceType}.
 */
public sealed interface ComplexType extends Type
    permits FhirComplexType, InlineComplexType, ResourceType {

  @Override
  default boolean isPrimitive() {
    return false;
  }

  @Override
  default boolean isComplex() {
    return true;
  }
}
