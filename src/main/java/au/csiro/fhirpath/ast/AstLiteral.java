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
package au.csiro.fhirpath.ast;

import jakarta.annotation.Nullable;

/**
 * Represents a literal value in a FHIRPath expression (e.g., {@code 'hello'}, {@code 42}).
 *
 * @param value the literal value, or null for the empty collection literal ({@code {}})
 */
public record AstLiteral(@Nullable Object value) implements AstNode {

  /**
   * Singleton instance representing null/empty collection literal. Used for padding optional
   * parameters in variadic functions.
   */
  public static final AstLiteral NULL = new AstLiteral(null);
}
