package com.example.fhirpath.test.assertion;

import static org.junit.jupiter.api.Assertions.assertEquals;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.List;

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
      assertEquals(
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

      assertEquals(adaptedExpected, adaptedActual, "List element at index " + i + " doesn't match");
    }
  }
}
