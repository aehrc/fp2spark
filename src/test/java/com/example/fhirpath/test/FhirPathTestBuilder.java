package com.example.fhirpath.test;

import com.example.fhirpath.test.assertion.Assertion;
import com.example.fhirpath.test.assertion.EmptyAssertion;
import com.example.fhirpath.test.assertion.EqualsAssertion;
import com.example.fhirpath.test.assertion.ErrorAssertion;
import org.apache.spark.sql.SparkSession;
import org.junit.jupiter.api.DynamicTest;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Fluent builder for creating FHIRPath test cases with readable DSL syntax.
 *
 * <p>This builder provides methods for:
 * <ul>
 *   <li>Grouping related tests with {@link #group(String)}</li>
 *   <li>Testing expected values with {@link #testEquals(Object, String)} and overloads</li>
 *   <li>Testing boolean results with {@link #testTrue(String)} and {@link #testFalse(String)}</li>
 *   <li>Testing empty collections with {@link #testEmpty(String)}</li>
 *   <li>Testing error conditions with {@link #testError(Class, String)}</li>
 *   <li>Testing with context using {@link #context(String)} wrapper</li>
 * </ul>
 *
 * <p>The builder uses {@link FhirPath#toColumn(String)} to compile and execute expressions,
 * which includes full pipeline logging (parsing, analysis, SQL generation).
 *
 * <p><b>Context Support:</b>
 * <pre>{@code
 * import static com.example.fhirpath.test.FhirPathTestBuilder.context;
 *
 * builder()
 *     .testEquals(15, "5 + %context", context("10"))
 *     .testEquals(1, "%context.count()", context("'x'"), "Count single value")
 * }</pre>
 *
 * <p><b>Optional Descriptions:</b>
 * <pre>{@code
 * builder()
 *     .testEquals(15, "5 + 10")                      // Expression used as description
 *     .testEquals(15, "5 + 10", "Simple addition")   // Explicit description
 * }</pre>
 */
public class FhirPathTestBuilder {

    private final FhirPathTestExecutor executor;
    private final List<TestCase> testCases = new ArrayList<>();
    private String currentGroup = null;

    /**
     * Create a new test builder with the provided executor.
     *
     * @param executor The test executor for running individual test cases
     */
    public FhirPathTestBuilder(@Nonnull FhirPathTestExecutor executor) {
        this.executor = executor;
    }

    /**
     * Create a context wrapper for FHIRPath context expressions.
     * <p>
     * This static factory method allows for clean import and usage:
     * <pre>{@code
     * import static com.example.fhirpath.test.FhirPathTestBuilder.context;
     *
     * testEquals(15, "5 + %context", context("10"))
     * }</pre>
     *
     * @param expression The FHIRPath expression to use as %context
     * @return A Context wrapper
     */
    @Nonnull
    public static Context context(@Nonnull String expression) {
        return new Context(expression);
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
     * Test that an expression equals an expected value (no context, expression used as description).
     *
     * @param expected   The expected result value
     * @param expression The FHIRPath expression to evaluate
     * @return This builder for method chaining
     */
    @Nonnull
    public FhirPathTestBuilder testEquals(
            @Nullable Object expected,
            @Nonnull String expression
    ) {
        return testEquals(expected, expression, (Context) null, expression);
    }

    /**
     * Test that an expression equals an expected value (no context, with description).
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
        return testEquals(expected, expression, (Context) null, description);
    }

    /**
     * Test that an expression with context equals an expected value (context, expression as description).
     *
     * @param expected   The expected result value
     * @param expression The FHIRPath expression to evaluate
     * @param context    The context expression wrapper
     * @return This builder for method chaining
     */
    @Nonnull
    public FhirPathTestBuilder testEquals(
            @Nullable Object expected,
            @Nonnull String expression,
            @Nonnull Context context
    ) {
        return testEquals(expected, expression, context, expression);
    }

    /**
     * Test that an expression with context equals an expected value (context + description).
     *
     * @param expected    The expected result value
     * @param expression  The FHIRPath expression to evaluate
     * @param context     The context expression wrapper
     * @param description A description of what this test verifies
     * @return This builder for method chaining
     */
    @Nonnull
    public FhirPathTestBuilder testEquals(
            @Nullable Object expected,
            @Nonnull String expression,
            @Nullable Context context,
            @Nonnull String description
    ) {
        testCases.add(new TestCase(
                fullDescription(description),
                expression,
                context,
                new EqualsAssertion(expected)
        ));
        return this;
    }

    /**
     * Test that an expression evaluates to true (no context, expression as description).
     *
     * @param expression The FHIRPath expression to evaluate
     * @return This builder for method chaining
     */
    @Nonnull
    public FhirPathTestBuilder testTrue(@Nonnull String expression) {
        return testEquals(true, expression);
    }

    /**
     * Test that an expression evaluates to true (no context, with description).
     *
     * @param expression  The FHIRPath expression to evaluate
     * @param description A description of what this test verifies
     * @return This builder for method chaining
     */
    @Nonnull
    public FhirPathTestBuilder testTrue(@Nonnull String expression, @Nonnull String description) {
        return testEquals(true, expression, description);
    }

    /**
     * Test that an expression with context evaluates to true (context, expression as description).
     *
     * @param expression The FHIRPath expression to evaluate
     * @param context    The context expression wrapper
     * @return This builder for method chaining
     */
    @Nonnull
    public FhirPathTestBuilder testTrue(@Nonnull String expression, @Nonnull Context context) {
        return testEquals(true, expression, context);
    }

    /**
     * Test that an expression with context evaluates to true (context + description).
     *
     * @param expression  The FHIRPath expression to evaluate
     * @param context     The context expression wrapper
     * @param description A description of what this test verifies
     * @return This builder for method chaining
     */
    @Nonnull
    public FhirPathTestBuilder testTrue(@Nonnull String expression, @Nonnull Context context, @Nonnull String description) {
        return testEquals(true, expression, context, description);
    }

    /**
     * Test that an expression evaluates to false (no context, expression as description).
     *
     * @param expression The FHIRPath expression to evaluate
     * @return This builder for method chaining
     */
    @Nonnull
    public FhirPathTestBuilder testFalse(@Nonnull String expression) {
        return testEquals(false, expression);
    }

    /**
     * Test that an expression evaluates to false (no context, with description).
     *
     * @param expression  The FHIRPath expression to evaluate
     * @param description A description of what this test verifies
     * @return This builder for method chaining
     */
    @Nonnull
    public FhirPathTestBuilder testFalse(@Nonnull String expression, @Nonnull String description) {
        return testEquals(false, expression, description);
    }

    /**
     * Test that an expression with context evaluates to false (context, expression as description).
     *
     * @param expression The FHIRPath expression to evaluate
     * @param context    The context expression wrapper
     * @return This builder for method chaining
     */
    @Nonnull
    public FhirPathTestBuilder testFalse(@Nonnull String expression, @Nonnull Context context) {
        return testEquals(false, expression, context);
    }

    /**
     * Test that an expression with context evaluates to false (context + description).
     *
     * @param expression  The FHIRPath expression to evaluate
     * @param context     The context expression wrapper
     * @param description A description of what this test verifies
     * @return This builder for method chaining
     */
    @Nonnull
    public FhirPathTestBuilder testFalse(@Nonnull String expression, @Nonnull Context context, @Nonnull String description) {
        return testEquals(false, expression, context, description);
    }

    /**
     * Test that an expression evaluates to an empty collection (no context, expression as description).
     *
     * @param expression The FHIRPath expression to evaluate
     * @return This builder for method chaining
     */
    @Nonnull
    public FhirPathTestBuilder testEmpty(@Nonnull String expression) {
        return testEmpty(expression, (Context) null, expression);
    }

    /**
     * Test that an expression evaluates to an empty collection (no context, with description).
     *
     * @param expression  The FHIRPath expression to evaluate
     * @param description A description of what this test verifies
     * @return This builder for method chaining
     */
    @Nonnull
    public FhirPathTestBuilder testEmpty(@Nonnull String expression, @Nonnull String description) {
        return testEmpty(expression, (Context) null, description);
    }

    /**
     * Test that an expression with context evaluates to an empty collection (context, expression as description).
     *
     * @param expression The FHIRPath expression to evaluate
     * @param context    The context expression wrapper
     * @return This builder for method chaining
     */
    @Nonnull
    public FhirPathTestBuilder testEmpty(@Nonnull String expression, @Nonnull Context context) {
        return testEmpty(expression, context, expression);
    }

    /**
     * Test that an expression with context evaluates to an empty collection (context + description).
     *
     * @param expression  The FHIRPath expression to evaluate
     * @param context     The context expression wrapper
     * @param description A description of what this test verifies
     * @return This builder for method chaining
     */
    @Nonnull
    public FhirPathTestBuilder testEmpty(@Nonnull String expression, @Nullable Context context, @Nonnull String description) {
        testCases.add(new TestCase(
                fullDescription(description),
                expression,
                context,
                new EmptyAssertion()
        ));
        return this;
    }

    /**
     * Test that an expression throws an exception (no context, expression as description).
     *
     * @param expectedExceptionType The expected exception class
     * @param expression            The FHIRPath expression to evaluate
     * @return This builder for method chaining
     */
    @Nonnull
    public FhirPathTestBuilder testError(
            @Nonnull Class<? extends Exception> expectedExceptionType,
            @Nonnull String expression
    ) {
        return testError(expectedExceptionType, expression, (Context) null, expression);
    }

    /**
     * Test that an expression throws an exception (no context, with description).
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
        return testError(expectedExceptionType, expression, (Context) null, description);
    }

    /**
     * Test that an expression with context throws an exception (context, expression as description).
     *
     * @param expectedExceptionType The expected exception class
     * @param expression            The FHIRPath expression to evaluate
     * @param context               The context expression wrapper
     * @return This builder for method chaining
     */
    @Nonnull
    public FhirPathTestBuilder testError(
            @Nonnull Class<? extends Exception> expectedExceptionType,
            @Nonnull String expression,
            @Nonnull Context context
    ) {
        return testError(expectedExceptionType, expression, context, expression);
    }

    /**
     * Test that an expression with context throws an exception (context + description).
     *
     * @param expectedExceptionType The expected exception class
     * @param expression            The FHIRPath expression to evaluate
     * @param context               The context expression wrapper
     * @param description           A description of what this test verifies
     * @return This builder for method chaining
     */
    @Nonnull
    public FhirPathTestBuilder testError(
            @Nonnull Class<? extends Exception> expectedExceptionType,
            @Nonnull String expression,
            @Nullable Context context,
            @Nonnull String description
    ) {
        testCases.add(new TestCase(
                fullDescription(description),
                expression,
                context,
                new ErrorAssertion(expectedExceptionType)
        ));
        return this;
    }

    /**
     * Build a stream of JUnit 5 dynamic tests from the configured test cases.
     * <p>
     * This method creates DynamicTest instances that delegate execution to the executor.
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
                        () -> executor.executeTest(tc)
                ));
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

}
