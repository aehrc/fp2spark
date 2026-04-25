package com.example.fhirpath.compat;

import static com.example.fhirpath.compat.ExclusionGroup.expectedDifference;
import static com.example.fhirpath.compat.ExclusionGroup.notImplemented;
import static org.junit.jupiter.api.Assertions.fail;

import jakarta.annotation.Nonnull;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Central registry of expected test failures (exclusions) for the compat test suite.
 *
 * <p>Exclusions are grouped by root cause. Each group carries a reason string and one or more
 * matchers that identify individual test runs by display name (and optionally test class).
 *
 * <p>This class is intentionally separate from the test classes so that test classes remain clean
 * mirrors of Pathling's test definitions.
 *
 * @see ExclusionGroup
 * @see ExclusionRule
 */
public final class CompatExclusions {

  private static final Logger log = LoggerFactory.getLogger(CompatExclusions.class);

  private CompatExclusions() {}

  // @formatter:off
  private static final List<ExclusionRule> RULES =
      Stream.of(

              // --- Strict typing: Boolean operators reject non-Boolean input ---
              // fp2sql enforces Boolean-only operands for not/and/or/xor;
              // Pathling coerces non-Boolean singletons to Boolean
              expectedDifference("Strict typing: Boolean operators reject non-Boolean input")
                  .scope(BooleanLogicFunctionsDslTest.class)
                  .expressions(
                      "emptyString.not()",
                      "stringValue.where($this.empty()).not()",
                      "stringArray.where($this.empty()).not()",
                      "%resource.not()",
                      "stringValue.not()",
                      "stringArray.where($this='value1').not()",
                      "choiceField.value.not()",
                      "choiceField.value.ofType(string).not()",
                      "choiceField.value.ofType(integer).not()")
                  .scope(SystemDslTest.class)
                  // empty evaluation for functions
                  .expressions(
                      "emptyString.not()",
                      "stringArray.where($this.empty()).not()",
                      "emptyCoding.not()",
                      "emptyCoding.where($this.empty()).not()",
                      "choiceField.value.ofType(integer).not()")
                  // empty evaluation for operators
                  .expressions(
                      "true and emptyString",
                      "stringArray.where($this.empty()) or false",
                      "false xor emptyCoding",
                      "emptyCoding.where($this.empty()) or false",
                      "true and choiceField.value.ofType(integer)")
                  // single element collection evaluate to true in functions
                  .expressions(
                      "stringValue.not()",
                      "codingValue.not()",
                      "stringArrayOne.not()",
                      "stringArray.where($this='value1').not()",
                      "%resource.not()",
                      "choiceField.value.not()",
                      "choiceField.value.ofType(string).not()")
                  // single element collection evaluate to true in boolean operators
                  .expressions(
                      "stringValue or {}",
                      "true and codingValue",
                      "{} or stringArrayOne",
                      "stringArray.where($this='value1') and true",
                      "choiceField.value and true",
                      "choiceField.value.ofType(string) or false")
                  // boolean evaluation in boolean expressions (where with non-Boolean)
                  .expressions(
                      "complex.where($this.singularString).id",
                      "complex.where($this.oneString).id"),

              // --- Strict typing: where()/exists() rejects non-Boolean criteria ---
              expectedDifference(
                      "Strict typing: where()/exists() rejects non-Boolean criteria expression")
                  .scope(ExistenceFunctionsDslTest.class)
                  .expressions("people.exists(name)"),

              // --- Strict typing: comparison with incompatible empty types ---
              expectedDifference("Strict typing: comparison rejects incompatible types")
                  .scope(ComparisonOperatorsDslTest.class)
                  .expressions("str1 < boolEmpty"),

              // --- Membership operator: complex type (spec-correct, Pathling throws) ---
              expectedDifference("Membership operator: complex type equality is spec-correct")
                  .scope(MembershipOperatorsDslTest.class)
                  .expressions("name in name"),

              // --- Calendar-to-UCUM: only second/millisecond bridge allowed ---
              expectedDifference("Calendar-to-UCUM: non-bridge calendar duration conversion")
                  .scope(ConversionFunctionsDslTest.class)
                  .expressions(
                      "'2 minutes'.toQuantity('s')", "'2 minutes'.convertsToQuantity('s')"),

              // --- Type functions: as operator on where()-filtered collections ---
              expectedDifference("Strict typing: as operator requires singleton input")
                  .scope(TypeFunctionsDslTest.class)
                  .expressions(
                      "component.where(value.is(String)).value.as(String)",
                      "component.where(value.is(Boolean)).value.as(Boolean)"),

              // --- ofType() cardinality: + operator requires singleton ---
              expectedDifference(
                      "Strict typing: ofType on plural returns MANY, + requires singleton")
                  .scope(FilteringAndProjectionFunctionsDslTest.class)
                  .expressions(
                      "polyStrings.value.ofType(System.Decimal)"
                          + " + polyStrings.value.ofType(FHIR.decimal)"),

              // --- repeat() function not implemented ---
              notImplemented("repeat() function not implemented")
                  .scope(RepeatFunctionDslTest.class)
                  .pattern("^(?!.*=> Exception)"),

              // --- repeatAll() function not implemented ---
              notImplemented("repeatAll() function not implemented")
                  .scope(RepeatAllFunctionDslTest.class)
                  .pattern("^(?!.*=> Exception)"),

              // --- Equality: uncomparable types with empty operand ---
              expectedDifference(
                      "Uncomparable types with empty operand return false instead of empty")
                  .scope(EqualityOperatorsDslTest.class)
                  .expressions(
                      "intVal = dateVal.where(false)", "intArray.where(false) != dateArray"),

              // --- Comparison: empty with uncomparable types throws instead of empty ---
              expectedDifference("Comparison with empty uncomparable type throws instead of empty")
                  .scope(ComparisonOperatorsDslTest.class)
                  .expressions("true > {}", "{} >= codingVal"),

              // --- Year ↔ month calendar equality/comparison (spec-exact, #178) ---
              // fp2sql applies the spec-exact factor 1 year = 12 months per §3.3,
              // which Pathling does not implement. fp2sql is ahead of Pathling here.
              expectedDifference("Year ↔ month calendar-duration equality per FHIRPath §3.3 (#178)")
                  .scope(CombiningOperatorsDslTest.class)
                  .expressions("1 year | 12 months")
                  .scope(ComparisonOperatorsDslTest.class)
                  .expressions("1 year > 1 month"))
          .flatMap(g -> g.build().stream())
          .toList();

