package com.example.fhirpath.compat;

import jakarta.annotation.Nonnull;

/**
 * A single exclusion rule that matches against a test's display name and class.
 *
 * @param matcher Predicate that tests whether a given test matches this rule
 * @param reason Human-readable reason why this test is an expected failure
 */
record ExclusionRule(@Nonnull ExclusionMatcher matcher, @Nonnull String reason) {

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
