package au.csiro.fhirpath;

import au.csiro.fhirpath.test.FhirPathTestBase;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Tests for FHIRPath quantity arithmetic operators (+, -, *, /).
 *
 * <p>Covers same-unit operations, UCUM cross-unit conversion (most granular unit selection), unit
 * algebra for multiplication/division, division by zero, calendar duration arithmetic, and empty
 * propagation.
 */
public class QuantityArithmeticTest extends FhirPathTestBase {

  // ===== Addition: same unit =====

  @TestFactory
  Stream<DynamicTest> testAddSameUnit() {
    return builder()
        .group("Addition same unit")
        .testTrue("10 'mg' + 5 'mg' = 15 'mg'", "Same unit addition")
        .testTrue("1 'cm' + 2 'cm' = 3 'cm'", "Same unit cm addition")
        .testTrue("0 'kg' + 5 'kg' = 5 'kg'", "Zero left operand")
        .testTrue("5 'kg' + 0 'kg' = 5 'kg'", "Zero right operand")
        .build();
  }

  // ===== Addition: cross-unit (most granular) =====

  @TestFactory
  Stream<DynamicTest> testAddCrossUnit() {
    return builder()
        .group("Addition cross-unit (most granular unit)")
        .testTrue("3 'm' + 3 'cm' = 303 'cm'", "m + cm → result in cm (spec example)")
        .testTrue("1 'g' + 500 'mg' = 1500 'mg'", "g + mg → result in mg")
        .testTrue("500 'mg' + 1 'g' = 1500 'mg'", "mg + g → result in mg")
        .build();
  }

  // ===== Addition: incompatible dimensions =====

  @TestFactory
  Stream<DynamicTest> testAddIncompatibleDimensions() {
    return builder()
        .group("Addition incompatible dimensions → empty")
        .testEmpty("10 'cm' + 10 'g'", "Length + mass → empty")
        .testEmpty("1 'kg' + 1 's'", "Mass + time → empty")
        .build();
  }

  // ===== Subtraction: same unit =====

  @TestFactory
  Stream<DynamicTest> testSubSameUnit() {
    return builder()
        .group("Subtraction same unit")
        .testTrue("10 'mg' - 5 'mg' = 5 'mg'", "Same unit subtraction")
        .testTrue("5 'cm' - 2 'cm' = 3 'cm'", "Same unit cm subtraction")
        .build();
  }

  // ===== Subtraction: cross-unit =====

  @TestFactory
  Stream<DynamicTest> testSubCrossUnit() {
    return builder()
        .group("Subtraction cross-unit (most granular unit)")
        .testTrue("3 'm' - 3 'cm' = 297 'cm'", "m - cm → result in cm (spec example)")
        .testTrue("1 'g' - 500 'mg' = 500 'mg'", "g - mg → result in mg")
        .build();
  }

  // ===== Subtraction: incompatible dimensions =====

  @TestFactory
  Stream<DynamicTest> testSubIncompatibleDimensions() {
    return builder()
        .group("Subtraction incompatible dimensions → empty")
        .testEmpty("10 'cm' - 10 'g'", "Length - mass → empty")
        .build();
  }

  // ===== Multiplication =====

  @TestFactory
  Stream<DynamicTest> testMultiply() {
    return builder()
        .group("Multiplication")
        .testTrue("12 'cm' * 3 'cm' = 36 'cm2'", "cm * cm → cm2 (spec example)")
        .testTrue("3 'cm' * 12 'cm2' = 36 'cm3'", "cm * cm2 → cm3 (spec example)")
        .testTrue("5 'kg' * 2 'kg' = 10 'kg2'", "kg * kg → kg2")
        .build();
  }

  // ===== Division =====

  @TestFactory
  Stream<DynamicTest> testDivide() {
    return builder()
        .group("Division")
        .testTrue("12 'cm2' / 3 'cm' = 4.0 'cm'", "cm2 / cm → cm (spec example)")
        .testTrue("36 'cm3' / 12 'cm2' = 3.0 'cm'", "cm3 / cm2 → cm")
        .build();
  }

  // ===== Division by zero =====

  @TestFactory
  Stream<DynamicTest> testDivisionByZero() {
    return builder()
        .group("Division by zero → empty")
        .testEmpty("10 'cm' / 0 'cm'", "Quantity division by zero → empty")
        .build();
  }

  // ===== Empty propagation =====

  @TestFactory
  Stream<DynamicTest> testEmptyPropagation() {
    return builder()
        .group("Empty propagation")
        .testEmpty("{} + 10 'mg'", "Empty left → empty")
        .testEmpty("10 'mg' + {}", "Empty right → empty")
        .testEmpty("{} - 10 'mg'", "Empty left subtraction → empty")
        .testEmpty("{} * 10 'mg'", "Empty left multiplication → empty")
        .testEmpty("{} / 10 'mg'", "Empty left division → empty")
        .build();
  }

