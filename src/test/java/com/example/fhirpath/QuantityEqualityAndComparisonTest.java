package com.example.fhirpath;

import com.example.fhirpath.test.FhirPathTestBase;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Tests for FHIRPath Quantity equality and comparison operators.
 *
 * <p>Covers same-unit comparison, UCUM cross-unit conversion, calendar duration handling,
 * cross-type implicit conversion, and empty propagation.
 */
public class QuantityEqualityAndComparisonTest extends FhirPathTestBase {

  // ===== Same-unit equality =====

  @TestFactory
  Stream<DynamicTest> testSameUnitEquality() {
    return builder()
        .group("Same unit equality")
        .testTrue("10 'mg' = 10 'mg'", "Same value and unit")
        .testFalse("10 'mg' = 20 'mg'", "Different value, same unit")
        .testTrue("1 year = 1 year", "Calendar duration equality")
        .testTrue("1 'cm' = 1 'cm'", "Same UCUM unit cm")
        .build();
  }

  // ===== UCUM cross-unit equality =====

  @TestFactory
  Stream<DynamicTest> testCrossUnitEquality() {
    return builder()
        .group("Cross-unit equality (same dimension)")
        .testTrue("10 'cm' = 0.1 'm'", "cm to m conversion")
        .testTrue("1000 'mg' = 1 'g'", "mg to g conversion")
        .testFalse("10 'cm' = 10 'm'", "Same dimension, different value after conversion")
        .testFalse("500 'mg' = 1 'g'", "mg vs g not equal")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testDifferentDimensionReturnsEmpty() {
    return builder()
        .group("Different dimension returns empty")
        .testEmpty("10 'cm' = 10 'g'", "Length vs mass → empty")
        .testEmpty("1 'kg' = 1 's'", "Mass vs time → empty")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testNotEquals() {
    return builder()
        .group("Not equals operator")
        .testTrue("10 'mg' != 20 'mg'", "Different values, same unit")
        .testFalse("10 'mg' != 10 'mg'", "Same values, same unit")
        .testTrue("10 'mg' != 10 'kg'", "Same dimension, different values after conversion")
        .testEmpty("10 'cm' != 10 'g'", "Different dimension returns empty")
        .build();
  }

  // ===== Calendar duration equality =====

  @TestFactory
  Stream<DynamicTest> testCalendarDurationEquality() {
    return builder()
        .group("Calendar duration equality")
        .testTrue("1 year = 1 year", "Same calendar unit")
        .testTrue("1 month = 1 month", "Same calendar month")
        .testEmpty("1 year = 12 months", "Non-definite different calendar codes → empty")
        .build();
  }

  // ===== Calendar definite duration vs UCUM =====

  @TestFactory
  Stream<DynamicTest> testCalendarDefiniteDurationVsUcum() {
    return builder()
        .group("Calendar definite duration vs UCUM")
        .testTrue("1 second = 1 's'", "Calendar second = UCUM s")
        .testTrue("1000 milliseconds = 1 's'", "1000 calendar ms = 1 UCUM s")
        .testFalse("1000 milliseconds > 1 's'", "1000ms is not > 1s")
        .testTrue("1 second < 2 's'", "Calendar 1 second < 2 UCUM s")
        .build();
  }

  // ===== Calendar non-definite vs UCUM =====

  @TestFactory
  Stream<DynamicTest> testCalendarNonDefiniteVsUcum() {
    return builder()
        .group("Calendar non-definite vs UCUM → empty")
        .testEmpty("1 year = 1 'a'", "Calendar year vs UCUM year")
        .testEmpty("1 month = 1 'mo'", "Calendar month vs UCUM month")
        .testEmpty("1 day = 1 'd'", "Calendar day vs UCUM day")
        .testEmpty("1 hour = 1 'h'", "Calendar hour vs UCUM hour")
        .testEmpty("1 minute = 1 'min'", "Calendar minute vs UCUM minute")
        .build();
  }

  // ===== Different system, same code =====

  @TestFactory
  Stream<DynamicTest> testDifferentSystemSameCode() {
    return builder()
        .group("Different system, same code")
        .testEmpty(
            "1 'year' = 1 year", "UCUM 'year' vs calendar year (same code, different system)")
        .build();
  }

  // ===== Comparison operators with UCUM conversion =====

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
  Stream<DynamicTest> testComparisonCrossUnit() {
    return builder()
        .group("Comparison cross-unit (same dimension)")
        .testFalse("20 'mg' > 10 'kg'", "20mg is not > 10kg")
        .testTrue("10 'mg' < 20 'kg'", "10mg < 20kg")
        .testFalse("10 'mg' >= 20 'kg'", "10mg is not >= 20kg")
        .testTrue("10 'mg' <= 20 'kg'", "10mg <= 20kg")
        .testTrue("2000 'g' > 1 'kg'", "2000g > 1kg")
        .testTrue("100 'cm' >= 1 'm'", "100cm >= 1m")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testComparisonDifferentDimensionReturnsEmpty() {
    return builder()
        .group("Comparison different dimension returns empty")
        .testEmpty("20 'mg' > 10 's'", "Mass vs time → empty")
        .testEmpty("10 'cm' < 20 'g'", "Length vs mass → empty")
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
