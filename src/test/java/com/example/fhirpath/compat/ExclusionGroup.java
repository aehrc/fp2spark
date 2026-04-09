package com.example.fhirpath.compat;

import com.example.fhirpath.compat.ExclusionRule.ExclusionMatcher;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Groups related exclusion rules under a single reason.
 *
 * <p>Provides a fluent API for defining matchers (expression substrings, regex patterns) optionally
 * scoped to a test class. All matchers in the group share the same reason string.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * group("Boolean operators reject non-Boolean input")
 *     .scope(BooleanLogicFunctionsDslTest.class)
 *     .expressions("stringValue.not()", "%resource.not()")
 *     .scope(SystemDslTest.class)
 *     .pattern("\\[Boolean evaluation\\]")
 * }</pre>
 */
final class ExclusionGroup {

  private final String reason;
  private final List<ExclusionMatcher> matchers = new ArrayList<>();
  @Nullable private Class<?> currentScope;
  @Nullable private ExclusionCategory category;

  private ExclusionGroup(@Nonnull String reason) {
    this.reason = reason;
  }

  /** Create a new exclusion group with the given reason. */
  @Nonnull
  static ExclusionGroup group(@Nonnull String reason) {
    return new ExclusionGroup(reason);
  }

  /** Create a group classified as an expected design difference. */
  @Nonnull
  static ExclusionGroup expectedDifference(@Nonnull String reason) {
    return group(reason).category(ExclusionCategory.EXPECTED_DIFFERENCE);
  }

  /** Create a group classified as a not-yet-implemented feature. */
  @Nonnull
  static ExclusionGroup notImplemented(@Nonnull String reason) {
    return group(reason).category(ExclusionCategory.NOT_IMPLEMENTED);
  }

  /** Create a group classified as a known or suspected bug. */
  @Nonnull
  static ExclusionGroup bug(@Nonnull String reason) {
    return group(reason).category(ExclusionCategory.BUG);
  }

  /** Create a group classified as a spec ambiguity. */
  @Nonnull
  static ExclusionGroup specAmbiguity(@Nonnull String reason) {
    return group(reason).category(ExclusionCategory.SPEC_AMBIGUITY);
  }

  @Nonnull
  private ExclusionGroup category(@Nonnull ExclusionCategory category) {
    this.category = category;
    return this;
  }

  /**
   * Scope subsequent matchers to a test class.
   *
   * <p>When scoped, matchers additionally require the test class to match. Scope applies to
   * matchers added after this call until the next {@code scope()} call or end of chain.
   */
  @Nonnull
  ExclusionGroup scope(@Nonnull Class<?> testClass) {
    this.currentScope = testClass;
    return this;
  }

  /** Clear the current scope so subsequent matchers are unscoped. */
  @Nonnull
  ExclusionGroup unscoped() {
    this.currentScope = null;
    return this;
  }

  /**
   * Add matchers for specific FHIRPath expressions (case-sensitive substring match against the
   * display name).
   */
  @Nonnull
  ExclusionGroup expressions(@Nonnull String... expressions) {
    for (String expr : expressions) {
      addMatcher((displayName, testClass) -> displayName.contains(expr));
    }
    return this;
  }

  /** Add a matcher using a regex pattern against the display name. */
  @Nonnull
  ExclusionGroup pattern(@Nonnull String regex) {
    Pattern compiled = Pattern.compile(regex);
    addMatcher((displayName, testClass) -> compiled.matcher(displayName).find());
    return this;
  }

  /** Build the flat list of {@link ExclusionRule} instances, one per matcher. */
  @Nonnull
  List<ExclusionRule> build() {
    if (category == null) {
      throw new IllegalStateException("ExclusionGroup must have a category: " + reason);
    }
    return matchers.stream().map(m -> new ExclusionRule(m, reason, category)).toList();
  }

  private void addMatcher(@Nonnull ExclusionMatcher baseMatcher) {
    if (currentScope != null) {
      final Class<?> scope = currentScope;
      matchers.add(
          (displayName, testClass) ->
              scope.equals(testClass) && baseMatcher.matches(displayName, testClass));
    } else {
      matchers.add(baseMatcher);
    }
  }
}