  // ===== Calendar duration arithmetic =====

  @TestFactory
  Stream<DynamicTest> testCalendarDefiniteDuration() {
    return builder()
        .group("Calendar definite duration arithmetic")
        .testTrue("1 second + 1 second = 2 seconds", "Calendar second addition")
        .testTrue(
            "500 milliseconds + 500 milliseconds = 1000 milliseconds",
            "Calendar millisecond addition")
        .build();
  }

  // ===== Calendar non-definite durations =====

  @TestFactory
  Stream<DynamicTest> testCalendarNonDefiniteDuration() {
    return builder()
        .group("Calendar non-definite durations → empty")
        .testEmpty("1 year + 1 year", "Year addition not supported")
        .testEmpty("1 month + 1 month", "Month addition not supported")
        .build();
  }

  // ===== Special UCUM units (non-linear: bel, decibel, neper, pH) =====

  @TestFactory
  Stream<DynamicTest> testSpecialUcumUnits() {
    return builder()
        .group("Arithmetic with special UCUM units → empty (issue #157)")
        // 'B' (bel) — the canonical example from the spec/issue
        .testEmpty("1 'B' * 2", "Multiplying bel by scalar → empty")
        .testEmpty("1 'B' + 1 'B'", "Adding two bels → empty")
        .testEmpty("1 'B' - 1 'B'", "Subtracting two bels → empty")
        .testEmpty("1 'B' / 2", "Dividing bel by scalar → empty")
        // Other non-linear UCUM units
        .testEmpty("1 'dB' + 1 'dB'", "Adding two decibels → empty")
        .testEmpty("1 'Np' * 2", "Neper (natural log unit) × scalar → empty")
        .testEmpty("1 '[pH]' + 1 '[pH]'", "pH (negative log concentration) → empty")
        // Mixed: special on either side poisons the operation
        .testEmpty("1 'B' + 1 'dB'", "bel + decibel → empty")
        .testEmpty("5 'kg' + 1 'B'", "Mass + bel → empty (incompatible anyway)")
        .build();
  }

  // ===== Unary minus (issue #187) =====

  @TestFactory
  Stream<DynamicTest> testUnaryMinus() {
    return builder()
        .group("Unary minus on Quantity (issue #187)")
        .testTrue("(-5.5 'mg').abs() = 5.5 'mg'", "Spec §5.7 example: (-5.5 'mg').abs()")
        .testTrue("-(1 'mg') = -1 'mg'", "Negation of positive quantity literal")
        .testTrue("-(-1 'mg') = 1 'mg'", "Double negation")
        .testTrue("(-0 'mg') = 0 'mg'", "Negation of zero preserves unit")
        .testTrue("-(5 'kg') < 0 'kg'", "Negated positive quantity compares less than zero")
        .testTrue(
            "(1 year).toQuantity('seconds') - 1 'a' = -21600 seconds",
            "Negative quantity literal in subtraction (6.6_math compat)")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testUnaryPlusQuantity() {
    return builder()
        .group("Unary plus on Quantity (identity)")
        .testTrue("+(5 'mg') = 5 'mg'", "Unary plus is identity for Quantity")
        .testTrue("+(-5 'mg') = -5 'mg'", "Unary plus preserves negative Quantity")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testUnaryMinusEmptyPropagation() {
    return builder()
        .group("Unary minus on Quantity — empty propagation")
        // Incompatible-dimension subtraction produces a null Quantity; negating it must stay null.
        .testEmpty("-(10 'cm' - 10 'g')", "Unary minus on empty Quantity → empty")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testUnaryMinusPreservesSpecialUnits() {
    return builder()
        .group("Unary minus on special UCUM units — negation is linear, not arithmetic")
        .testTrue("(-1 'B') = -1 'B'", "Negation of bel preserves unit")
        .testTrue("(-1 '[pH]') = -1 '[pH]'", "Negation of pH preserves unit")
        .build();
  }

  // ===== Regression guard: non-special units still work =====

  @TestFactory
  Stream<DynamicTest> testNonSpecialUnitsRegression() {
    return builder()
        .group("Non-special UCUM units still perform arithmetic (regression guard)")
        .testTrue("1 'kg' * 2 'kg' = 2 'kg2'", "kg × kg still works after special-unit check")
        .testTrue("2 'mg' + 3 'mg' = 5 'mg'", "mg + mg still works")
        .testTrue("10 'cm' / 2 'cm' = 5.0 '1'", "cm ÷ cm still works")
        .build();
  }
}
