package com.example.fhirpath;

import com.example.fhirpath.test.FhirPathTestBase;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.stream.Stream;

/**
 * Comprehensive tests for FHIRPath Boolean logic operators.
 *
 * <p>Tests all boolean operators with three-valued logic as specified in the FHIRPath specification.
 *
 * <p>Based on FHIRPath spec section 6.5: Boolean logic
 *
 * <p>Covers:
 * - All spec truth tables for and, or, not, xor, implies
 * - Three-valued logic (true, false, empty)
 * - Edge cases with empty collections
 */
public class BooleanOperatorsTest extends FhirPathTestBase {

    // ========== and operator ==========

    @TestFactory
    Stream<DynamicTest> testAndOperator() {
        return builder()
                .group("and operator - truth table")
                // true and X
                .testTrue("true and true", "Both true")
                .testFalse("true and false", "True and false")
                .testEmpty("true and {}", "True and empty")
                // false and X
                .testFalse("false and true", "False and true")
                .testFalse("false and false", "Both false")
                .testFalse("false and {}", "False and empty")
                // empty and X
                .testEmpty("{} and true", "Empty and true")
                .testFalse("{} and false", "Empty and false")
                .testEmpty("{} and {}", "Both empty")
                .build();
    }

    // ========== or operator ==========

    @TestFactory
    Stream<DynamicTest> testOrOperator() {
        return builder()
                .group("or operator - truth table")
                // true or X
                .testTrue("true or true", "Both true")
                .testTrue("true or false", "True or false")
                .testTrue("true or {}", "True or empty")
                // false or X
                .testTrue("false or true", "False or true")
                .testFalse("false or false", "Both false")
                .testEmpty("false or {}", "False or empty")
                // empty or X
                .testTrue("{} or true", "Empty or true")
                .testEmpty("{} or false", "Empty or false")
                .testEmpty("{} or {}", "Both empty")
                .build();
    }

    // ========== not() function ==========

    @TestFactory
    Stream<DynamicTest> testNotFunction() {
        return builder()
                .group("not() function - truth table")
                .testFalse("true.not()", "Not true")
                .testTrue("false.not()", "Not false")
                .testEmpty("{}.not()", "Not empty")
                .build();
    }

    // ========== xor operator ==========

    @TestFactory
    Stream<DynamicTest> testXorOperator() {
        return builder()
                .group("xor operator - truth table")
                // true xor X
                .testFalse("true xor true", "Both true - not exclusive")
                .testTrue("true xor false", "True xor false - exclusive")
                .testEmpty("true xor {}", "True xor empty")
                // false xor X
                .testTrue("false xor true", "False xor true - exclusive")
                .testFalse("false xor false", "Both false - not exclusive")
                .testEmpty("false xor {}", "False xor empty")
                // empty xor X
                .testEmpty("{} xor true", "Empty xor true")
                .testEmpty("{} xor false", "Empty xor false")
                .testEmpty("{} xor {}", "Both empty")
                .build();
    }

    // ========== implies operator ==========

    @TestFactory
    Stream<DynamicTest> testImpliesOperator() {
        return builder()
                .group("implies operator - truth table")
                // true implies X
                .testTrue("true implies true", "True implies true - valid implication")
                .testFalse("true implies false", "True implies false - invalid implication")
                .testEmpty("true implies {}", "True implies empty")
                // false implies X
                .testTrue("false implies true", "False implies true - vacuously true")
                .testTrue("false implies false", "False implies false - vacuously true")
                .testTrue("false implies {}", "False implies empty - vacuously true")
                // empty implies X
                .testTrue("{} implies true", "Empty implies true")
                .testEmpty("{} implies false", "Empty implies false")
                .testEmpty("{} implies {}", "Empty implies empty")
                .build();
    }

    // ========== Complex expressions ==========

    @TestFactory
    Stream<DynamicTest> testComplexBooleanExpressions() {
        return builder()
                .group("Complex boolean expressions")
                .testTrue("(true and true) or false", "Compound with and/or")
                .testFalse("true and (false or false)", "Nested or in and")
                .testTrue("(false or true) and true", "Or followed by and")
                .testFalse("true and false and true", "Multiple and operators")
                .testTrue("false or false or true", "Multiple or operators")
                .testTrue("(true xor false) and true", "Xor in compound expression")
                .testTrue("false implies (true or false)", "Implies with or - vacuously true")
                .testFalse("true implies (true and false)", "Implies with and - evaluates right side")
                .build();
    }

    // ========== Empty propagation ==========

    @TestFactory
    Stream<DynamicTest> testEmptyPropagation() {
        return builder()
                .group("Empty collection propagation")
                .testEmpty("(true and {}) or {}", "Empty or empty = empty")
                .testEmpty("(true and {}) or false", "Empty or false = empty (per spec)")
                .testEmpty("({} xor {}) and true", "Empty and true = empty")
                .testTrue("{} implies true", "Empty implies true = true (per spec)")
                .testEmpty("{} implies {}", "Empty implies empty = empty")
                .build();
    }
}
