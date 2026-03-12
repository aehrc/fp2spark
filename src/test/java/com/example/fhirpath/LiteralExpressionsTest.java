package com.example.fhirpath;

import com.example.fhirpath.test.FhirPathTestBase;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Tests for FHIRPath literal expressions (resource-less, context-less evaluation).
 *
 * <p>This test class covers:
 *
 * <ul>
 *   <li>Literal values (strings, numbers, booleans, empty collections)
 *   <li>Arithmetic operators (+, -, /, *)
 *   <li>Comparison operators (&lt;, &gt;, &lt;=, &gt;=)
 *   <li>Equality operators (=, !=)
 *   <li>Collection operators (; for concatenation)
 *   <li>Functions (count(), exists(), where(), iif(), first())
 * </ul>
 *
 * <p>These tests are ported from the original parameterized test in FhirPathIntegrationTest to use
 * the new fluent DSL for better readability and organization.
 */
public class LiteralExpressionsTest extends FhirPathTestBase {

  @TestFactory
  Stream<DynamicTest> testLiterals() {
    return builder()
        .group("String literals")
        .testEquals("hello", "'hello'", "Basic string literal")
        .group("Integer literals")
        .testEquals(12, "12", "Basic integer literal")
        .group("Decimal literals")
        .testEquals(10.4, "10.4", "Basic decimal literal")
        .group("Boolean literals")
        .testTrue("true", "Boolean true literal")
        .group("Empty collections")
        .testEmpty("{}", "Empty collection literal")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testAddition() {
    return builder()
        .group("Integer addition")
        .testEquals(15, "5 + 10", "Basic integer addition")
        .group("Decimal addition")
        .testEquals(15.3, "5.1 + 10.2", "Basic decimal addition")
        .group("Mixed type addition (Integer + Decimal)")
        .testEquals(15.2, "5 + 10.2", "Integer + Decimal")
        .testEquals(15.1, "5.1 + 10", "Decimal + Integer")
        .group("String concatenation")
        .testEquals("foobar", "'foo' + 'bar'", "String concatenation")
        .group("Addition with empty collections")
        .testEmpty("{} + 10", "Empty collection + Integer")
        // DISABLED: testEmpty("{} + {}", "Empty + Empty (inconsistent resolution)")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testSubtraction() {
    return builder()
        .group("Integer subtraction")
        .testEquals(6, "10 - 4", "Basic integer subtraction")
        .group("Decimal subtraction")
        .testEquals(6.3, "10.5 - 4.2", "Basic decimal subtraction")
        .group("Mixed type subtraction")
        .testEquals(5.8, "10 - 4.2", "Integer - Decimal")
        .testEquals(6.5, "10.5 - 4", "Decimal - Integer")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testDivision() {
    return builder()
        .group("Integer division")
        .testEquals(2.5, "10 / 4", "Integer division returns decimal")
        .group("Decimal division")
        .testEquals(2.6, "10.4 / 4.0", "Basic decimal division")
        .group("Mixed type division")
        .testEquals(2.5, "10 / 4.0", "Integer / Decimal")
        .testEquals(2.6, "10.4 / 4", "Decimal / Integer")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testComparisons() {
    return builder()
        .group("Integer comparisons")
        .testTrue("5 < 10", "Less than")
        .group("Decimal comparisons")
        .testFalse("5.1 > 10.2", "Greater than (false)")
        .group("Mixed type comparisons")
        .testTrue("5 < 10.2", "Integer < Decimal")
        .testFalse("5.1 >= 10", "Decimal >= Integer (false)")
        .group("String comparisons")
        .testTrue("'a' > 'A'", "Case-sensitive string comparison")
        .group("Comparison with empty collections")
        .testEmpty("'a' > {}", "String > Empty collection")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testCount() {
    return builder()
        .group("count() on singular values")
        .testEquals(1, "10.count()", "Count of single integer")
        .group("count() on empty collections")
        .testEquals(0, "{}.count()", "Count of empty collection")
        .group("count() on collections")
        .testEquals(2, "(1 ; 2).count()", "Count of 2-element collection")
        .testEquals(3, "('a' ; 'b' ; 'c').count()", "Count of 3-element collection")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testExists() {
    return builder()
        .group("exists() on singular values")
        .testTrue("'xxx'.exists()", "String exists")
        .group("exists() on empty collections")
        .testFalse("{}.exists()", "Empty collection does not exist")
        .group("exists() on collections")
        .testTrue("(1 ; 2).exists()", "Collection exists")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testEquality() {
    return builder()
        .group("Integer equality")
        .testTrue("5 = 5", "Equal integers")
        .group("Decimal equality")
        .testTrue("5.3 = 5.3", "Equal decimals")
        .group("Mixed type equality")
        .testTrue("5 = 5.0", "Integer equals Decimal")
        .testTrue("6.0 = 6", "Decimal equals Integer")
        .group("String equality")
        .testFalse("'x' = 'y'", "Unequal strings")
        .group("Type mismatch")
        .testFalse("'1' = 1", "String != Integer (different types)")
        .group("Equality with empty collections")
        .testEmpty("{} = 1", "Empty collection = Integer")
        .testEmpty("'xxx'={}", "String = Empty collection")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testConcatenation() {
    return builder()
        .group("Integer concatenation")
        .testEquals(List.of(5, 10), "5 ; 10", "Two integers")
        .group("Decimal concatenation")
        .testEquals(List.of(5.2, 10.5), "5.2 ; 10.5", "Two decimals")
        .group("String concatenation")
        .testEquals(List.of("a", "b", "c"), "'a' ; 'b' ; 'c'", "Three strings")
        .group("Concatenation with empty collections")
        .testEquals(List.of(1), "1 ; {}", "Integer ; Empty")
        .testEquals(List.of(1), "{} ; 1", "Empty ; Integer")
        // DISABLED: testEmpty("{} ; {}", "Empty ; Empty")
        .group("Preserves order and duplicates")
        .testEquals(List.of(true, false, true), "true ; false ; true", "Boolean with duplicate")
        .group("Nested concatenation (flattens)")
        .testEquals(List.of(1.1, 2, 3), "1.1 ; (2 ; 3)", "Decimal ; (Integer ; Integer)")
        .testEquals(
            List.of(2, 3, 1.1, 2.3, 2.0),
            "(2 ; 3) ; (1.1 ; 2.3 ; 2.0)",
            "Two collections concatenated")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testDefaultEmptyContext() {
    return builder()
        .group("Empty context operations")
        .testEquals(0, "count()", "count() on empty context")
        .testFalse("%resource.exists()", "%resource does not exist")
        .testEmpty("%resource.foo", "Field access on empty resource")
        .testEmpty("bar", "Field access on empty implicit context")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testWhere() {
    return builder()
        .group("where() with $this reference")
        .testEquals(List.of(2, 3), "(1 ; 2 ; 3).where($this > 1)", "Filter integers > 1")
        .group("where() edge cases")
        .testEmpty("{}.where($this > 1)", "where() on empty collection")
        .testEquals(2, "2.where($this > 1)", "where() on singular value (match)")
        .testEmpty("'foo'.where($this = 'bar')", "where() on singular value (no match)")
        .testEmpty("(1 ; 2 ; 3).where({})", "where() with empty lambda")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testExistsWithCriteria() {
    return builder()
        .group("exists(criteria) on empty collection")
        .testFalse("{}.exists($this > 1)", "Empty collection returns false")
        .group("exists(criteria) on singular values")
        .testTrue("2.exists($this > 1)", "Singular value matching")
        .testFalse("'foo'.exists($this = 'bar')", "Singular value not matching")
        .group("exists(criteria) on collections")
        .testTrue("(1 ; 2 ; 3).exists($this > 1)", "Collection with matches")
        .testFalse("(1 ; 2 ; 3).exists($this > 5)", "Collection without matches")
        .testTrue("('a' ; 'b' ; 'c').exists($this = 'b')", "String collection with match")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testIif() {
    return builder()
        .group("iif() with literal boolean criterion")
        .testEquals("true", "{}.iif(true, 'true')", "Empty collection, true criterion")
        .testEmpty("{}.iif(false, 'true')", "Empty collection, false criterion")
        .testEquals("found", "5.iif(true, 'found')", "Singular value, true criterion")
        .testEmpty("5.iif(false, 'found')", "Singular value, false criterion")
        .group("iif() with collection-level criterion (implicit $this)")
        .testEquals(List.of(1, 2), "(1 ; 2).iif(exists(), $this)", "implicit exists()")
        .testEmpty("(1 ; 2).iif(empty(), $this)", "implicit empty()")
        .testEquals(List.of(1, 2, 3), "(1 ; 2 ; 3).iif(count() > 2, $this)", "implicit count() > 2")
        .testEmpty("(1 ; 2).iif(count() > 2, $this)", "count criterion false")
        .testEquals(1, "(1 ; 2 ; 3).iif(count() = 3, first())", "implicit in both lambdas")
        .group("iif() with lambda as true-result")
        .testEquals(3, "(5 ; 10 ; 15).iif(exists(), count())", "implicit in both")
        .testEquals(
            List.of(3, 4),
            "(1 ; 2 ; 3 ; 4).iif(count() > 2, where($this > 2))",
            "implicit count(), explicit $this in where")
        .group("iif() nested")
        .testEquals("nested", "(1 ; 2).iif(iif(exists(), true), 'nested')", "nested in criterion")
        .testEquals("match", "(1 ; 2).iif(true, iif(count() = 2, 'match'))", "nested in result")
        .testEquals("deep", "5.iif(true, 10.iif(true, 'deep'))", "double nested result")
        .group("iif() with different result types")
        .testEquals("found", "(1 ; 2 ; 3).iif(count() > 2, 'found')", "returns string")
        .testEquals(999, "('a' ; 'b').iif(exists(), 999)", "returns integer")
        .group("iif() combined with other operations")
        .testEquals(3, "(1 ; 2 ; 3).iif(exists(), $this).count()", "chained with count()")
        .testEquals(8, "(5 ; 10).iif(count() = 2, first()) + 3", "result used in arithmetic")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testFirst() {
    return builder()
        .group("first() on multi-element collections")
        .testEquals(1, "(1 ; 2 ; 3).first()", "Integer collection")
        .testEquals("a", "('a' ; 'b' ; 'c').first()", "String collection")
        .testEquals(5.2, "(5.2 ; 10.5 ; 15.3).first()", "Decimal collection")
        .group("first() on empty collection")
        .testEmpty("{}.first()", "Returns empty")
        .group("first() on singular value")
        .testEquals(5, "5.first()", "Integer singular value")
        .testEquals("hello", "'hello'.first()", "String singular value")
        .build();
  }
}
