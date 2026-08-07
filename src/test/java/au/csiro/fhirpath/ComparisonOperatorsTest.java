package au.csiro.fhirpath;

import au.csiro.fhirpath.analyzer.CardinalityMismatchException;
import au.csiro.fhirpath.operation.OverloadResolutionException;
import au.csiro.fhirpath.test.FhirPathTestBase;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Tests for FHIRPath comparison operators ({@code >}, {@code <}, {@code >=}, {@code <=}).
 *
 * <p>Based on FHIRPath specification section 6.2: Comparison (lines 3190–3329)
 *
 * <p>Covers:
 *
 * <ul>
 *   <li>All four comparison operators for Integer, Decimal, and String types
 *   <li>Cross-type numeric coercion (Integer/Decimal)
 *   <li>Empty collection propagation
 *   <li>Boundary values (equal operands with strict vs non-strict operators)
 *   <li>Boolean not orderable (rejected with OverloadResolutionException)
 *   <li>Incompatible type rejection
 *   <li>Spec examples from sections 6.2.1-6.2.4
 *   <li>Compound expressions combining comparison with boolean operators
 *   <li>Resource field comparison
 * </ul>
 */
class ComparisonOperatorsTest extends FhirPathTestBase {

  // ========== Integer comparison ==========

  @TestFactory
  Stream<DynamicTest> testIntegerGreaterThan() {
    return builder()
        .group("Integer greater than")
        .testTrue("10 > 5")
        .testFalse("5 > 10")
        .testFalse("5 > 5", "Equal values - strict")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testIntegerLessThan() {
    return builder()
        .group("Integer less than")
        .testTrue("5 < 10")
        .testFalse("10 < 5")
        .testFalse("5 < 5", "Equal values - strict")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testIntegerGreaterOrEqual() {
    return builder()
        .group("Integer greater or equal")
        .testTrue("10 >= 5")
        .testFalse("5 >= 10")
        .testTrue("5 >= 5", "Equal values")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testIntegerLessOrEqual() {
    return builder()
        .group("Integer less or equal")
        .testTrue("5 <= 10")
        .testFalse("10 <= 5")
        .testTrue("5 <= 5", "Equal values")
        .build();
  }

  // ========== Decimal comparison ==========

  @TestFactory
  Stream<DynamicTest> testDecimalComparison() {
    return builder()
        .group("Decimal comparison")
        .testTrue("10.0 > 5.0")
        .testFalse("5.0 > 10.0")
        .testTrue("5.0 < 10.0")
        .testFalse("10.0 < 5.0")
        .testTrue("10.0 >= 5.0")
        .testTrue("5.0 >= 5.0", "Equal values")
        .testFalse("5.0 >= 10.0")
        .testTrue("5.0 <= 10.0")
        .testTrue("5.0 <= 5.0", "Equal values")
        .testFalse("10.0 <= 5.0")
        .build();
  }

  // ========== String comparison (lexicographic/Unicode) ==========

  @TestFactory
  Stream<DynamicTest> testStringComparison() {
    return builder()
        .group("String comparison (Unicode ordering)")
        .testTrue("'abc' > 'ABC'", "Lowercase > uppercase per Unicode")
        .testFalse("'ABC' > 'abc'")
        .testFalse("'abc' < 'ABC'")
        .testTrue("'ABC' < 'abc'")
        .testTrue("'abc' >= 'ABC'")
        .testTrue("'abc' >= 'abc'", "Equal strings")
        .testFalse("'abc' <= 'ABC'")
        .testTrue("'abc' <= 'abc'", "Equal strings")
        .testTrue("'b' > 'a'")
        .testTrue("'abc' < 'abd'", "Differ in last char")
        .testTrue("'ab' < 'abc'", "Prefix is less than full string")
        .build();
  }

  // ========== Cross-type numeric comparison (Integer/Decimal) ==========

  @TestFactory
  Stream<DynamicTest> testCrossTypeNumericComparison() {
    return builder()
        .group("Integer/Decimal cross-type comparison")
        .testTrue("10 > 5.0", "Integer converted to Decimal per spec")
        .testFalse("10 < 5.0")
        .testTrue("5.0 < 10")
        .testTrue("10 >= 5.0")
        .testTrue("5 <= 5.0", "Equal cross-type values")
        .testTrue("5 >= 5.0", "Equal cross-type values")
        .testFalse("5 > 5.0", "Equal cross-type - strict")
        .testFalse("5 < 5.0", "Equal cross-type - strict")
        .build();
  }

  // ========== Empty collection semantics ==========

  @TestFactory
  Stream<DynamicTest> testEmptyCollectionSemantics() {
    return builder()
        .group("Empty collection propagation - greater than")
        .testEmpty("{} > 1", "Empty > value")
        .testEmpty("1 > {}", "Value > empty")
        .testEmpty("{} > {}", "Both empty >")
        .group("Empty collection propagation - less than")
        .testEmpty("{} < 1", "Empty < value")
        .testEmpty("1 < {}", "Value < empty")
        .testEmpty("{} < {}", "Both empty <")
        .group("Empty collection propagation - greater or equal")
        .testEmpty("{} >= 1", "Empty >= value")
        .testEmpty("1 >= {}", "Value >= empty")
        .testEmpty("{} >= {}", "Both empty >=")
        .group("Empty collection propagation - less or equal")
        .testEmpty("{} <= 1", "Empty <= value")
        .testEmpty("1 <= {}", "Value <= empty")
        .testEmpty("{} <= {}", "Both empty <=")
        .build();
  }

  // ========== Error: Boolean not orderable ==========

  @TestFactory
  Stream<DynamicTest> testBooleanNotOrderable() {
    return builder()
        .group("Boolean not orderable (not in COMPARABLE)")
        .testError(OverloadResolutionException.class, "true > false", "Boolean >")
        .testError(OverloadResolutionException.class, "true < false", "Boolean <")
        .testError(OverloadResolutionException.class, "true >= false", "Boolean >=")
        .testError(OverloadResolutionException.class, "true <= false", "Boolean <=")
        .build();
  }

  // ========== Error: Incompatible types ==========

  @TestFactory
  Stream<DynamicTest> testIncompatibleTypes() {
    return builder()
        .group("Incompatible types rejected")
        .testError(OverloadResolutionException.class, "1 > 'hello'", "Integer vs String")
        .testError(OverloadResolutionException.class, "'hello' < 1", "String vs Integer")
        .testError(OverloadResolutionException.class, "true >= 1", "Boolean vs Integer")
        .testError(OverloadResolutionException.class, "'abc' <= true", "String vs Boolean")
        .build();
  }

  // ========== Error: Non-singular collections ==========

  @TestFactory
  Stream<DynamicTest> testCardinalityErrors() {
    return builder()
        .group("Non-singular collections rejected")
        .testError(CardinalityMismatchException.class, "(1 ; 2) > 5", "MANY left operand")
        .testError(CardinalityMismatchException.class, "5 < (1 ; 2)", "MANY right operand")
        .testError(CardinalityMismatchException.class, "(1 ; 2) >= (3 ; 4)", "Both operands MANY")
        .build();
  }

  // ========== Compound expressions ==========

  @TestFactory
  Stream<DynamicTest> testCompoundExpressions() {
    return builder()
        .group("Compound expressions with comparison")
        .testTrue("(5 > 3) and (10 <= 10)", "Both conditions true")
        .testFalse("(5 > 3) and (10 < 10)", "Second condition false")
        .testTrue("(5 > 10) or (3 < 5)", "First false, second true")
        .testTrue("(5 >= 5) and (5 <= 5)", "Equal with both non-strict operators")
        .build();
  }

  // ========== Resource field comparison ==========

  @TestFactory
  Stream<DynamicTest> testSingularFieldComparison() {
    return builder()
        .group("Singular resource field comparison")
        .withSubject("Patient", p -> p.string("id", "p1"))
        .testTrue("id > 'a'", "String field > literal")
        .testFalse("id < 'a'", "String field not < literal")
        .testTrue("id >= 'p1'", "String field >= equal literal")
        .testTrue("id <= 'p1'", "String field <= equal literal")
        .testFalse("id > 'p1'", "String field not > equal literal")
        .testFalse("id < 'p1'", "String field not < equal literal")
        .build();
  }
}
