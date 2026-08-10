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
 * A single exclusion rule that matches against a test's display name and class.
 *
 * @param matcher Predicate that tests whether a given test matches this rule
 * @param reason Human-readable reason why this test is an expected failure
 */
record ExclusionRule(
    @Nonnull ExclusionMatcher matcher,
    @Nonnull String reason,
    @Nonnull ExclusionCategory category) {

  /** Returns true if this rule matches the given test. */
  boolean matches(@Nonnull String displayName, @Nonnull Class<?> testClass) {
    return matcher.matches(displayName, testClass);
  }

  /** Functional interface for matching a test by display name and test class. */
  @FunctionalInterface
  interface ExclusionMatcher {
    boolean matches(@Nonnull String displayName, @Nonnull Class<?> testClass);
  }
}
