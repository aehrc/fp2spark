package com.example.fhirpath.test.assertion;

import static org.junit.jupiter.api.Assertions.fail;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * Interface for FHIRPath test assertions.
 *
 * <p>Implementations verify that actual test results match expected outcomes. Different assertion
 * types handle different verification strategies:
 *
 * <ul>
 *   <li>{@link EqualsAssertion} - Verifies value equality with type adaptation
 *   <li>{@link EmptyAssertion} - Verifies empty collection results
 *   <li>{@link ErrorAssertion} - Verifies exception throwing
 * </ul>
 */
public interface Assertion {
  /**
   * Assert that the actual result matches the expected outcome. Called when test execution
   * completes successfully.
   *
   * @param actual The actual result from test execution
   * @throws AssertionError if the assertion fails
   */
  void assertResult(@Nullable Object actual);

  /**
   * Assert that an exception matches the expected error condition. Called when test execution
   * throws an exception.
   *
   * <p>Default implementation fails (most assertions expect successful execution). Override in
   * {@link ErrorAssertion} to validate exception types.
   *
   * @param exception The exception that was thrown
   * @throws AssertionError if the exception doesn't match expectations
   */
  default void assertError(@Nonnull Exception exception) {
    fail(
        "Unexpected exception: " + exception.getClass().getName() + ": " + exception.getMessage(),
        exception);
  }
}
