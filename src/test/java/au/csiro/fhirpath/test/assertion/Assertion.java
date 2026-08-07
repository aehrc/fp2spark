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
package au.csiro.fhirpath.test.assertion;

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
