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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Assertion that checks equality with an expected value.
 *
 * <p>This assertion uses {@link TypeAdapter} to adapt expected values to match actual value types
 * returned by Spark. This allows tests to use convenient Java types (int, double) while comparing
 * against BigDecimal results.
 *
 * @see TypeAdapter for adaptation rules
 */
public record EqualsAssertion(@Nullable Object expected) implements Assertion {

  private static final TypeAdapter TYPE_ADAPTER = new TypeAdapter();

  @Override
  public void assertResult(@Nullable Object actual) {
    // Convert Scala collections and Spark Rows to Java equivalents first
    Object convertedActual = TYPE_ADAPTER.convertScalaToJava(actual);

    // Normalize single-element collections to scalars.
    // In FHIRPath a collection of one IS a singular value, so [x] == x.
    Object adaptedExpected = unwrapSingleton(expected);
    Object adaptedActual = unwrapSingleton(convertedActual);

    // Adapt expected value to match actual value's type (e.g., CodingValue → Map)
    adaptedExpected = TYPE_ADAPTER.adaptToActualType(adaptedExpected, adaptedActual);

    // Compare adapted values
    if (adaptedExpected instanceof List<?> expectedList
        && adaptedActual instanceof List<?> actualList) {
      assertListEquals(expectedList, actualList);
    } else {
      assertValuesEqual(
          adaptedExpected, adaptedActual, "Expression result does not match expected value");
    }
  }

  /**
   * Unwraps a single-element list to its scalar value. In FHIRPath, a collection containing exactly
   * one element is equivalent to a singular value.
   */
  @Nullable
  private static Object unwrapSingleton(@Nullable final Object value) {
    if (value instanceof List<?> list && list.size() == 1) {
      return list.getFirst();
    }
    return value;
  }

  /**
   * Assert that two lists are equal element-by-element. Both lists must already be converted to
   * Java types via {@link TypeAdapter#convertScalaToJava}.
   *
   * @param expected The expected list
   * @param actual The actual list (already converted)
   */
  private void assertListEquals(@Nonnull List<?> expected, @Nonnull List<?> actual) {
    assertEquals(expected.size(), actual.size(), "List sizes don't match");
    for (int i = 0; i < expected.size(); i++) {
      final Object actualElement = actual.get(i);
      final Object adaptedExpected = TYPE_ADAPTER.adaptToActualType(expected.get(i), actualElement);

      assertValuesEqual(
          adaptedExpected, actualElement, "List element at index " + i + " doesn't match");
    }
  }

  /**
   * Asserts that two values are equal, using {@link BigDecimal#compareTo} for numeric comparison to
   * avoid scale-sensitive {@link BigDecimal#equals} failures (e.g., {@code 120} vs {@code 1.2E+2}).
   */
  @SuppressWarnings("unchecked")
  private static void assertValuesEqual(
      @Nullable final Object expected,
      @Nullable final Object actual,
      @Nonnull final String message) {
    if (expected instanceof BigDecimal expectedBd && actual instanceof BigDecimal actualBd) {
      assertTrue(
          expectedBd.compareTo(actualBd) == 0,
          () ->
              message
                  + " expected: "
                  + expectedBd.toPlainString()
                  + ", was: "
                  + actualBd.toPlainString());
    } else if (expected instanceof Map<?, ?> expectedMap && actual instanceof Map<?, ?> actualMap) {
      assertMapEquals((Map<String, Object>) expectedMap, (Map<String, Object>) actualMap, message);
    } else {
      assertEquals(expected, actual, message);
    }
  }

  /**
   * Asserts that two maps are equal, delegating value comparison to {@link #assertValuesEqual} so
   * that BigDecimal values are compared numerically.
   */
  private static void assertMapEquals(
      @Nonnull final Map<String, Object> expected,
      @Nonnull final Map<String, Object> actual,
      @Nonnull final String message) {
    assertEquals(expected.keySet(), actual.keySet(), message + " (map keys differ)");
    for (final String key : expected.keySet()) {
      assertValuesEqual(expected.get(key), actual.get(key), message + " [key=" + key + "]");
    }
  }
}
