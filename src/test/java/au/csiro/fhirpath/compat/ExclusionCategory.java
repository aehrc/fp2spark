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
package au.csiro.fhirpath.compat;

import jakarta.annotation.Nonnull;

/**
 * Classifies exclusion groups by root cause.
 *
 * <p>Each category carries a short label for display in test names and logs (e.g. {@code
 * [XFAIL:NOT_IMPL]}).
 */
enum ExclusionCategory {
  EXPECTED_DIFFERENCE("EXPECTED"),
  NOT_IMPLEMENTED("NOT_IMPL"),
  BUG("BUG"),
  SPEC_AMBIGUITY("SPEC_AMBIG");

  private final String label;

  ExclusionCategory(@Nonnull String label) {
    this.label = label;
  }

  /** Short label for display in test names and logs. */
  @Nonnull
  String label() {
    return label;
  }
}
