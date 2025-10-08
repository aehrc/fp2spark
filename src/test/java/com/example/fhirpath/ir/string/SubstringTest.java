package com.example.fhirpath.ir.string;

import com.example.fhirpath.ir.IRNodeTestBase;
import com.example.fhirpath.typing.Type;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for the Substring IRNode based on FHIRPath specification.
 *
 * Spec: substring(start : Integer [, length : Integer]) : String
 * - Returns string starting at position start (zero-based)
 * - If length is given, returns at most length characters
 * - If start lies outside the string, returns empty ({ })
 * - If fewer characters remain than length, returns just remaining characters
 * - If input or start is empty, result is empty
 * - If empty length provided, behaves as if length not provided
 * - If negative or zero length provided, returns empty string ('')
 */
public class SubstringTest extends IRNodeTestBase {

    /**
     * Test cases directly from the FHIRPath specification plus critical edge cases.
     */
    static Stream<Arguments> substringTestCases() {
        return Stream.of(
                // Examples from FHIRPath spec
                Arguments.of("abcdefg", 3, null, "defg", "spec: substring from middle to end"),
                Arguments.of("abcdefg", 1, 2, "bc", "spec: substring with length"),
                Arguments.of("abcdefg", 6, 2, "g", "spec: length exceeds remaining chars"),
                Arguments.of("abcdefg", 7, 1, null, "spec: start outside string"),
                Arguments.of("abcdefg", -1, 1, null, "spec: negative start outside string"),
                Arguments.of("abcdefg", 3, 0, "", "spec: zero length returns empty string"),
                Arguments.of("abcdefg", 3, -1, "", "spec: negative length returns empty string"),
                Arguments.of("abcdefg", -1, -1, null, "spec: negative start and length"),

                // Critical edge cases
                Arguments.of("abcdefg", 0, null, "abcdefg", "edge: start at beginning no length"),
                Arguments.of("abcdefg", 0, 3, "abc", "edge: start at beginning with length"),
                Arguments.of("a", 0, null, "a", "edge: single char"),
                Arguments.of("a", 1, null, null, "edge: single char at end position"),
                Arguments.of("", 0, null, null, "edge: empty string"),

                // Null input or start (spec: result is empty)
                Arguments.of(null, 0, 1, null, "spec: null input returns empty"),
                Arguments.of("abcdefg", null, 1, null, "spec: null start returns empty")
        );
    }

    @ParameterizedTest(name = "[{index}] {4}")
    @MethodSource("substringTestCases")
    void testSubstring(String input, Integer start, Integer length, String expected, String description) {
        Substring substring = new Substring(
                str(input),
                integer(start),
                length != null ? integer(length) : null
        );

        String actual = evaluateAsString(substring);
        assertEquals(expected, actual, description);
    }

    /**
     * Test empty length parameter behavior (spec: behaves as if length not provided).
     */
    static Stream<Arguments> emptyLengthTestCases() {
        return Stream.of(
                Arguments.of("abcdefg", 0, "abcdefg", "empty length: returns from start to end"),
                Arguments.of("abcdefg", 3, "defg", "empty length: returns from middle to end")
        );
    }

    @ParameterizedTest(name = "[{index}] {3}")
    @MethodSource("emptyLengthTestCases")
    void testSubstringWithEmptyLength(String input, Integer start, String expected, String description) {
        // Explicitly pass null literal for length to test "empty length" behavior
        Substring substring = new Substring(
                str(input),
                integer(start),
                lit(null, Type.INTEGER)
        );

        String actual = evaluateAsString(substring);
        assertEquals(expected, actual, description);
    }
}
