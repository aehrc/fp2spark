package com.example.fhirpath.test;

import com.example.fhirpath.test.assertion.EmptyAssertion;
import com.example.fhirpath.test.assertion.EqualsAssertion;
import com.example.fhirpath.test.assertion.ErrorAssertion;
import com.example.fhirpath.typing.ResourceType;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.junit.jupiter.api.DynamicTest;

/**
 * Fluent builder for creating FHIRPath test cases with readable DSL syntax.
 *
 * <p>This builder provides methods for:
 *
 * <ul>
 *   <li>Grouping related tests with {@link #group(String)}
 *   <li>Testing expected values with {@link #testEquals(Object, String)} and overloads
 *   <li>Testing boolean results with {@link #testTrue(String)} and {@link #testFalse(String)}
 *   <li>Testing empty collections with {@link #testEmpty(String)}
 *   <li>Testing error conditions with {@link #testError(Class, String)}
 *   <li>Testing with context using {@link #context(String)} wrapper
 * </ul>
 *
 * <p>The builder uses {@link FhirPath#toColumn(String)} to compile and execute expressions, which
 * includes full pipeline logging (parsing, analysis, SQL generation).
 *
 * <p><b>Context Support:</b>
 *
 * <pre>{@code
 * import static com.example.fhirpath.test.FhirPathTestBuilder.context;
 *
 * builder()
 *     .testEquals(15, "5 + %context", context("10"))
 *     .testEquals(1, "%context.count()", context("'x'"), "Count single value")
 * }</pre>
 *
 * <p><b>Optional Descriptions:</b>
 *
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
  private TestSubject currentSubject = null;

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
   *
   * <p>This static factory method allows for clean import and usage:
   *
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
   * Set the current resource test data. All subsequent test cases will use this resource.
   *
   * <p>This method accepts a Consumer that builds the resource data using {@link
   * ResourceDataBuilder}.
   *
   * <p><b>Example:</b>
   *
   * <pre>{@code
   * builder()
   *     .withSubject("Patient", sb -> sb
   *         .string("id", "patient-1")
   *         .integer("age", 30)
   *         .element("name", n -> n.string("family", "Smith"))
   *     )
   *     .testEquals("Smith", "name.family")
   *     .testEquals(30, "age")
   * }</pre>
   *
   * @param resourceTypeName The name of the resource type (e.g., "Patient")
   * @param builderConsumer Consumer that builds the resource data
   * @return This builder for method chaining
   */
  @Nonnull
  public FhirPathTestBuilder withSubject(
      @Nonnull final String resourceTypeName,
      @Nonnull final java.util.function.Consumer<ResourceDataBuilder> builderConsumer) {
    final ResourceDataBuilder dataBuilder = new ResourceDataBuilder();
    builderConsumer.accept(dataBuilder);
    this.currentSubject =
        new MapTestSubject(ResourceTestData.of(resourceTypeName, dataBuilder.build()));
    return this;
  }

  /**
   * Set the current resource test data from a pre-built Map.
   *
   * @param resourceTypeName The name of the resource type (e.g., "Patient")
   * @param data The pre-built resource data map
   * @return This builder for method chaining
   */
  @Nonnull
  public FhirPathTestBuilder withSubject(
      @Nonnull final String resourceTypeName, @Nonnull final java.util.Map<String, Object> data) {
    this.currentSubject = new MapTestSubject(ResourceTestData.of(resourceTypeName, data));
    return this;
  }

  /**
   * Set the current resource test data with an explicit ResourceType.
   *
   * @param resourceType The explicit resource type definition
   * @param builderConsumer Consumer that builds the resource data
   * @return This builder for method chaining
   */
  @Nonnull
  public FhirPathTestBuilder withSubject(
      @Nonnull final ResourceType resourceType,
      @Nonnull final java.util.function.Consumer<ResourceDataBuilder> builderConsumer) {
    final ResourceDataBuilder dataBuilder = new ResourceDataBuilder();
    builderConsumer.accept(dataBuilder);
    this.currentSubject =
        new MapTestSubject(ResourceTestData.of(resourceType, dataBuilder.build()));
    return this;
  }

  /**
   * Set the current test subject to a HAPI FHIR resource.
   *
   * <p>The resource will be serialized to FHIR JSON and loaded into Spark. Type resolution uses
   * {@link com.example.fhirpath.typing.FhirResourceType} backed by the HAPI definition.
   *
   * @param resource The HAPI FHIR resource object (e.g., Patient, Observation)
   * @return This builder for method chaining
   */
  @Nonnull
  public FhirPathTestBuilder withSubject(@Nonnull final IBaseResource resource) {
    this.currentSubject = new HapiTestSubject(resource);
    return this;
  }

  /**
   * Test that an expression equals an expected value (no context, expression used as description).
   *
   * @param expected The expected result value
   * @param expression The FHIRPath expression to evaluate
   * @return This builder for method chaining
   */
  @Nonnull
  public FhirPathTestBuilder testEquals(@Nullable Object expected, @Nonnull String expression) {
    return testEquals(expected, expression, (Context) null, expression);
  }

  /**
   * Test that an expression equals an expected value (no context, with description).
   *
   * @param expected The expected result value
   * @param expression The FHIRPath expression to evaluate
   * @param description A description of what this test verifies
   * @return This builder for method chaining
   */
  @Nonnull
  public FhirPathTestBuilder testEquals(
      @Nullable Object expected, @Nonnull String expression, @Nonnull String description) {
    return testEquals(expected, expression, (Context) null, description);
  }

  /**
   * Test that an expression with context equals an expected value (context, expression as
   * description).
   *
   * @param expected The expected result value
   * @param expression The FHIRPath expression to evaluate
   * @param context The context expression wrapper
   * @return This builder for method chaining
   */
  @Nonnull
  public FhirPathTestBuilder testEquals(
      @Nullable Object expected, @Nonnull String expression, @Nonnull Context context) {
    return testEquals(expected, expression, context, expression);
  }

  /**
   * Test that an expression with context equals an expected value (context + description).
   *
   * @param expected The expected result value
   * @param expression The FHIRPath expression to evaluate
   * @param context The context expression wrapper
   * @param description A description of what this test verifies
   * @return This builder for method chaining
   */
  @Nonnull
  public FhirPathTestBuilder testEquals(
      @Nullable Object expected,
      @Nonnull String expression,
      @Nullable Context context,
      @Nullable String description) {
    final String testDescription =
        buildTestDescription(expression, context, formatExpected(expected), description);

    testCases.add(
        new TestCase(
            testDescription, expression, context, currentSubject, new EqualsAssertion(expected)));
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
   * @param expression The FHIRPath expression to evaluate
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
   * @param context The context expression wrapper
   * @return This builder for method chaining
   */
  @Nonnull
  public FhirPathTestBuilder testTrue(@Nonnull String expression, @Nonnull Context context) {
    return testEquals(true, expression, context);
  }

  /**
   * Test that an expression with context evaluates to true (context + description).
   *
   * @param expression The FHIRPath expression to evaluate
   * @param context The context expression wrapper
   * @param description A description of what this test verifies
   * @return This builder for method chaining
   */
  @Nonnull
  public FhirPathTestBuilder testTrue(
      @Nonnull String expression, @Nonnull Context context, @Nonnull String description) {
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
   * @param expression The FHIRPath expression to evaluate
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
   * @param context The context expression wrapper
   * @return This builder for method chaining
   */
  @Nonnull
  public FhirPathTestBuilder testFalse(@Nonnull String expression, @Nonnull Context context) {
    return testEquals(false, expression, context);
  }

  /**
   * Test that an expression with context evaluates to false (context + description).
   *
   * @param expression The FHIRPath expression to evaluate
   * @param context The context expression wrapper
   * @param description A description of what this test verifies
   * @return This builder for method chaining
   */
  @Nonnull
  public FhirPathTestBuilder testFalse(
      @Nonnull String expression, @Nonnull Context context, @Nonnull String description) {
    return testEquals(false, expression, context, description);
  }

  /**
   * Test that an expression evaluates to an empty collection (no context, expression as
   * description).
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
   * @param expression The FHIRPath expression to evaluate
   * @param description A description of what this test verifies
   * @return This builder for method chaining
   */
  @Nonnull
  public FhirPathTestBuilder testEmpty(@Nonnull String expression, @Nonnull String description) {
    return testEmpty(expression, (Context) null, description);
  }

  /**
   * Test that an expression with context evaluates to an empty collection (context, expression as
   * description).
   *
   * @param expression The FHIRPath expression to evaluate
   * @param context The context expression wrapper
   * @return This builder for method chaining
   */
  @Nonnull
  public FhirPathTestBuilder testEmpty(@Nonnull String expression, @Nonnull Context context) {
    return testEmpty(expression, context, expression);
  }

  /**
   * Test that an expression with context evaluates to an empty collection (context + description).
   *
   * @param expression The FHIRPath expression to evaluate
   * @param context The context expression wrapper
   * @param description A description of what this test verifies
   * @return This builder for method chaining
   */
  @Nonnull
  public FhirPathTestBuilder testEmpty(
      @Nonnull String expression, @Nullable Context context, @Nullable String description) {
    final String testDescription =
        buildTestDescription(
            expression,
            context,
            "empty", // Special marker for empty collections
            description);

    testCases.add(
        new TestCase(testDescription, expression, context, currentSubject, new EmptyAssertion()));
    return this;
  }

  /**
   * Test that an expression throws an exception (no context, expression as description).
   *
   * @param expectedExceptionType The expected exception class
   * @param expression The FHIRPath expression to evaluate
   * @return This builder for method chaining
   */
  @Nonnull
  public FhirPathTestBuilder testError(
      @Nonnull Class<? extends Exception> expectedExceptionType, @Nonnull String expression) {
    return testError(expectedExceptionType, expression, (Context) null, expression);
  }

  /**
   * Test that an expression throws an exception (no context, with description).
   *
   * @param expectedExceptionType The expected exception class
   * @param expression The FHIRPath expression to evaluate
   * @param description A description of what this test verifies
   * @return This builder for method chaining
   */
  @Nonnull
  public FhirPathTestBuilder testError(
      @Nonnull Class<? extends Exception> expectedExceptionType,
      @Nonnull String expression,
      @Nonnull String description) {
    return testError(expectedExceptionType, expression, (Context) null, description);
  }

  /**
   * Test that an expression with context throws an exception (context, expression as description).
   *
   * @param expectedExceptionType The expected exception class
   * @param expression The FHIRPath expression to evaluate
   * @param context The context expression wrapper
   * @return This builder for method chaining
   */
  @Nonnull
  public FhirPathTestBuilder testError(
      @Nonnull Class<? extends Exception> expectedExceptionType,
      @Nonnull String expression,
      @Nonnull Context context) {
    return testError(expectedExceptionType, expression, context, expression);
  }

  /**
   * Test that an expression with context throws an exception (context + description).
   *
   * @param expectedExceptionType The expected exception class
   * @param expression The FHIRPath expression to evaluate
   * @param context The context expression wrapper
   * @param description A description of what this test verifies
   * @return This builder for method chaining
   */
  @Nonnull
  public FhirPathTestBuilder testError(
      @Nonnull Class<? extends Exception> expectedExceptionType,
      @Nonnull String expression,
      @Nullable Context context,
      @Nullable String description) {
    final String testDescription =
        buildTestDescription(
            expression, context, formatExpectedException(expectedExceptionType), description);

    testCases.add(
        new TestCase(
            testDescription,
            expression,
            context,
            currentSubject,
            new ErrorAssertion(expectedExceptionType)));
    return this;
  }

  /**
   * Build a stream of JUnit 5 dynamic tests from the configured test cases.
   *
   * <p>This method creates DynamicTest instances that delegate execution to the executor.
   *
   * <p>Tests can be filtered using the system property {@code fhirpath.test.filter}. Only tests
   * whose descriptions contain the filter pattern (case-insensitive) will be included.
   *
   * @return A stream of DynamicTest instances
   */
  @Nonnull
  public Stream<DynamicTest> build() {
    if (testCases.isEmpty()) {
      return Stream.empty();
    }

    // Apply filter if specified
    final TestFilter filter = TestFilter.fromSystemProperty();
    Stream<TestCase> testStream = testCases.stream();
    if (filter.isActive()) {
      // Recreate stream after count() terminal operation
      testStream = testCases.stream().filter(tc -> filter.matches(tc.description()));
    }
    return testStream.map(
        tc -> DynamicTest.dynamicTest(tc.description(), () -> executor.executeTest(tc)));
  }

  /**
   * Build comprehensive test description in format: expression [with context] => expected [:
   * description] [group]
   *
   * @param expression The FHIRPath expression being tested
   * @param context Optional context (null if not used)
   * @param expectedDisplay String representation of expected value
   * @param userDescription Optional user-provided description (null if not provided)
   * @return Formatted test description
   */
  @Nonnull
  private String buildTestDescription(
      @Nonnull String expression,
      @Nullable Context context,
      @Nonnull String expectedDisplay,
      @Nullable String userDescription) {
    final StringBuilder desc = new StringBuilder();

    // Core: expression [with context] => expected
    desc.append(expression);

    if (context != null) {
      desc.append(" with ").append(context.expression());
    }

    desc.append(" => ").append(expectedDisplay);

    // Optional: user description
    if (userDescription != null && !userDescription.equals(expression)) {
      desc.append(" : ").append(userDescription);
    }

    // Optional: group (as metadata at the end)
    if (currentGroup != null) {
      desc.append(" [").append(currentGroup).append("]");
    }

    return desc.toString();
  }

  /**
   * Format expected value for display in test description.
   *
   * @param expected The expected value (can be any type)
   * @return String representation suitable for test descriptions
   */
  @Nonnull
  private String formatExpected(@Nullable Object expected) {
    if (expected == null) {
      return "null";
    }
    if (expected instanceof String str) {
      return "'" + str + "'"; // String values in quotes
    }
    if (expected instanceof Boolean) {
      return expected.toString(); // true/false
    }
    if (expected instanceof List<?> list) {
      return list.toString(); // [1, 2, 3]
    }
    return expected.toString(); // Numbers, etc.
  }

  /**
   * Format expected exception for display in test description.
   *
   * @param exceptionType The expected exception class
   * @return Simple class name for display
   */
  @Nonnull
  private String formatExpectedException(@Nonnull Class<? extends Exception> exceptionType) {
    return exceptionType.getSimpleName();
  }
}
