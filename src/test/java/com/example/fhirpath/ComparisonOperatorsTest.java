package com.example.fhirpath;

import com.example.fhirpath.operation.OverloadResolutionException;
import com.example.fhirpath.test.FhirPathTestBase;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Tests for FHIRPath comparison operators ({@code >}, {@code <}, {@code >=}, {@code <=}).
 *
 * <p>Based on FHIRPath specification section 6.2: Comparison
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
        .testTrue("10 > 5", "Greater value")
        .testFalse("5 > 10", "Lesser value")
        .testFalse("5 > 5", "Equal values (strict)")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testIntegerLessThan() {
    return builder()
        .group("Integer less than")
        .testTrue("5 < 10", "Lesser value")
        .testFalse("10 < 5", "Greater value")
        .testFalse("5 < 5", "Equal values (strict)")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testIntegerGreaterOrEqual() {
    return builder()
        .group("Integer greater or equal")
        .testTrue("10 >= 5", "Greater value")
        .testFalse("5 >= 10", "Lesser value")
        .testTrue("5 >= 5", "Equal values")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testIntegerLessOrEqual() {
    return builder()
        .group("Integer less or equal")
        .testTrue("5 <= 10", "Lesser value")
        .testFalse("10 <= 5", "Greater value")
        .testTrue("5 <= 5", "Equal values")
        .build();
  }

  // ========== Decimal comparison ==========

  @TestFactory
  Stream<DynamicTest> testDecimalComparison() {
    return builder()
        .group("Decimal comparison")
        .testTrue("10.0 > 5.0", "Greater than")
        .testFalse("5.0 > 10.0", "Not greater than")
        .testTrue("5.0 < 10.0", "Less than")
        .testFalse("10.0 < 5.0", "Not less than")
        .testTrue("10.0 >= 5.0", "Greater or equal (greater)")
        .testTrue("5.0 >= 5.0", "Greater or equal (equal)")
        .testFalse("5.0 >= 10.0", "Not greater or equal")
        .testTrue("5.0 <= 10.0", "Less or equal (less)")
        .testTrue("5.0 <= 5.0", "Less or equal (equal)")
        .testFalse("10.0 <= 5.0", "Not less or equal")
        .build();
  }

  // ========== String comparison (lexicographic/Unicode) ==========

  @TestFactory
  Stream<DynamicTest> testStringComparison() {
    return builder()
        .group("String comparison (Unicode ordering)")
        .testTrue("'abc' > 'ABC'", "Lowercase > uppercase (spec example)")
        .testFalse("'ABC' > 'abc'", "Uppercase not > lowercase")
        .testFalse("'abc' < 'ABC'", "Lowercase not < uppercase (spec example)")
        .testTrue("'ABC' < 'abc'", "Uppercase < lowercase")
        .testTrue("'abc' >= 'ABC'", "Lowercase >= uppercase")
        .testTrue("'abc' >= 'abc'", "Equal strings >=")
        .testFalse("'abc' <= 'ABC'", "Lowercase not <= uppercase")
        .testTrue("'abc' <= 'abc'", "Equal strings <=")
        .testTrue("'b' > 'a'", "Single char comparison")
        .testTrue("'abc' < 'abd'", "Differ in last char")
        .testTrue("'ab' < 'abc'", "Prefix is less than full string")
        .build();
  }

  // ========== Cross-type numeric comparison (Integer/Decimal) ==========

  @TestFactory
  Stream<DynamicTest> testCrossTypeNumericComparison() {
    return builder()
        .group("Integer/Decimal cross-type comparison")
        .testTrue("10 > 5.0", "Integer > Decimal (spec example)")
        .testFalse("10 < 5.0", "Integer not < Decimal (spec example)")
        .testTrue("5.0 < 10", "Decimal < Integer")
        .testTrue("10 >= 5.0", "Integer >= Decimal")
        .testTrue("5 <= 5.0", "Integer <= equal Decimal")
        .testTrue("5 >= 5.0", "Integer >= equal Decimal")
        .testFalse("5 > 5.0", "Integer not > equal Decimal")
        .testFalse("5 < 5.0", "Integer not < equal Decimal")
        .build();
  }

  // ========== Empty collection semantics ==========

  @TestFactory
  Stream<DynamicTest> testEmptyCollectionSemantics() {
    return builder()
        .group("Empty collection propagation — greater than")
        .testEmpty("{} > 1", "Empty > value")
        .testEmpty("1 > {}", "Value > empty")
        .testEmpty("{} > {}", "Both empty >")
        .group("Empty collection propagation — less than")
        .testEmpty("{} < 1", "Empty < value")
        .testEmpty("1 < {}", "Value < empty")
        .testEmpty("{} < {}", "Both empty <")
        .group("Empty collection propagation — greater or equal")
        .testEmpty("{} >= 1", "Empty >= value")
        .testEmpty("1 >= {}", "Value >= empty")
        .testEmpty("{} >= {}", "Both empty >=")
        .group("Empty collection propagation — less or equal")
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
        .withSubject(
            "Patient",
            p ->
                p.string("id", "p1")
                    .elementArray(
                        "name", n -> n.string("family", "Smith").stringArray("given", "John")))
        .testTrue("id > 'a'", "String field > literal")
        .testFalse("id < 'a'", "String field not < literal")
        .testTrue("id >= 'p1'", "String field >= equal literal")
        .testTrue("id <= 'p1'", "String field <= equal literal")
        .testFalse("id > 'p1'", "String field not > equal literal")
        .testFalse("id < 'p1'", "String field not < equal literal")
        .build();
  }
}
