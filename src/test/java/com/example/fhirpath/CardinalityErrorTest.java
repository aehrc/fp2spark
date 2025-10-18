package com.example.fhirpath;

import com.example.fhirpath.analyzer.CardinalityMismatchException;
import com.example.fhirpath.typing.Cardinality;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that cardinality violations are properly detected and reported.
 *
 * <p>Per FHIRPath specification:
 * <ul>
 *   <li>Section 3559-3566: Math operators require each operand to be a single element.
 *   <li>Section 3196-3197: Comparison operators require single-valued collections.
 * </ul>
 *
 * <p>These tests verify that expressions like {@code (1 | 2) + 2} are rejected
 * at analysis time with {@link CardinalityMismatchException}.
 */
class CardinalityErrorTest {

    /**
     * Tests that math and comparison operators reject MANY cardinality arguments.
     */
    @ParameterizedTest(name = "[{index}] {0} should throw CardinalityMismatchException for operation ''{1}''")
    @MethodSource("cardinalityViolations")
    void testCardinalityMismatchThrowsException(String expression, String expectedOperation) {
        CardinalityMismatchException exception = assertThrows(
                CardinalityMismatchException.class,
                () -> FhirPath.toColumn(expression),
                "Expression '" + expression + "' should throw CardinalityMismatchException"
        );

        assertEquals(expectedOperation, exception.getOperationName(),
                "Exception should reference operation '" + expectedOperation + "'");
        assertEquals(Cardinality.SINGLE, exception.getExpected(),
                "Math/comparison operators expect SINGLE cardinality");
        assertEquals(Cardinality.MANY, exception.getActual(),
                "Argument has MANY cardinality");
    }

    /**
     * Test cases for expressions that violate cardinality constraints.
     *
     * <p>Format: (expression, expected operation name)
     */
    static Stream<Arguments> cardinalityViolations() {
        return Stream.of(
                // Math operators - left operand MANY
                Arguments.of("(1 | 2) + 2", "add"),
                Arguments.of("(1 | 2) - 3", "sub"),
                Arguments.of("(1 | 2) * 3", "multiply"),
                Arguments.of("(5 | 10) / 2", "divide"),
                Arguments.of("(1 | 2 | 3) mod 2", "mod"),

                // Math operators - right operand MANY
                Arguments.of("2 + (1 | 2)", "add"),
                Arguments.of("10 - (1 | 2)", "sub"),
                Arguments.of("5 * (2 | 3)", "multiply"),
                Arguments.of("10 / (2 | 5)", "divide"),
                Arguments.of("10 mod (2 | 3)", "mod"),

                // Math operators - both operands MANY
                Arguments.of("(1 | 2) + (3 | 4)", "add"),
                Arguments.of("(1 | 2) - (3 | 4)", "sub"),
                Arguments.of("(1 | 2) * (3 | 4)", "multiply"),
                Arguments.of("(10 | 20) / (2 | 5)", "divide"),

                // Comparison operators - left operand MANY
                Arguments.of("(1 | 2) > 5", "gt"),
                Arguments.of("(1 | 2) < 5", "lt"),
                Arguments.of("(1 | 2) >= 5", "geq"),
                Arguments.of("(1 | 2) <= 5", "leq"),

                // Comparison operators - right operand MANY
                Arguments.of("5 > (1 | 2)", "gt"),
                Arguments.of("5 < (1 | 2)", "lt"),
                Arguments.of("5 >= (1 | 2)", "geq"),
                Arguments.of("5 <= (1 | 2)", "leq"),

                // Comparison operators - both operands MANY
                Arguments.of("(1 | 2) > (3 | 4)", "gt"),
                Arguments.of("(1 | 2) < (3 | 4)", "lt"),
                Arguments.of("(1 | 2) >= (3 | 4)", "geq"),
                Arguments.of("(1 | 2) <= (3 | 4)", "leq"),

                // String operations with MANY cardinality
                Arguments.of("('a' | 'b') + 'c'", "add"),
                Arguments.of("'prefix' + ('a' | 'b')", "add"),
                Arguments.of("('hello' | 'world') + ('foo' | 'bar')", "add"),

                // Nested unions
                Arguments.of("((1 | 2) | 3) + 4", "add"),
                Arguments.of("(1 | (2 | 3)) > 0", "gt")
        );
    }

    /**
     * Tests that valid cardinality usages do NOT throw exceptions.
     */
    @ParameterizedTest(name = "[{index}] {0} should NOT throw (valid cardinality)")
    @MethodSource("validCardinalityUsages")
    void testValidCardinalityDoesNotThrow(String expression) {
        assertDoesNotThrow(
                () -> FhirPath.toColumn(expression),
                "Expression '" + expression + "' should not throw (valid cardinality)"
        );
    }

    /**
     * Test cases for expressions with valid cardinality.
     */
    static Stream<Arguments> validCardinalityUsages() {
        return Stream.of(
                // Single + Single - valid
                Arguments.of("1 + 2"),
                Arguments.of("5 - 3"),
                Arguments.of("4 * 2"),
                Arguments.of("10 / 2"),
                Arguments.of("10 mod 3"),

                // Comparisons with SINGLE operands - valid
                Arguments.of("1 > 2"),
                Arguments.of("5 < 10"),
                Arguments.of("3 >= 3"),
                Arguments.of("2 <= 5"),

                // String concatenation with SINGLE operands - valid
                Arguments.of("'hello' + ' world'"),
                Arguments.of("'a' + 'b'"),

                // Collection operations that accept MANY - valid
                Arguments.of("(1 | 2).count()"),
                Arguments.of("(1 | 2).first()"),
                Arguments.of("(1 | 2 | 3).where($this > 1)"),
                Arguments.of("(1 | 2).exists()"),
                Arguments.of("(1 | 2).empty()"),

                // Note: first() + math would be valid BUT Phase 1 limitation:
                // first() returns ?ANY instead of specific element type, so type checking fails
                // These will work in Phase 2 with type variables
                // Arguments.of("(1 | 2).first() + 3"),  // Phase 2
                // Arguments.of("(1 | 2).first() > 0"),  // Phase 2

                // Empty collections - valid (handled at runtime)
                Arguments.of("{} + 2"),
                Arguments.of("2 + {}"),
                Arguments.of("{} > 5")
        );
    }
}
