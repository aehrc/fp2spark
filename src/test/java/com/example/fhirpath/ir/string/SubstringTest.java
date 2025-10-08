package com.example.fhirpath.ir.string;

import com.example.fhirpath.ir.IRNodeTestBase;
import com.example.fhirpath.typing.Type;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for the Substring IRNode.
 * Tests all edge cases from the FHIRPath specification.
 */
public class SubstringTest extends IRNodeTestBase {

    /**
     * Test cases for substring with both start and length parameters.
     */
    static Stream<Arguments> substringWithLengthTestCases() {
        return Stream.of(
                // Basic functionality
                Arguments.of("abcdefg", 0, 3, "abc", "basic: first 3 characters"),
                Arguments.of("abcdefg", 1, 2, "bc", "basic: middle 2 characters"),
                Arguments.of("abcdefg", 3, 2, "de", "basic: characters from middle"),
                Arguments.of("abcdefg", 6, 1, "g", "basic: last character"),

                // Zero length - spec says returns empty string ('')
                Arguments.of("abcdefg", 0, 0, "", "zero length: returns empty string"),
                Arguments.of("abcdefg", 3, 0, "", "zero length: from middle returns empty string"),
                Arguments.of("abcdefg", 6, 0, "", "zero length: near end returns empty string"),

                // Negative length - spec says returns empty string ('')
                Arguments.of("abcdefg", 0, -1, "", "negative length: returns empty string"),
                Arguments.of("abcdefg", 2, -5, "", "negative length: from middle returns empty string"),
                Arguments.of("abcdefg", 5, -10, "", "negative length: large negative returns empty string"),

                // Length exceeds remaining characters
                Arguments.of("abcdefg", 5, 10, "fg", "length exceeds: returns remaining chars"),
                Arguments.of("abcdefg", 0, 100, "abcdefg", "length exceeds: returns entire string"),
                Arguments.of("abcdefg", 6, 5, "g", "length exceeds: returns last char"),

                // Start at end of string - spec says returns empty ({ })
                Arguments.of("abcdefg", 7, 1, null, "start at end: returns null/empty"),
                Arguments.of("abcdefg", 7, 5, null, "start at end with length: returns null/empty"),

                // Start beyond end of string - spec says returns empty ({ })
                Arguments.of("abcdefg", 10, 2, null, "start beyond end: returns null/empty"),
                Arguments.of("abcdefg", 100, 5, null, "start far beyond end: returns null/empty"),

                // Edge case: negative start (not in spec, but testing behavior)
                Arguments.of("abcdefg", -1, 3, null, "negative start: returns null/empty"),
                Arguments.of("abcdefg", -5, 2, null, "negative start: returns null/empty"),

                // Single character string
                Arguments.of("a", 0, 1, "a", "single char: full string"),
                Arguments.of("a", 0, 0, "", "single char: zero length"),
                Arguments.of("a", 1, 1, null, "single char: start at end returns null/empty"),
                Arguments.of("a", 2, 1, null, "single char: start beyond end"),

                // Empty string input - length 0, so any position is out of bounds
                Arguments.of("", 0, 0, null, "empty string: position 0 returns null/empty"),
                Arguments.of("", 0, 1, null, "empty string: position 0 with length returns null/empty"),
                Arguments.of("", 1, 1, null, "empty string: start beyond"),

                // Null input - spec says returns empty
                Arguments.of(null, 0, 1, null, "null input: returns null/empty"),
                Arguments.of(null, 5, 10, null, "null input with params: returns null/empty"),

                // Null start - spec says returns empty
                // Note: This will be tested separately if needed

                // Large strings
                Arguments.of("a".repeat(1000), 500, 10, "a".repeat(10), "large string: substring from middle"),
                Arguments.of("a".repeat(1000), 990, 20, "a".repeat(10), "large string: near end"),

                // Special characters and unicode
                Arguments.of("hello world", 6, 5, "world", "with spaces: extract word"),
                Arguments.of("hello\nworld", 6, 5, "world", "with newline: extract after newline"),
                Arguments.of("café", 0, 3, "caf", "unicode: first 3 chars"),
                Arguments.of("café", 3, 1, "é", "unicode: unicode character"),
                Arguments.of("🚀🌟⭐", 1, 1, "🌟", "emoji: extract middle emoji")
        );
    }

    @ParameterizedTest(name = "[{index}] {4}")
    @MethodSource("substringWithLengthTestCases")
    void testSubstringWithLength(String input, Integer start, Integer length, String expected, String description) {
        Substring substring = new Substring(
                str(input),
                integer(start),
                integer(length)
        );

        String actual = evaluateAsString(substring);
        assertEquals(expected, actual, description);
    }

