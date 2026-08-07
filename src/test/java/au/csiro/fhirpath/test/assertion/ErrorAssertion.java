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
