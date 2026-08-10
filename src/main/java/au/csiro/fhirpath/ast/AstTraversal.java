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

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * Represents a field traversal in a FHIRPath expression (e.g., {@code name} in {@code
 * Patient.name}).
 *
 * @param path the field path name to traverse
 * @param target the target expression, or null for implicit target (resolved during analysis)
 */
public record AstTraversal(String path, @Nullable AstNode target)
    implements WithTarget<AstTraversal> {
  /**
   * Constructor for traversals without a target (standalone field access).
   *
   * @param path the field path name
   */
  public AstTraversal(final String path) {
    this(path, null);
  }

  /**
   * Create a new AstTraversal with a different target.
   *
   * @param newTarget the new target node
   * @return a new AstTraversal instance with the updated target
   */
  @Nonnull
  @Override
  public AstTraversal withTarget(@Nonnull final AstNode newTarget) {
    return new AstTraversal(this.path, newTarget);
  }
}
