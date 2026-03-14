package com.example.fhirpath.test;

import com.example.fhirpath.test.assertion.Assertion;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * A single FHIRPath test case containing an expression and expected outcome.
 *
 * @param description Human-readable description of what this test verifies
 * @param expression The FHIRPath expression to evaluate
 * @param context Optional FHIRPath expression to use as %context (null if not used)
 * @param subject Optional test subject providing resource data and type (null for
 *     literal/context-only tests)
 * @param assertion The assertion that verifies the result
 */
record TestCase(
    @Nonnull String description,
    @Nonnull String expression,
    @Nullable Context context,
    @Nullable TestSubject subject,
    @Nonnull Assertion assertion) {}
