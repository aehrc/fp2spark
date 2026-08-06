package au.csiro.fhirpath.test;

import jakarta.annotation.Nonnull;

/**
 * Wrapper for FHIRPath context expressions in tests.
 *
 * <p>This wrapper class provides type safety to distinguish context parameters from description
 * strings in test method calls.
 *
 * <p>Usage:
 *
 * <pre>{@code
 * import static au.csiro.fhirpath.test.FhirPathTestBuilder.context;
 *
 * builder()
 *     .testEquals(15, "5 + %context", context("10"))
 *     .testEquals(1, "%context.count()", context("'x'"), "Count single value")
 * }</pre>
 */
public record Context(@Nonnull String expression) {}
