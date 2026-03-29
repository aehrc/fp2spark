package com.example.fhirpath.test.assertion;

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
    // Adapt expected value to match actual value's type
    // This also converts Scala collections to Java collections
    Object adaptedExpected = TYPE_ADAPTER.adaptToActualType(expected, actual);
    Object adaptedActual = TYPE_ADAPTER.convertScalaToJava(actual);

    // Normalize single-element collections to scalars.
    // In FHIRPath a collection of one IS a singular value, so [x] == x.
    adaptedExpected = unwrapSingleton(adaptedExpected);
    adaptedActual = unwrapSingleton(adaptedActual);

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
   * Assert that two lists are equal element-by-element. Recursively handles nested collections and
   * Scala/Java type conversions.
   *
   * @param expected The expected list
   * @param actual The actual list
   */
  private void assertListEquals(@Nonnull List<?> expected, @Nonnull List<?> actual) {
    assertEquals(expected.size(), actual.size(), "List sizes don't match");
    for (int i = 0; i < expected.size(); i++) {
      Object expectedElement = expected.get(i);
      Object actualElement = actual.get(i);

      // Recursively adapt and compare nested elements
      Object adaptedExpected = TYPE_ADAPTER.adaptToActualType(expectedElement, actualElement);
      Object adaptedActual = TYPE_ADAPTER.convertScalaToJava(actualElement);

      assertValuesEqual(
          adaptedExpected, adaptedActual, "List element at index " + i + " doesn't match");
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
