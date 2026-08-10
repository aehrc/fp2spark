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

import java.util.Optional;

/**
 * Represents a FHIRPath element type.
 *
 * <p>In the element-first type model, Type represents the kind of element, while {@link
 * Cardinality} represents how many elements (0..1 or 0..*).
 *
 * <p>Types are combined with cardinality to form {@link Shape}s.
 */
public interface Type {
  /** Returns the name of this type. */
  String getName();

  /** Returns whether this type is a primitive type. */
  boolean isPrimitive();

  /** Returns whether this type is a complex type. */
  boolean isComplex();

  /**
   * Resolves a field specification for the given field name.
   *
   * <p>Complex types override this to provide field resolution. Primitive types, {@link
   * FhirPrimitiveType}, and {@link LambdaType} return empty by default.
   *
   * @param fieldName the name of the field to resolve
   * @return the field specification, or empty if the field cannot be resolved
   */
  default Optional<FieldSpec> resolveField(final String fieldName) {
    return Optional.empty();
  }
}
