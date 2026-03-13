package com.example.fhirpath;

import com.example.fhirpath.test.FhirPathTestBase;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Tests for FHIRPath Quantity equality and comparison operators.
 *
 * <p>Based on FHIRPath specification: Quantity equality compares code fields (strict,
 * case-sensitive comparison). If codes match, values are compared. If codes differ, the result is
 * empty.
 *
 * <p>Covers:
 *
 * <ul>
 *   <li>Same-unit equality and inequality
 *   <li>Different-unit returns empty
 *   <li>Calendar duration equality
 *   <li>Calendar vs UCUM returns empty (strict code comparison)
 *   <li>Comparison operators ({@code >}, {@code <}, {@code >=}, {@code <=})
 *   <li>Cross-type implicit conversion (INTEGER/DECIMAL → QUANTITY)
 * </ul>
 */
public class QuantityEqualityAndComparisonTest extends FhirPathTestBase {

  // ===== Equality =====

  @TestFactory
  Stream<DynamicTest> testSameUnitEquality() {
    return builder()
        .group("Same unit equality")
        .testTrue("10 'mg' = 10 'mg'", "Same value and unit")
        .testFalse("10 'mg' = 20 'mg'", "Different value, same unit")
        .testTrue("1 year = 1 year", "Calendar duration equality")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testDifferentUnitReturnsEmpty() {
    return builder()
        .group("Different unit returns empty")
        .testEmpty("10 'mg' = 10 'kg'", "Different UCUM units")
        .testEmpty("1 year = 1 'a'", "Calendar vs UCUM")
        .testEmpty("1 second = 1 's'", "Calendar second vs UCUM 's'")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testNotEquals() {
    return builder()
        .group("Not equals operator")
        .testTrue("10 'mg' != 20 'mg'", "Different values, same unit")
        .testFalse("10 'mg' != 10 'mg'", "Same values, same unit")
        .testEmpty("10 'mg' != 10 'kg'", "Different units returns empty")
        .build();
  }

  // ===== Comparison =====

  @TestFactory
  Stream<DynamicTest> testComparisonSameUnit() {
    return builder()
        .group("Comparison same unit")
        .testTrue("20 'mg' > 10 'mg'", "Greater than")
        .testFalse("10 'mg' > 20 'mg'", "Not greater than")
        .testTrue("10 'mg' < 20 'mg'", "Less than")
        .testFalse("20 'mg' < 10 'mg'", "Not less than")
        .testTrue("10 'mg' >= 10 'mg'", "Greater or equal (equal)")
        .testTrue("20 'mg' >= 10 'mg'", "Greater or equal (greater)")
        .testTrue("10 'mg' <= 10 'mg'", "Less or equal (equal)")
        .testTrue("10 'mg' <= 20 'mg'", "Less or equal (less)")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testComparisonDifferentUnitReturnsEmpty() {
    return builder()
        .group("Comparison different unit returns empty")
        .testEmpty("20 'mg' > 10 'kg'", "gt with different units")
        .testEmpty("10 'mg' < 20 'kg'", "lt with different units")
        .testEmpty("10 'mg' >= 20 'kg'", "geq with different units")
        .testEmpty("10 'mg' <= 20 'kg'", "leq with different units")
        .build();
  }

  // ===== Cross-type implicit conversion =====

  @TestFactory
  Stream<DynamicTest> testCrossTypeIntegerToQuantity() {
    return builder()
        .group("Cross-type INTEGER → QUANTITY")
        .testEmpty("10 = 10 'mg'", "Integer vs UCUM quantity → empty (different units)")
        .testTrue("10 = 10 '1'", "Integer vs default unit quantity → true")
        .testEmpty("5 > 3 'mg'", "Integer comparison vs UCUM → empty")
        .testTrue("5 > 3 '1'", "Integer comparison vs default unit → true")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testCrossTypeDecimalToQuantity() {
    return builder()
        .group("Cross-type DECIMAL → QUANTITY")
        .testEmpty("10.0 = 10.0 'mg'", "Decimal vs UCUM quantity → empty")
        .testTrue("10.0 = 10.0 '1'", "Decimal vs default unit quantity → true")
        .build();
  }
}
