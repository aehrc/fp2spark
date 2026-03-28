package com.example.fhirpath;

import com.example.fhirpath.test.FhirPathTestBase;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Tests for FHIRPath math functions.
 *
 * <p>Based on FHIRPath specification section 5.7.3 (Math).
 *
 * <p>Covers:
 *
 * <ul>
 *   <li>abs, ceiling, floor, truncate, round
 *   <li>exp, ln, log, power, sqrt
 *   <li>Empty collection propagation for all functions
 *   <li>Edge cases from spec examples
 * </ul>
 */
public class MathFunctionsTest extends FhirPathTestBase {

  // ---------------------------------------------------------------------------
  // abs()
  // ---------------------------------------------------------------------------

  @TestFactory
  Stream<DynamicTest> testAbs() {
    return builder()
        .group("abs() spec examples")
        .testEquals(5, "(-5).abs()")
        .testEquals(5.5, "(-5.5).abs()")
        .group("abs() core semantics")
        .testEquals(5, "5.abs()", "Positive integer unchanged")
        .testEquals(0, "0.abs()", "Zero unchanged")
        .testEquals(5.5, "5.5.abs()", "Positive decimal unchanged")
        .testEquals(0.0, "0.0.abs()", "Decimal zero unchanged")
        .group("abs() empty propagation")
        .testEmpty("{}.abs()")
        .build();
  }

  // ---------------------------------------------------------------------------
  // ceiling()
  // ---------------------------------------------------------------------------

  @TestFactory
  Stream<DynamicTest> testCeiling() {
    return builder()
        .group("ceiling() spec examples")
        .testEquals(1, "1.ceiling()")
        .testEquals(2, "1.1.ceiling()")
        .testEquals(-1, "(-1.1).ceiling()")
        .group("ceiling() core semantics")
        .testEquals(2, "1.8.ceiling()")
        .testEquals(1, "1.0.ceiling()", "Whole decimal")
        .testEquals(0, "(-0.5).ceiling()")
        .group("ceiling() empty propagation")
        .testEmpty("{}.ceiling()")
        .build();
  }

  // ---------------------------------------------------------------------------
  // floor()
  // ---------------------------------------------------------------------------

  @TestFactory
  Stream<DynamicTest> testFloor() {
    return builder()
        .group("floor() spec examples")
        .testEquals(1, "1.floor()")
        .testEquals(2, "2.1.floor()")
        .testEquals(-3, "(-2.1).floor()")
        .group("floor() core semantics")
        .testEquals(1, "1.8.floor()")
        .testEquals(1, "1.0.floor()", "Whole decimal")
        .testEquals(-1, "(-0.5).floor()")
        .group("floor() empty propagation")
        .testEmpty("{}.floor()")
        .build();
  }

  // ---------------------------------------------------------------------------
  // truncate()
  // ---------------------------------------------------------------------------

  @TestFactory
  Stream<DynamicTest> testTruncate() {
    return builder()
        .group("truncate() spec examples")
        .testEquals(101, "101.truncate()")
        .testEquals(1, "1.00000001.truncate()")
        .testEquals(-1, "(-1.56).truncate()")
        .group("truncate() core semantics")
        .testEquals(0, "0.9.truncate()", "Less than 1")
        .testEquals(0, "(-0.9).truncate()", "Negative less than 1")
        .group("truncate() empty propagation")
        .testEmpty("{}.truncate()")
        .build();
  }

  // ---------------------------------------------------------------------------
  // round()
  // ---------------------------------------------------------------------------

  @TestFactory
  Stream<DynamicTest> testRound() {
    return builder()
        .group("round() spec examples")
        .testEquals(1.0, "1.round()")
        .testEquals(3.142, "3.14159.round(3)")
        .group("round() core semantics")
        .testEquals(2.0, "1.5.round()", "Half rounds up")
        .testEquals(2.0, "2.4.round()", "Below half rounds down")
        .testEquals(-2.0, "(-1.5).round()", "Negative half")
        .testEquals(1.23, "1.234.round(2)", "Two decimal places")
        .group("round() empty propagation")
        .testEmpty("{}.round()")
        .testEmpty("{}.round(2)")
        .build();
  }

  // ---------------------------------------------------------------------------
  // exp()
  // ---------------------------------------------------------------------------

  @TestFactory
  Stream<DynamicTest> testExp() {
    return builder()
        .group("exp() spec examples")
        .testEquals(1.0, "0.exp()")
        .testEquals(1.0, "(-0.0).exp()")
        .group("exp() core semantics")
        .testTrue("1.exp() > 2.718 and 1.exp() < 2.719", "e^1 ≈ 2.718")
        .group("exp() empty propagation")
        .testEmpty("{}.exp()")
        .build();
  }

  // ---------------------------------------------------------------------------
  // ln()
  // ---------------------------------------------------------------------------

  @TestFactory
  Stream<DynamicTest> testLn() {
    return builder()
        .group("ln() spec examples")
        .testEquals(0.0, "1.ln()")
        .testEquals(0.0, "1.0.ln()")
        .group("ln() core semantics")
        .testTrue("2.ln() > 0.693 and 2.ln() < 0.694", "ln(2) ≈ 0.693")
        .group("ln() empty propagation")
        .testEmpty("{}.ln()")
        .build();
  }

  // ---------------------------------------------------------------------------
  // log()
  // ---------------------------------------------------------------------------

  @TestFactory
  Stream<DynamicTest> testLog() {
    return builder()
        .group("log() spec examples")
        .testEquals(4.0, "16.log(2)")
        .testEquals(2.0, "100.0.log(10.0)")
        .group("log() core semantics")
        .testEquals(3.0, "8.log(2)", "2^3 = 8")
        .testEquals(1.0, "10.log(10)", "log base 10 of 10")
        .group("log() empty propagation")
        .testEmpty("{}.log(2)")
        .build();
  }

  // ---------------------------------------------------------------------------
  // power()
  // ---------------------------------------------------------------------------

  @TestFactory
  Stream<DynamicTest> testPower() {
    return builder()
        .group("power() spec examples")
        .testEquals(8, "2.power(3)")
        .testEquals(6.25, "2.5.power(2)")
        .testEmpty("(-1).power(0.5)", "Unrepresentable result → empty")
        .group("power() core semantics")
        .testEquals(1, "5.power(0)", "Any number to power 0")
        .testEquals(1.0, "5.0.power(0)", "Decimal to power 0")
        .testEquals(4.0, "2.power(2.0)", "Integer base, decimal exponent")
        .group("power() empty propagation")
        .testEmpty("{}.power(2)")
        .build();
  }

  // ---------------------------------------------------------------------------
  // sqrt()
  // ---------------------------------------------------------------------------

  @TestFactory
  Stream<DynamicTest> testSqrt() {
    return builder()
        .group("sqrt() spec examples")
        .testEquals(9.0, "81.sqrt()")
        .testEmpty("(-1).sqrt()", "Negative → empty")
        .group("sqrt() core semantics")
        .testEquals(0.0, "0.sqrt()", "sqrt(0) = 0")
        .testEquals(2.0, "4.0.sqrt()", "Decimal input")
        .testEquals(1.0, "1.sqrt()", "sqrt(1) = 1")
        .group("sqrt() empty propagation")
        .testEmpty("{}.sqrt()")
        .build();
  }
}
