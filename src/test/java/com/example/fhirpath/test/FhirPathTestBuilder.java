package com.example.fhirpath.test;

import com.example.fhirpath.FhirPath;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.junit.jupiter.api.DynamicTest;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Fluent builder for creating FHIRPath test cases with readable DSL syntax.
 *
 * <p>This builder provides methods for:
 * <ul>
 *   <li>Grouping related tests with {@link #group(String)}</li>
 *   <li>Testing expected values with {@link #testEquals(Object, String, String)}</li>
 *   <li>Testing boolean results with {@link #testTrue(String, String)} and {@link #testFalse(String, String)}</li>
 *   <li>Testing empty collections with {@link #testEmpty(String, String)}</li>
 *   <li>Testing error conditions with {@link #testError(Class, String, String)}</li>
 * </ul>
 *
 * <p>The builder uses {@link FhirPath#toColumn(String)} to compile and execute expressions,
 * which includes full pipeline logging (parsing, analysis, SQL generation).
 */
public class FhirPathTestBuilder {

    private final SparkSession spark;
    private final List<TestCase> testCases = new ArrayList<>();
    private String currentGroup = null;

    public FhirPathTestBuilder(@Nonnull SparkSession spark) {
        this.spark = spark;
    }

    /**
     * Set the current test group. All subsequent test cases will be prefixed with this group name.
     *
     * @param groupName The group name to use for subsequent tests
     * @return This builder for method chaining
     */
    @Nonnull
    public FhirPathTestBuilder group(@Nonnull String groupName) {
        this.currentGroup = groupName;
        return this;
    }

    /**
     * Test that an expression equals an expected value.
     *
     * @param expected    The expected result value
     * @param expression  The FHIRPath expression to evaluate
     * @param description A description of what this test verifies
     * @return This builder for method chaining
     */
    @Nonnull
    public FhirPathTestBuilder testEquals(
            @Nullable Object expected,
            @Nonnull String expression,
            @Nonnull String description
    ) {
        testCases.add(new TestCase(
                fullDescription(description),
                expression,
                new EqualsAssertion(expected)
        ));
        return this;
    }

    /**
     * Test that an expression evaluates to true.
     *
     * @param expression  The FHIRPath expression to evaluate
     * @param description A description of what this test verifies
     * @return This builder for method chaining
     */
    @Nonnull
    public FhirPathTestBuilder testTrue(
            @Nonnull String expression,
            @Nonnull String description
    ) {
        return testEquals(true, expression, description);
    }

    /**
     * Test that an expression evaluates to false.
     *
     * @param expression  The FHIRPath expression to evaluate
     * @param description A description of what this test verifies
     * @return This builder for method chaining
     */
    @Nonnull
    public FhirPathTestBuilder testFalse(
            @Nonnull String expression,
            @Nonnull String description
    ) {
        return testEquals(false, expression, description);
    }

    /**
     * Test that an expression evaluates to an empty collection.
     *
     * @param expression  The FHIRPath expression to evaluate
     * @param description A description of what this test verifies
     * @return This builder for method chaining
     */
    @Nonnull
    public FhirPathTestBuilder testEmpty(
            @Nonnull String expression,
            @Nonnull String description
    ) {
        testCases.add(new TestCase(
                fullDescription(description),
                expression,
                new EmptyAssertion()
        ));
        return this;
    }

    /**
     * Test that an expression throws an exception.
     *
     * @param expectedExceptionType The expected exception class
     * @param expression            The FHIRPath expression to evaluate
     * @param description           A description of what this test verifies
     * @return This builder for method chaining
     */
    @Nonnull
    public FhirPathTestBuilder testError(
            @Nonnull Class<? extends Exception> expectedExceptionType,
            @Nonnull String expression,
            @Nonnull String description
    ) {
        testCases.add(new TestCase(
                fullDescription(description),
                expression,
                new ErrorAssertion(expectedExceptionType)
        ));
        return this;
    }

    /**
     * Build a stream of JUnit 5 dynamic tests from the configured test cases.
     *
     * @return A stream of DynamicTest instances
     */
    @Nonnull
    public Stream<DynamicTest> build() {
        if (testCases.isEmpty()) {
            return Stream.empty();
        }

        return testCases.stream()
                .map(tc -> DynamicTest.dynamicTest(
                        tc.description(),
                        () -> executeTest(tc)
                ));
    }

    /**
     * Execute a single test case by compiling and evaluating the expression.
     */
    private void executeTest(@Nonnull TestCase testCase) {
        try {
            // Use FhirPath API to compile and execute (includes all logging)
            Column column = FhirPath.toColumn(testCase.expression());

            // Execute with Spark (literal expressions don't need input data)
            // Use range(1) to create a single-row dataset for evaluation
            Dataset<Row> result = spark.range(1).toDF().select(column.alias("result"));

            // Extract result value
            Object actualValue = extractResult(result);

            // Perform assertion
            testCase.assertion().assertResult(actualValue);

        } catch (Exception e) {
            // Handle expected errors
            if (testCase.assertion() instanceof ErrorAssertion errorAssertion) {
                errorAssertion.assertError(e);
            } else {
                throw new AssertionError(
                        "Unexpected exception for expression: " + testCase.expression(),
                        e
                );
            }
        }
    }

    /**
     * Extract the result value from a Spark query result.
     * <p>
     * Normalizes BigDecimal values by stripping trailing zeros for consistent comparison.
     *
     * @param result The Spark Dataset containing the query result
     * @return The extracted value (null for empty collections)
     */
    @Nullable
    private Object extractResult(@Nonnull Dataset<Row> result) {
        List<Row> rows = result.collectAsList();
        if (rows.isEmpty() || rows.get(0).isNullAt(0)) {
            return null;
        }

        Object value = rows.get(0).get(0);

        // Handle Spark arrays (convert to Java List and normalize elements)
        if (value instanceof scala.collection.Seq<?> seq) {
            List<?> javaList = scala.jdk.javaapi.CollectionConverters.asJava(seq);
            // Normalize BigDecimal values in list
            return javaList.stream()
                    .map(this::normalizeValue)
                    .toList();
        }

        return normalizeValue(value);
    }

    /**
     * Normalize a value for comparison.
     * Strips trailing zeros from BigDecimal values.
     */
    @Nullable
    private Object normalizeValue(@Nullable Object value) {
        if (value instanceof BigDecimal bd) {
            return bd.stripTrailingZeros();
        }
        return value;
    }

    /**
     * Create full test description by combining group name (if present) with description.
     */
    @Nonnull
    private String fullDescription(@Nonnull String description) {
        return currentGroup != null
                ? currentGroup + " - " + description
                : description;
    }

    // ========== Assertion Types ==========

    /**
     * Base interface for test assertions.
     */
    private interface Assertion {
        void assertResult(@Nullable Object actual);
    }

    /**
     * Assertion that checks equality with an expected value.
     * <p>
     * This assertion adapts the expected value to match the actual value's type when appropriate.
     * This is necessary because:
     * <ul>
     *   <li>FHIRPath DECIMAL maps to Java BigDecimal, but tests use int/double for convenience</li>
     *   <li>Spark collections may contain BigDecimal even when integers are expected</li>
     * </ul>
     * <p>
     * Adaptation rules:
     * <ul>
     *   <li>If actual is BigDecimal, adapt expected int/double to BigDecimal</li>
     *   <li>For lists, assume monomorphic (detect type from first non-null element)</li>
     *   <li>No adaptation for other type mismatches (fail as type error)</li>
     * </ul>
     */
    private record EqualsAssertion(@Nullable Object expected) implements Assertion {
        @Override
        public void assertResult(@Nullable Object actual) {
            // Adapt expected value to match actual value's type
            Object adaptedExpected = adaptToActualType(expected, actual);

            // Compare adapted values
            if (adaptedExpected instanceof List<?> expectedList && actual instanceof List<?> actualList) {
                assertListEquals(expectedList, actualList);
            } else {
                assertEquals(
                        adaptedExpected,
                        actual,
                        "Expression result does not match expected value"
                );
            }
        }

        /**
         * Adapt expected value to match the type of the actual value.
         * <p>
         * This handles the case where tests use convenient Java types (int, double)
         * but Spark returns BigDecimal for FHIRPath DECIMAL type.
         */
        @Nullable
        private Object adaptToActualType(@Nullable Object expected, @Nullable Object actual) {
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

            // Adapt to BigDecimal (FHIRPath DECIMAL type)
            if (targetType == BigDecimal.class) {
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
            }

            // No adaptation possible or needed
            return value;
        }

        /**
         * Detect the element type of a list from its first non-null element.
         * Assumes lists are monomorphic (all elements same type).
         */
        @Nullable
        private Class<?> detectListElementType(@Nonnull List<?> list) {
            return list.stream()
                    .filter(java.util.Objects::nonNull)
                    .findFirst()
                    .map(Object::getClass)
                    .orElse(null);
        }

        /**
         * Assert that two lists are equal element-by-element.
         */
        private void assertListEquals(@Nonnull List<?> expected, @Nonnull List<?> actual) {
            assertEquals(
                    expected.size(),
                    actual.size(),
                    "List sizes don't match"
            );
            for (int i = 0; i < expected.size(); i++) {
                assertEquals(
                        expected.get(i),
                        actual.get(i),
                        "List element at index " + i + " doesn't match"
                );
            }
        }
    }

    /**
     * Assertion that checks for an empty collection (null or empty list).
     */
    private record EmptyAssertion() implements Assertion {
        @Override
        public void assertResult(@Nullable Object actual) {
            assertTrue(
                    actual == null || (actual instanceof List && ((List<?>) actual).isEmpty()),
                    "Expected empty collection but got: " + actual
            );
        }
    }

    /**
     * Assertion that expects an exception to be thrown.
     */
    private record ErrorAssertion(@Nonnull Class<? extends Exception> expectedType) implements Assertion {
        @Override
        public void assertResult(@Nullable Object actual) {
            fail("Expected exception " + expectedType.getName() + " but got result: " + actual);
        }

        public void assertError(@Nonnull Exception e) {
            assertTrue(
                    expectedType.isInstance(e),
                    "Expected " + expectedType.getName() + " but got " + e.getClass().getName()
                            + ": " + e.getMessage()
            );
        }
    }

    /**
     * Internal record holding test case data.
     */
    private record TestCase(
            @Nonnull String description,
            @Nonnull String expression,
            @Nonnull Assertion assertion
    ) {
    }
}
