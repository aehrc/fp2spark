package com.example.fhirpath.compat;

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
