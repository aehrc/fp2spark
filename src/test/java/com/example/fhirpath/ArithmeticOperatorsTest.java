package com.example.fhirpath;

import com.example.fhirpath.operation.OverloadResolutionException;
import com.example.fhirpath.test.FhirPathTestBase;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Tests for FHIRPath arithmetic operators.
 *
 * <p>Based on FHIRPath specification section 6.2 (Math):
 *
 * <ul>
 *   <li>Addition (+): Integer, Decimal, String
 *   <li>Subtraction (-): Integer, Decimal
 *   <li>Multiplication (*): Integer, Decimal
 *   <li>Division (/): Integer, Decimal — always returns Decimal
 *   <li>Integer division (div): Integer, Decimal — truncated division
 *   <li>Modulo (mod): Integer, Decimal
 *   <li>String concatenation (&): treats empty as ""
 *   <li>Unary plus (+) and minus (-): Integer, Decimal
 *   <li>Division by zero returns empty for /, div, mod
 *   <li>Empty collection propagation
 * </ul>
 */
class ArithmeticOperatorsTest extends FhirPathTestBase {

  // ========== Addition (+) ==========

  @TestFactory
  Stream<DynamicTest> testIntegerAddition() {
    return builder()
        .group("Integer addition")
        .testEquals(15, "5 + 10")
        .testEquals(0, "0 + 0")
        .testEquals(-3, "-5 + 2", "Negative + positive")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testDecimalAddition() {
    return builder()
        .group("Decimal addition")
        .testEquals(15.3, "5.1 + 10.2")
        .testEquals(0.0, "0.0 + 0.0")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testMixedAddition() {
    return builder()
        .group("Mixed type addition")
        .testEquals(15.5, "5 + 10.5", "Integer + Decimal")
        .testEquals(15.5, "10.5 + 5", "Decimal + Integer")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testStringAddition() {
    return builder()
        .group("String addition")
        .testEquals("ABCDEF", "'ABC' + 'DEF'", "Spec example")
        .testEquals("", "'' + ''", "Empty strings")
        .build();
  }

  // ========== Subtraction (-) ==========

  @TestFactory
  Stream<DynamicTest> testIntegerSubtraction() {
    return builder()
        .group("Integer subtraction")
        .testEquals(5, "10 - 5")
        .testEquals(-5, "5 - 10")
        .testEquals(0, "5 - 5")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testDecimalSubtraction() {
    return builder()
        .group("Decimal subtraction")
        .testEquals(5.0, "10.5 - 5.5")
        .testEquals(-5.0, "5.5 - 10.5")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testMixedSubtraction() {
    return builder()
        .group("Mixed type subtraction")
        .testEquals(5.5, "10.5 - 5", "Decimal - Integer")
        .testEquals(-5.5, "5 - 10.5", "Integer - Decimal")
        .build();
  }

  // ========== Multiplication (*) ==========

  @TestFactory
  Stream<DynamicTest> testIntegerMultiplication() {
    return builder()
        .group("Integer multiplication")
        .testEquals(50, "5 * 10")
        .testEquals(0, "5 * 0")
        .testEquals(-15, "5 * (-3)")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testDecimalMultiplication() {
    return builder()
        .group("Decimal multiplication")
        .testEquals(6.25, "2.5 * 2.5")
        .testEquals(0.0, "2.5 * 0.0")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testMixedMultiplication() {
    return builder()
        .group("Mixed type multiplication")
        .testEquals(12.5, "5 * 2.5", "Integer * Decimal")
        .testEquals(12.5, "2.5 * 5", "Decimal * Integer")
        .build();
  }

  // ========== Division (/) ==========

  @TestFactory
  Stream<DynamicTest> testDivision() {
    return builder()
        .group("Division")
        .testEquals(2.5, "10 / 4", "Integer / Integer returns Decimal (spec)")
        .testEquals(2.5, "5.0 / 2.0", "Decimal / Decimal")
        .testEquals(2.5, "10 / 4.0", "Integer / Decimal")
        .testEquals(2.6, "10.4 / 4", "Decimal / Integer")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testDivisionByZero() {
    return builder()
        .group("Division by zero")
        .testEmpty("12 / 0", "Spec example: 12 / 0 = empty")
        .testEmpty("10.0 / 0.0", "Decimal division by zero")
        .testEmpty("0 / 0", "Zero / zero")
        .build();
  }

  // ========== Integer division (div) ==========

  @TestFactory
  Stream<DynamicTest> testIntegerDivision() {
    return builder()
        .group("Integer division (div)")
        .testEquals(2, "5 div 2", "Spec example")
        .testEquals(0, "1 div 3", "Smaller / larger")
        .testEquals(3, "10 div 3")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testDecimalIntegerDivision() {
    return builder()
        .group("Decimal integer division (div)")
        .testEquals(7.0, "5.5 div 0.7", "Spec example: 5.5 div 0.7 = 7")
        .testEquals(2.0, "5.0 div 2.0")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testNegativeIntegerDivision() {
    return builder()
        .group("Negative integer division (div)")
        .testEquals(-3, "(-7) div 2", "Truncation toward zero, not floor")
        .testEquals(-3, "7 div (-2)", "Negative divisor")
        .testEquals(3, "(-7) div (-2)", "Both negative")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testDivByZero() {
    return builder()
        .group("div by zero")
        .testEmpty("5 div 0", "Spec example: 5 div 0 = empty")
        .testEmpty("5.0 div 0.0", "Decimal div by zero")
        .build();
  }

  // ========== Modulo (mod) ==========

  @TestFactory
  Stream<DynamicTest> testIntegerModulo() {
    return builder()
        .group("Integer modulo")
        .testEquals(1, "5 mod 2", "Spec example")
        .testEquals(0, "10 mod 5", "Even division")
        .testEquals(1, "10 mod 3")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testDecimalModulo() {
    return builder()
        .group("Decimal modulo")
        .testEquals(0.6, "5.5 mod 0.7", "Spec example: 5.5 mod 0.7 = 0.6")
        .testEquals(0.0, "4.0 mod 2.0")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testNegativeModulo() {
    return builder()
        .group("Negative modulo")
        .testEquals(-1, "(-7) mod 2", "Negative dividend")
        .testEquals(1, "7 mod (-2)", "Negative divisor")
        .testEquals(-1, "(-7) mod (-2)", "Both negative")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testModByZero() {
    return builder()
        .group("mod by zero")
        .testEmpty("5 mod 0", "Spec example: 5 mod 0 = empty")
        .testEmpty("5.0 mod 0.0", "Decimal mod by zero")
        .build();
  }

  // ========== String concatenation (&) ==========

  @TestFactory
  Stream<DynamicTest> testStringConcat() {
    return builder()
        .group("String concatenation (&)")
        .testEquals("ABCDEF", "'ABC' & 'DEF'", "Spec example")
        .testEquals("", "'' & ''", "Empty strings")
        .testEquals("ABC", "'ABC' & ''", "Right empty string")
        .testEquals("DEF", "'' & 'DEF'", "Left empty string")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testStringConcatWithEmpty() {
    return builder()
        .group("String concat with empty collection")
        .testEquals("ABCDEF", "'ABC' & {} & 'DEF'", "Spec example: empty treated as ''")
        .testEquals("ABC", "'ABC' & {}", "Right operand empty")
        .testEquals("DEF", "{} & 'DEF'", "Left operand empty")
        .testEquals("", "{} & {}", "Both operands empty")
        .build();
  }

  // ========== Unary plus and minus ==========

  @TestFactory
  Stream<DynamicTest> testUnaryPlus() {
    return builder()
        .group("Unary plus")
        .testEquals(5, "+5", "Integer")
        .testEquals(5.5, "+5.5", "Decimal")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testUnaryMinus() {
    return builder()
        .group("Unary minus")
        .testEquals(-5, "-5", "Integer")
        .testEquals(-5.5, "-5.5", "Decimal")
        .testEquals(5, "-(-5)", "Double negation")
        .build();
  }

  // ========== Empty collection propagation ==========

  @TestFactory
  Stream<DynamicTest> testEmptyPropagation() {
    return builder()
        .group("Empty collection propagation")
        .testEmpty("{} + 5", "Empty + Integer")
        .testEmpty("5 + {}", "Integer + Empty")
        .testEmpty("{} - 5", "Empty - Integer")
        .testEmpty("{} * 5", "Empty * Integer")
        .testEmpty("{} / 5", "Empty / Integer")
        .testEmpty("{} div 5", "Empty div Integer")
        .testEmpty("{} mod 5", "Empty mod Integer")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testStringAdditionWithEmpty() {
    return builder()
        .group("String + with empty")
        .testEmpty("'ABC' + {}", "Spec example: 'ABC' + {} = empty")
        .testEmpty("{} + 'DEF'", "Empty + string = empty")
        .build();
  }

  // ========== Type errors ==========

  @TestFactory
  Stream<DynamicTest> testIncompatibleTypes() {
    return builder()
        .group("Incompatible type errors")
        .testError(OverloadResolutionException.class, "5 + true", "Integer + Boolean")
        .testError(OverloadResolutionException.class, "'abc' - 'def'", "String subtraction")
        .testError(OverloadResolutionException.class, "'abc' * 'def'", "String multiplication")
        .testError(OverloadResolutionException.class, "'abc' / 'def'", "String division")
        .testError(OverloadResolutionException.class, "'abc' div 'def'", "String div")
        .testError(OverloadResolutionException.class, "'abc' mod 'def'", "String mod")
        .build();
  }

  // ========== Resource field arithmetic ==========

  @TestFactory
  Stream<DynamicTest> testArithmeticOnResourceFields() {
    return builder()
        .group("Arithmetic on resource fields")
        .withSubject("Patient", p -> p.integer("age", 55))
        .testEquals(65, "age + 10")
        .testEquals(65.3, "10.3 + age")
        .build();
  }

  // ========== Compound expressions ==========

  @TestFactory
  Stream<DynamicTest> testCompoundExpressions() {
    return builder()
        .group("Compound arithmetic")
        .testEquals(17, "5 + 3 * 4", "Precedence: * before +")
        .testEquals(32, "(5 + 3) * 4", "Parenthesized")
        .testTrue("10 / 4 = 2.5", "Division result in comparison")
        .testTrue("5 mod 2 = 1", "Mod result in comparison")
        .testTrue("5 div 2 = 2", "Div result in comparison")
        .build();
  }
}
