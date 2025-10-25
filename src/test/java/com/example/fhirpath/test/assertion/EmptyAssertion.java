package com.example.fhirpath.test.assertion;

import jakarta.annotation.Nullable;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Assertion that verifies a result is an empty collection.
 *
 * <p>A result is considered empty if it is:
 * <ul>
 *   <li>null (FHIRPath empty collection {})</li>
 *   <li>An empty Java List</li>
 * </ul>
 */
public record EmptyAssertion() implements Assertion {
    @Override
    public void assertResult(@Nullable Object actual) {
        assertTrue(
                actual == null || (actual instanceof List && ((List<?>) actual).isEmpty()),
                "Expected empty collection but got: " + actual
        );
    }
}
