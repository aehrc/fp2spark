package com.example.fhirpath.ir.math;

import com.example.fhirpath.ir.IRNodeTestBase;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Unit tests for the Exp IRNode based on FHIRPath specification.
 *
 * Spec: exp() : Decimal
 * - Returns e raised to the power of the input
 * - If input collection contains an Integer, it will be implicitly converted to Decimal
 * - If input collection is empty, the result is empty
 */
public class ExpTest extends IRNodeTestBase {

    /**
     * Test cases directly from the FHIRPath specification plus critical edge cases.
     */
    static Stream<Arguments> expTestCases() {
        return Stream.of(
                // Examples from FHIRPath spec
                Arguments.of(0.0, 1.0, "spec: 0.exp() returns 1.0"),
                Arguments.of(-0.0, 1.0, "spec: (-0.0).exp() returns 1.0"),

                // Critical edge cases
                Arguments.of(1.0, Math.E, "edge: exp(1) returns e"),
                Arguments.of(-1.0, 1.0 / Math.E, "edge: exp(-1) returns 1/e"),
                Arguments.of(2.0, Math.E * Math.E, "edge: exp(2) returns e^2")
        );
    }

    @ParameterizedTest(name = "[{index}] {2}")
    @MethodSource("expTestCases")
    void testExp(Double input, Double expected, String description) {
        Exp exp = new Exp(decimal(input));

        Object result = evaluateIRNode(exp);
        Double actual = ((Number) result).doubleValue();

        assertEquals(expected, actual, 1e-10, description);
    }

    /**
     * Test null input behavior (spec: empty collection returns empty).
     */
    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("nullInputTestCases")
    void testExpWithNullInput(String description) {
        Exp exp = new Exp(decimal(null));

        String actual = evaluateAsString(exp);

        assertNull(actual, description);
    }

    static Stream<Arguments> nullInputTestCases() {
        return Stream.of(
                Arguments.of("spec: null input returns empty")
        );
    }
}
