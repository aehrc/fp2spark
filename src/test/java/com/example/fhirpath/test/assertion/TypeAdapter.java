package com.example.fhirpath.test.assertion;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.math.BigDecimal;
import java.util.List;

/**
 * Adapts expected test values to match actual value types returned by Spark.
 *
 * <p>This class handles type mismatches between convenient test value types
 * (int, double) and actual Spark return types (BigDecimal for FHIRPath DECIMAL).
 *
 * <p><b>Adaptation Rules:</b>
 * <ul>
 *   <li>If actual is BigDecimal, adapt expected int/long/double/float to BigDecimal</li>
 *   <li>For lists, assume monomorphic (detect element type from first non-null element)</li>
 *   <li>No adaptation for incompatible type pairs (returns expected unchanged)</li>
 * </ul>
 *
 * <p><b>Rationale:</b>
 * FHIRPath DECIMAL type maps to Java BigDecimal, but writing {@code testEquals(new BigDecimal("5"), ...)}
 * in every test is verbose. This adapter allows {@code testEquals(5, ...)} while comparing against
 * BigDecimal results correctly.
 */
class TypeAdapter {

    /**
     * Adapt expected value to match the type of actual value.
     * <p>
     * This method recursively adapts collections and their elements.
     *
     * @param expected The expected value from the test
     * @param actual   The actual value returned by Spark
     * @return The adapted expected value, or original if no adaptation needed
     */
    @Nullable
    Object adaptToActualType(@Nullable Object expected, @Nullable Object actual) {
        if (expected == null || actual == null) {
            return expected;
        }

        // Handle lists: adapt each element to match actual list's element type
        if (expected instanceof List<?> expectedList && actual instanceof List<?> actualList) {
            // Detect actual list element type from first non-null element
            Class<?> actualElementType = detectListElementType(actualList);
            if (actualElementType != null) {
                return expectedList.stream()
                        .map(e -> adaptValue(e, actualElementType))
                        .toList();
            }
            return expectedList;
        }

        // Handle scalar values: adapt to actual type
        return adaptValue(expected, actual.getClass());
    }

    /**
     * Adapt a single value to the target type.
     *
     * @param value      The value to adapt
     * @param targetType The target type to adapt to
     * @return The adapted value, or original if no adaptation possible
     */
    @Nullable
    private Object adaptValue(@Nullable Object value, @Nonnull Class<?> targetType) {
        if (value == null) {
            return null;
        }

        // If already correct type, return as-is
        if (targetType.isInstance(value)) {
            // For BigDecimal, normalize by stripping trailing zeros
            if (value instanceof BigDecimal bd) {
                return bd.stripTrailingZeros();
            }
            return value;
        }

        // Adapt numeric types to BigDecimal (FHIRPath DECIMAL type)
        if (targetType == BigDecimal.class) {
            return adaptToBigDecimal(value);
        }

        // No adaptation possible or needed
        return value;
    }

    /**
     * Adapt a numeric value to BigDecimal.
     *
     * @param value The numeric value to adapt
     * @return BigDecimal representation, or original value if not numeric
     */
    @Nullable
    private Object adaptToBigDecimal(@Nonnull Object value) {
        if (value instanceof Integer i) {
            return BigDecimal.valueOf(i);
        }
        if (value instanceof Long l) {
            return BigDecimal.valueOf(l);
        }
        if (value instanceof Double d) {
            return BigDecimal.valueOf(d).stripTrailingZeros();
        }
        if (value instanceof Float f) {
            return BigDecimal.valueOf(f).stripTrailingZeros();
        }
        // Not a numeric type we can adapt
        return value;
    }

    /**
     * Detect the element type of a list from its first non-null element.
     * <p>
     * Assumes lists are monomorphic (all elements have the same type).
     *
     * @param list The list to inspect
     * @return The class of the first non-null element, or null if list is empty or all nulls
     */
    @Nullable
    private Class<?> detectListElementType(@Nonnull List<?> list) {
        return list.stream()
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .map(Object::getClass)
                .orElse(null);
    }
}
