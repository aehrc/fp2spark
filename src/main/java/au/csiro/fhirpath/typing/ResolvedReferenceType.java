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
 * Represents the result type of the {@code resolve()} function.
 *
 * <p>This type represents extracted reference type information (a type name string). It supports
 * dynamic type checking via {@code is}, {@code as}, and {@code ofType} operators at runtime, but
 * does not support field traversal — attempting to access child fields throws an error.
 *
 * <p>The runtime value is a STRING containing the FHIR resource type name (e.g., "Patient",
 * "Practitioner"). Type operations compare this string against the requested type specifier.
 */
public final class ResolvedReferenceType implements Type {

  /** Singleton instance. */
  public static final ResolvedReferenceType INSTANCE = new ResolvedReferenceType();

  private ResolvedReferenceType() {}

  @Override
  public String getName() {
    return "ResolvedReference";
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
   * Field traversal is not supported on resolved references.
   *
   * @throws UnsupportedOperationException always — resolve() returns type information only
   */
  @Override
  public Optional<FieldSpec> resolveField(final String fieldName) {
    throw new UnsupportedOperationException(
        "Field traversal after resolve() is not supported."
            + " resolve() returns type information only;"
            + " use is/as/ofType for type checking.");
  }

  @Override
  public String toString() {
    return "ResolvedReference";
  }
}