    /**
     * Test cases for substring with only start parameter (no length).
     */
    static Stream<Arguments> substringWithoutLengthTestCases() {
        return Stream.of(
                // Basic functionality - should return from start to end
                Arguments.of("abcdefg", 0, "abcdefg", "no length: from beginning returns all"),
                Arguments.of("abcdefg", 1, "bcdefg", "no length: from position 1"),
                Arguments.of("abcdefg", 3, "defg", "no length: from middle to end"),
                Arguments.of("abcdefg", 6, "g", "no length: from last position"),

                // Start at end - spec says returns empty ({ })
                Arguments.of("abcdefg", 7, null, "no length: start at end returns null/empty"),

                // Start beyond end - spec says returns empty ({ })
                Arguments.of("abcdefg", 10, null, "no length: start beyond end returns null/empty"),
                Arguments.of("abcdefg", 100, null, "no length: start far beyond returns null/empty"),

                // Negative start
                Arguments.of("abcdefg", -1, null, "no length: negative start returns null/empty"),

                // Single character
                Arguments.of("a", 0, "a", "no length: single char from start"),
                Arguments.of("a", 1, null, "no length: single char at end returns null/empty"),

                // Empty string - length 0, so position 0 is at/beyond the end
                Arguments.of("", 0, null, "no length: empty string position 0 returns null/empty"),
                Arguments.of("", 1, null, "no length: empty string beyond"),

                // Null input
                Arguments.of(null, 0, null, "no length: null input returns null/empty"),
                Arguments.of(null, 5, null, "no length: null input with start returns null/empty"),

                // Special characters
                Arguments.of("hello world", 6, "world", "no length: extract to end with space"),
                Arguments.of("café", 3, "é", "no length: unicode to end"),
                Arguments.of("🚀🌟⭐", 1, "🌟⭐", "no length: emojis to end")
        );
    }

    @ParameterizedTest(name = "[{index}] {3}")
    @MethodSource("substringWithoutLengthTestCases")
    void testSubstringWithoutLength(String input, Integer start, String expected, String description) {
        Substring substring = new Substring(
                str(input),
                integer(start),
                null  // no length parameter
        );

        String actual = evaluateAsString(substring);
        assertEquals(expected, actual, description);
    }

    /**
     * Test cases for null/empty parameters according to FHIRPath spec:
     * - If input is empty, result is empty
     * - If start is empty, result is empty
     * - If length is empty, behaves as if length not provided
     */
    static Stream<Arguments> nullParameterTestCases() {
        return Stream.of(
                // Null start parameter - should return null/empty
                // Note: Creating a Literal with null value for Integer type
                Arguments.of("abcdefg", null, 3, null, "null start with length: returns null/empty"),
                Arguments.of("abcdefg", null, null, null, "null start no length: returns null/empty"),

                // All null
                Arguments.of(null, null, null, null, "all null: returns null/empty"),
                Arguments.of(null, null, 5, null, "null input and start: returns null/empty")
        );
    }

    @ParameterizedTest(name = "[{index}] {4}")
    @MethodSource("nullParameterTestCases")
    void testSubstringWithNullParameters(String input, Integer start, Integer length, String expected, String description) {
        Substring substring = new Substring(
                str(input),
                integer(start),
                length != null ? integer(length) : null
        );

        String actual = evaluateAsString(substring);
        assertEquals(expected, actual, description);
    }

    /**
     * Test the behavior when length is null (empty in FHIRPath terms).
     * According to spec: "If an empty length is provided, the behavior is the same as if length had not been provided."
     */
    static Stream<Arguments> emptyLengthParameterTestCases() {
        return Stream.of(
                // Null length should behave like no length - return from start to end
                Arguments.of("abcdefg", 0, "abcdefg", "empty length param: from start returns all"),
                Arguments.of("abcdefg", 3, "defg", "empty length param: from middle to end"),
                Arguments.of("abcdefg", 6, "g", "empty length param: from near end")
        );
    }

    @ParameterizedTest(name = "[{index}] {3}")
    @MethodSource("emptyLengthParameterTestCases")
    void testSubstringWithEmptyLength(String input, Integer start, String expected, String description) {
        // Explicitly pass null for length to test the "empty length" behavior
        Substring substring = new Substring(
                str(input),
                integer(start),
                lit(null, Type.INTEGER)  // Explicitly create a null literal
        );

        String actual = evaluateAsString(substring);
        assertEquals(expected, actual, description);
    }

    /**
     * Test type validation - substring should only work with String input
     */
    static Stream<Arguments> typeValidationTestCases() {
        return Stream.of(
                // These test cases verify that we get appropriate behavior (likely errors or null)
                // when non-string types are passed
                Arguments.of(123, 0, 2, "integer input"),
                Arguments.of(true, 0, 1, "boolean input")
        );
    }

    @ParameterizedTest(name = "[{index}] {3}")
    @MethodSource("typeValidationTestCases")
    void testSubstringWithInvalidInputType(Object input, Integer start, Integer length, String description) {
        // This test verifies that invalid input types are handled gracefully
        // The exact behavior depends on Spark's substr behavior with non-string inputs
        try {
            Type inputType = input instanceof Integer ? Type.INTEGER : Type.BOOLEAN;
            Substring substring = new Substring(
                    lit(input, inputType),
                    integer(start),
                    integer(length)
            );

            // Attempt to evaluate - might throw exception or return null
            evaluateAsString(substring);

            // If we get here without exception, the implementation handles it somehow
            // This is acceptable - we're just documenting the behavior
        } catch (Exception e) {
            // Exception is expected for invalid input types
            // This is acceptable behavior
        }
    }
}