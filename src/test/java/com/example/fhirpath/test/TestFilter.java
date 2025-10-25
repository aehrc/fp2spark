package com.example.fhirpath.test;

import jakarta.annotation.Nonnull;

/**
 * Filters test descriptions based on substring matching.
 *
 * <p>Supports case-insensitive substring matching against test descriptions.
 * The filter pattern can be read from the system property {@code fhirpath.test.filter}.
 *
 * <p><b>Usage examples:</b>
 * <pre>{@code
 * // Run only tests containing "5 + 10"
 * mvn test -Dfhirpath.test.filter="5 + 10"
 *
 * // Run all tests in "Integer addition" group
 * mvn test -Dfhirpath.test.filter="Integer addition"
 *
 * // Run tests containing "division"
 * mvn test -Dfhirpath.test.filter="division"
 * }</pre>
 *
 * <p>Matching is case-insensitive, so "Addition" will match "Integer addition",
 * "ADDITION", or "addition".
 */
class TestFilter {

    private static final String FILTER_PROPERTY = "fhirpath.test.filter";

    private final String pattern;

    TestFilter(@Nonnull final String pattern) {
        this.pattern = pattern.trim().toLowerCase();
    }

    /**
     * Get filter from system property, or null if not specified.
     *
     * @return TestFilter instance if system property is set, null otherwise
     */
    @Nonnull
    static TestFilter fromSystemProperty() {
        final String filterPattern = System.getProperty(FILTER_PROPERTY);
        if (filterPattern == null || filterPattern.trim().isEmpty()) {
            return new TestFilter("");  // Empty pattern matches everything
        }
        return new TestFilter(filterPattern.trim());
    }

    /**
     * Check if this filter is active (has non-empty pattern).
     *
     * @return true if filter pattern is non-empty
     */
    boolean isActive() {
        return !pattern.isEmpty();
    }

    /**
     * Check if description matches the filter pattern (case-insensitive substring match).
     *
     * @param description The test description to check
     * @return true if description contains the pattern (case-insensitive)
     */
    boolean matches(@Nonnull final String description) {
        if (!isActive()) {
            return true;  // Empty pattern matches everything
        }
        return description.toLowerCase().contains(pattern);
    }

    /**
     * Get the filter pattern for logging/display purposes.
     *
     * @return The filter pattern
     */
    @Nonnull
    String getPattern() {
        return pattern;
    }
}
