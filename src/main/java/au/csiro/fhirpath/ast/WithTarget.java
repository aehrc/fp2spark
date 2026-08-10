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
 * Interface for AST nodes that have an optional target expression. When target is null, an implicit
 * target is used (either $this in lambda context or %context otherwise).
 *
 * @param <T> The concrete type implementing this interface (for fluent API)
 */
public interface WithTarget<T extends WithTarget<T>> extends AstNode {

  /** Returns the target expression, or null if using implicit target. */
  @Nullable
  AstNode target();

  /**
   * Creates a copy of this node with the specified target. Used to resolve implicit targets during
   * analysis.
   */
  @Nonnull
  T withTarget(@Nonnull AstNode target);
}
