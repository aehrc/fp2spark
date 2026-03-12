package com.example.fhirpath.test.assertion;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * Assertion that expects an exception to be thrown during test execution.
 *
 * <p>If the test completes successfully, the assertion fails. If an exception is thrown, this
 * assertion verifies the exception type matches expectations.
 */
public record ErrorAssertion(@Nonnull Class<? extends Exception> expectedType)
    implements Assertion {

  @Override
  public void assertResult(@Nullable Object actual) {
    fail("Expected exception " + expectedType.getName() + " but got result: " + actual);
  }

  @Override
  public void assertError(@Nonnull Exception exception) {
    assertTrue(
        expectedType.isInstance(exception),
        "Expected "
            + expectedType.getName()
            + " but got "
            + exception.getClass().getName()
            + ": "
            + exception.getMessage());
  }
}
