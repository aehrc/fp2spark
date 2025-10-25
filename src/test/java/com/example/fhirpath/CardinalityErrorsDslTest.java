package com.example.fhirpath;

import com.example.fhirpath.analyzer.CardinalityMismatchException;
import com.example.fhirpath.test.FhirPathTestBase;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.stream.Stream;

/**
 * Tests for cardinality constraint violations using the DSL framework.
 *
 * <p>Per FHIRPath specification:
 * <ul>
 *   <li>Section 3559-3566: Math operators require each operand to be a single element.</li>
 *   <li>Section 3196-3197: Comparison operators require single-valued collections.</li>
 * </ul>
 *
 * <p>These tests verify that expressions like {@code (1 ; 2) + 2} are rejected
 * at analysis time with {@link CardinalityMismatchException}.
 */
public class CardinalityErrorsDslTest extends FhirPathTestBase {

    @TestFactory
    Stream<DynamicTest> testMathOperatorsLeftOperandMany() {
        return builder()
                .group("Math operators - left operand MANY")
                .testError(CardinalityMismatchException.class, "(1 ; 2) + 2", "Addition with MANY left operand")
                .testError(CardinalityMismatchException.class, "(1 ; 2) - 3", "Subtraction with MANY left operand")
                .testError(CardinalityMismatchException.class, "(1 ; 2) * 3", "Multiplication with MANY left operand")
                .testError(CardinalityMismatchException.class, "(5 ; 10) / 2", "Division with MANY left operand")
                .testError(CardinalityMismatchException.class, "(1 ; 2 ; 3) mod 2", "Modulo with MANY left operand")
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testMathOperatorsRightOperandMany() {
        return builder()
                .group("Math operators - right operand MANY")
                .testError(CardinalityMismatchException.class, "2 + (1 ; 2)", "Addition with MANY right operand")
                .testError(CardinalityMismatchException.class, "10 - (1 ; 2)", "Subtraction with MANY right operand")
                .testError(CardinalityMismatchException.class, "5 * (2 ; 3)", "Multiplication with MANY right operand")
                .testError(CardinalityMismatchException.class, "10 / (2 ; 5)", "Division with MANY right operand")
                .testError(CardinalityMismatchException.class, "10 mod (2 ; 3)", "Modulo with MANY right operand")
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testMathOperatorsBothOperandsMany() {
        return builder()
                .group("Math operators - both operands MANY")
                .testError(CardinalityMismatchException.class, "(1 ; 2) + (3 ; 4)", "Addition with both MANY")
                .testError(CardinalityMismatchException.class, "(1 ; 2) - (3 ; 4)", "Subtraction with both MANY")
                .testError(CardinalityMismatchException.class, "(1 ; 2) * (3 ; 4)", "Multiplication with both MANY")
                .testError(CardinalityMismatchException.class, "(10 ; 20) / (2 ; 5)", "Division with both MANY")
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testComparisonOperatorsLeftOperandMany() {
        return builder()
                .group("Comparison operators - left operand MANY")
                .testError(CardinalityMismatchException.class, "(1 ; 2) > 5", "Greater than with MANY left operand")
                .testError(CardinalityMismatchException.class, "(1 ; 2) < 5", "Less than with MANY left operand")
                .testError(CardinalityMismatchException.class, "(1 ; 2) >= 5", "Greater or equal with MANY left operand")
                .testError(CardinalityMismatchException.class, "(1 ; 2) <= 5", "Less or equal with MANY left operand")
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testComparisonOperatorsRightOperandMany() {
        return builder()
                .group("Comparison operators - right operand MANY")
                .testError(CardinalityMismatchException.class, "5 > (1 ; 2)", "Greater than with MANY right operand")
                .testError(CardinalityMismatchException.class, "5 < (1 ; 2)", "Less than with MANY right operand")
                .testError(CardinalityMismatchException.class, "5 >= (1 ; 2)", "Greater or equal with MANY right operand")
                .testError(CardinalityMismatchException.class, "5 <= (1 ; 2)", "Less or equal with MANY right operand")
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testComparisonOperatorsBothOperandsMany() {
        return builder()
                .group("Comparison operators - both operands MANY")
                .testError(CardinalityMismatchException.class, "(1 ; 2) > (3 ; 4)", "Greater than with both MANY")
                .testError(CardinalityMismatchException.class, "(1 ; 2) < (3 ; 4)", "Less than with both MANY")
                .testError(CardinalityMismatchException.class, "(1 ; 2) >= (3 ; 4)", "Greater or equal with both MANY")
                .testError(CardinalityMismatchException.class, "(1 ; 2) <= (3 ; 4)", "Less or equal with both MANY")
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testStringOperationsWithMany() {
        return builder()
                .group("String operations with MANY cardinality")
                .testError(CardinalityMismatchException.class, "('a' ; 'b') + 'c'", "String concat with MANY left")
                .testError(CardinalityMismatchException.class, "'prefix' + ('a' ; 'b')", "String concat with MANY right")
                .testError(CardinalityMismatchException.class, "('hello' ; 'world') + ('foo' ; 'bar')", "String concat with both MANY")
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testNestedConcatenations() {
        return builder()
                .group("Nested concatenations with MANY")
                .testError(CardinalityMismatchException.class, "((1 ; 2) ; 3) + 4", "Nested concatenation left + single")
                .testError(CardinalityMismatchException.class, "(1 ; (2 ; 3)) > 0", "Nested concatenation comparison")
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testValidCardinalityUsages() {
        return builder()
                .group("Valid: Single + Single math operations")
                .testEquals(3, "1 + 2", "Addition")
                .testEquals(2, "5 - 3", "Subtraction")
                .testEquals(8, "4 * 2", "Multiplication")
                .testEquals(5.0, "10 / 2", "Division (returns decimal)")
                .testEquals(1, "10 mod 3", "Modulo")
                .group("Valid: Comparisons with SINGLE operands")
                .testFalse("1 > 2", "Greater than")
                .testTrue("5 < 10", "Less than")
                .testTrue("3 >= 3", "Greater or equal")
                .testTrue("2 <= 5", "Less or equal")
                .group("Valid: String concatenation with SINGLE operands")
                .testEquals("hello world", "'hello' + ' world'", "String concatenation")
                .testEquals("ab", "'a' + 'b'", "Single char concatenation")
                .group("Valid: Collection operations that accept MANY")
                .testEquals(2, "(1 ; 2).count()", "count() on collection")
                .testEquals(1, "(1 ; 2).first()", "first() on collection")
                .testEquals(java.util.List.of(2, 3), "(1 ; 2 ; 3).where($this > 1)", "where() on collection")
                .testTrue("(1 ; 2).exists()", "exists() on collection")
                .testFalse("(1 ; 2).empty()", "empty() on collection")
                .group("Valid: Empty collections (handled at runtime)")
                .testEmpty("{} + 2", "Empty + single")
                .testEmpty("2 + {}", "Single + empty")
                .testEmpty("{} > 5", "Empty comparison")
                .build();
    }
}
