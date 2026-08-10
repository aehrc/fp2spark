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

/**
 * Represents a lambda type in the FHIRPath type system. Lambdas are expressions that are evaluated
 * with an implicit $this binding.
 *
 * <p>In FHIRPath, lambda parameter types are always implicit - determined by the collection element
 * type. There is no syntax to declare parameter types. The lambda is checked for compatibility
 * during type resolution.
 *
 * <p>Used by collection operations like where(), select(), repeat() that accept criteria or
 * projection expressions.
 *
 * <p>Examples:
 *
 * <ul>
 *   <li>{@code where(criteria)} expects Lambda(Boolean)
 *   <li>{@code select(projection)} expects Lambda(R)
 * </ul>
 *
 * @param returnShape The shape of the lambda body evaluation result
 */
public record LambdaType(@Nonnull Shape returnShape) implements Type {

  @Override
  @Nonnull
  public String getName() {
    return "Lambda(" + returnShape + ")";
  }

  @Override
  public boolean isPrimitive() {
    return false; // Lambdas are not primitives
  }

  @Override
  public boolean isComplex() {
    return false; // Lambdas are not complex types
  }

  @Override
  public String toString() {
    return "Lambda(" + returnShape + ")";
  }
}
