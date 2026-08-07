package au.csiro.fhirpath.compat;

import au.csiro.fhirpath.test.FhirPathTestBuilder;
import jakarta.annotation.Nonnull;
import java.util.function.Function;
import java.util.stream.Stream;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.junit.jupiter.api.DynamicTest;

/**
 * Adapter that provides Pathling's {@code FhirPathTestBuilder} API on top of fp2sql's builder.
 *
 * <p>Key adaptations:
 *
 * <ul>
 *   <li>{@code withSubject(Function)} → builds Map via {@link CompatModelBuilder}, delegates to
 *       fp2sql's {@code withSubject(String, Map)}
 *   <li>{@code withResource(IBaseResource)} → delegates to fp2sql's {@code
 *       withSubject(IBaseResource)}
 *   <li>{@code testError(expr, desc)} → delegates with {@code Exception.class}
 *   <li>{@code testError(msg, expr, desc)} → delegates with {@code Exception.class} (drops message)
 * </ul>
 */
public class CompatTestBuilder {

  private final FhirPathTestBuilder delegate;
  private final Class<?> testClass;

  CompatTestBuilder(
      @Nonnull final FhirPathTestBuilder delegate, @Nonnull final Class<?> testClass) {
    this.delegate = delegate;
    this.testClass = testClass;
  }

  /**
   * Set test subject using Pathling's Function-based model builder API.
   *
   * <p>The function receives a {@link CompatModelBuilder} and returns it after configuration. The
   * built Map is passed to fp2sql's builder with default resource type "Test".
   */
  @Nonnull
  public CompatTestBuilder withSubject(
      @Nonnull final Function<CompatModelBuilder, CompatModelBuilder> builderFunction) {
    final CompatModelBuilder modelBuilder = new CompatModelBuilder();
    builderFunction.apply(modelBuilder);
    final java.util.Map<String, Object> model = modelBuilder.getModel();
    final String resourceType =
        model.containsKey("resourceType") ? String.valueOf(model.get("resourceType")) : "Test";
    delegate.withSubject(resourceType, model);
    return this;
  }

  /** Set test subject to a HAPI FHIR resource. Maps Pathling's {@code withResource}. */
  @Nonnull
  public CompatTestBuilder withResource(@Nonnull final IBaseResource resource) {
    delegate.withSubject(resource);
    return this;
  }

  /** Set the current test group. */
  @Nonnull
  public CompatTestBuilder group(@Nonnull final String groupName) {
    delegate.group(groupName);
    return this;
  }

  /** Test that an expression equals an expected value. */
  @Nonnull
  public CompatTestBuilder testEquals(
      final Object expected, @Nonnull final String expression, @Nonnull final String description) {
    delegate.testEquals(expected, expression, description);
    return this;
  }

  /** Test that an expression evaluates to true. */
  @Nonnull
  public CompatTestBuilder testTrue(
      @Nonnull final String expression, @Nonnull final String description) {
    delegate.testTrue(expression, description);
    return this;
  }

  /** Test that an expression evaluates to false. */
  @Nonnull
  public CompatTestBuilder testFalse(
      @Nonnull final String expression, @Nonnull final String description) {
    delegate.testFalse(expression, description);
    return this;
  }

  /** Test that an expression evaluates to an empty collection. */
  @Nonnull
  public CompatTestBuilder testEmpty(
      @Nonnull final String expression, @Nonnull final String description) {
    delegate.testEmpty(expression, description);
    return this;
  }

  /** Test that an expression throws any error. */
  @Nonnull
  public CompatTestBuilder testError(
      @Nonnull final String expression, @Nonnull final String description) {
    delegate.testError(Exception.class, expression, description);
    return this;
  }

  /**
   * Test that an expression throws an error. The error message is not verified — only that some
   * exception is thrown.
   */
  @Nonnull
  public CompatTestBuilder testError(
      @Nonnull final String ignoredErrorMessage,
      @Nonnull final String expression,
      @Nonnull final String description) {
    delegate.testError(Exception.class, expression, description);
    return this;
  }

  /**
   * Build the stream of dynamic tests, applying XFAIL wrapping for known exclusions.
   *
   * <p>Each test is checked against {@link CompatExclusions}. Matching tests are wrapped with XFAIL
   * behavior: failures are absorbed, unexpected passes are flagged.
   */
  @Nonnull
  public Stream<DynamicTest> build() {
    return delegate
        .build()
        .map(
            test ->
                CompatExclusions.findMatch(test.getDisplayName(), testClass)
                    .map(rule -> CompatExclusions.wrapXFail(test, rule))
                    .orElse(test));
  }
}
