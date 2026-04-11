package com.example.fhirpath.compat.yaml.format;

import com.example.fhirpath.compat.yaml.YamlTestDefinition.TestCase;
import jakarta.annotation.Nullable;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * A single exclusion rule parsed from {@code config.yaml}.
 *
 * <p>Ported from Pathling's {@code au.csiro.pathling.test.yaml.format.ExcludeRule}. Public fields
 * with setters are required for SnakeYAML's JavaBean deserialization.
 *
 * <p>Matcher types supported: {@code function} (substring match on {@code name(}), {@code
 * expression} (regex against expression), {@code any} (substring on expression or description).
 * SpEL matchers from Pathling are not supported — any entry is silently ignored.
 *
 * <p>Outcome semantics:
 *
 * <ul>
 *   <li>{@code null} (YAML absent or explicit {@code null}) → skip (TestAbortedException)
 *   <li>{@code error} → expect the test to throw an exception
 *   <li>{@code failure} → expect the test to fail an assertion (XFAIL)
 *   <li>{@code pass} → expect the test to pass normally
 * </ul>
 */
public class ExcludeRule {

  public static final String OUTCOME_ERROR = "error";
  public static final String OUTCOME_FAILURE = "failure";
  public static final String OUTCOME_PASS = "pass";

  @Nullable private String id;
  @Nullable private String title;
  @Nullable private String comment;
  @Nullable private String type;
  @Nullable private String outcome;
  private boolean disabled = false;
  @Nullable private List<String> function;
  @Nullable private List<String> expression;
  @Nullable private List<String> desc;
  @Nullable private List<String> any;
  @Nullable private List<String> spel;

  @Nullable
  public String getId() {
    return id;
  }

  public void setId(@Nullable final String id) {
    this.id = id;
  }

  @Nullable
  public String getTitle() {
    return title;
  }

  public void setTitle(@Nullable final String title) {
    this.title = title;
  }

  @Nullable
  public String getComment() {
    return comment;
  }

  public void setComment(@Nullable final String comment) {
    this.comment = comment;
  }

  @Nullable
  public String getType() {
    return type;
  }

  public void setType(@Nullable final String type) {
    this.type = type;
  }

  @Nullable
  public String getOutcome() {
    return outcome;
  }

  public void setOutcome(@Nullable final String outcome) {
    this.outcome = outcome;
  }

  public boolean isDisabled() {
    return disabled;
  }

  public void setDisabled(final boolean disabled) {
    this.disabled = disabled;
  }

  @Nullable
  public List<String> getFunction() {
    return function;
  }

  public void setFunction(@Nullable final List<String> function) {
    this.function = function;
  }

  @Nullable
  public List<String> getExpression() {
    return expression;
  }

  public void setExpression(@Nullable final List<String> expression) {
    this.expression = expression;
  }

  @Nullable
  public List<String> getDesc() {
    return desc;
  }

  public void setDesc(@Nullable final List<String> desc) {
    this.desc = desc;
  }

  @Nullable
  public List<String> getAny() {
    return any;
  }

  public void setAny(@Nullable final List<String> any) {
    this.any = any;
  }

  @Nullable
  public List<String> getSpel() {
    return spel;
  }

  public void setSpel(@Nullable final List<String> spel) {
    this.spel = spel;
  }

  /**
   * Builds a combined matcher predicate for this rule. The predicate returns {@code true} when the
   * rule has at least one matcher and any of them match the test case. A disabled rule never
   * matches.
   */
  Predicate<TestCase> toPredicate() {
    if (disabled) {
      return tc -> false;
    }
    final List<Predicate<TestCase>> predicates =
        Stream.of(
                Stream.ofNullable(function).flatMap(List::stream).map(ExcludeRule::functionMatcher),
                Stream.ofNullable(expression)
                    .flatMap(List::stream)
                    .map(ExcludeRule::expressionMatcher),
                Stream.ofNullable(any).flatMap(List::stream).map(ExcludeRule::anyMatcher),
                Stream.ofNullable(desc).flatMap(List::stream).map(ExcludeRule::descMatcher))
            .flatMap(s -> s)
            .toList();
    if (predicates.isEmpty()) {
      return tc -> false;
    }
    return tc -> predicates.stream().anyMatch(p -> p.test(tc));
  }

  private static Predicate<TestCase> functionMatcher(final String function) {
    final String needle = function + "(";
    return tc -> tc.expression().contains(needle);
  }

  private static Predicate<TestCase> expressionMatcher(final String regex) {
    final Pattern pattern = Pattern.compile(regex);
    return tc -> pattern.matcher(tc.expression()).find();
  }

  private static Predicate<TestCase> anyMatcher(final String substring) {
    return tc ->
        tc.expression().contains(substring)
            || (tc.description() != null && tc.description().contains(substring));
  }

  private static Predicate<TestCase> descMatcher(final String substring) {
    return tc -> tc.description() != null && tc.description().contains(substring);
  }

  @Override
  public String toString() {
    return "ExcludeRule{title="
        + title
        + ", id="
        + id
        + ", type="
        + type
        + ", outcome="
        + outcome
        + "}";
  }
}