  // @formatter:on

  /**
   * Find the first exclusion rule matching the given test.
   *
   * @param displayName The DynamicTest display name
   * @param testClass The test class that produced the test
   * @return The matching rule, or empty if no exclusion applies
   */
  static Optional<ExclusionRule> findMatch(
      @Nonnull String displayName, @Nonnull Class<?> testClass) {
    return RULES.stream().filter(rule -> rule.matches(displayName, testClass)).findFirst();
  }

  /**
   * Wrap a DynamicTest with XFAIL behavior.
   *
   * <p>The test still runs. If it fails (expected), the failure is absorbed and the test passes. If
   * it unexpectedly passes, the test fails with a message to remove the exclusion.
   */
  static DynamicTest wrapXFail(@Nonnull DynamicTest test, @Nonnull ExclusionRule rule) {
    String tag = "[XFAIL:" + rule.category().label() + "]";
    return DynamicTest.dynamicTest(
        tag + " " + test.getDisplayName(),
        () -> {
          boolean passed = false;
          try {
            test.getExecutable().execute();
            passed = true;
          } catch (AssertionError | Exception e) {
            // Expected failure — absorb it
            log.info("{} {} — {}", tag, test.getDisplayName(), rule.reason());
          }
          if (passed) {
            fail("XFAIL test unexpectedly passed. Remove this exclusion. Reason: " + rule.reason());
          }
        });
  }
}
