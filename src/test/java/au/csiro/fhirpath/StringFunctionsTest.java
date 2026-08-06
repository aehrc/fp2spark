package au.csiro.fhirpath;

import au.csiro.fhirpath.test.FhirPathTestBase;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Tests for FHIRPath string manipulation functions.
 *
 * <p>Based on FHIRPath specification section 5.7 (String Manipulation).
 *
 * <p>Covers:
 *
 * <ul>
 *   <li>length, upper, lower, trim
 *   <li>startsWith, endsWith, contains (function), indexOf
 *   <li>substring, replace, matches, replaceMatches
 *   <li>split, join, toChars
 *   <li>Empty collection propagation for all functions
 *   <li>Edge cases from spec examples
 * </ul>
 */
public class StringFunctionsTest extends FhirPathTestBase {

  // ---------------------------------------------------------------------------
  // length()
  // ---------------------------------------------------------------------------

  @TestFactory
  Stream<DynamicTest> testLength() {
    return builder()
        .group("length() core semantics")
        .testEquals(7, "'abcdefg'.length()")
        .testEquals(0, "''.length()", "Empty string has length 0")
        .group("length() empty propagation")
        .testEmpty("{}.length()")
        .build();
  }

  // ---------------------------------------------------------------------------
  // upper()
  // ---------------------------------------------------------------------------

  @TestFactory
  Stream<DynamicTest> testUpper() {
    return builder()
        .group("upper() spec examples")
        .testEquals("ABCDEFG", "'abcdefg'.upper()")
        .testEquals("ABCDEFG", "'AbCdefg'.upper()")
        .group("upper() empty propagation")
        .testEmpty("{}.upper()")
        .build();
  }

  // ---------------------------------------------------------------------------
  // lower()
  // ---------------------------------------------------------------------------

  @TestFactory
  Stream<DynamicTest> testLower() {
    return builder()
        .group("lower() spec examples")
        .testEquals("abcdefg", "'ABCDEFG'.lower()")
        .testEquals("abcdefg", "'aBcDEFG'.lower()")
        .group("lower() empty propagation")
        .testEmpty("{}.lower()")
        .build();
  }

  // ---------------------------------------------------------------------------
  // trim()
  // ---------------------------------------------------------------------------

  @TestFactory
  Stream<DynamicTest> testTrim() {
    return builder()
        .group("trim() core semantics")
        .testEquals("abc", "'  abc  '.trim()")
        .testEquals("abc", "'abc'.trim()", "No whitespace to trim")
        .testEquals("", "'   '.trim()", "All whitespace yields empty string")
        .group("trim() empty propagation")
        .testEmpty("{}.trim()")
        .build();
  }

  // ---------------------------------------------------------------------------
  // startsWith()
  // ---------------------------------------------------------------------------

  @TestFactory
  Stream<DynamicTest> testStartsWith() {
    return builder()
        .group("startsWith() spec examples")
        .testTrue("'abcdefg'.startsWith('abc')")
        .testFalse("'abcdefg'.startsWith('xyz')")
        .group("startsWith() edge cases")
        .testTrue("'abcdefg'.startsWith('')", "Empty prefix always returns true")
        .group("startsWith() empty propagation")
        .testEmpty("{}.startsWith('abc')", "Empty input returns empty")
        .build();
  }

  // ---------------------------------------------------------------------------
  // endsWith()
  // ---------------------------------------------------------------------------

  @TestFactory
  Stream<DynamicTest> testEndsWith() {
    return builder()
        .group("endsWith() spec examples")
        .testTrue("'abcdefg'.endsWith('efg')")
        .testFalse("'abcdefg'.endsWith('abc')")
        .group("endsWith() edge cases")
        .testTrue("'abcdefg'.endsWith('')", "Empty suffix always returns true")
        .group("endsWith() empty propagation")
        .testEmpty("{}.endsWith('efg')", "Empty input returns empty")
        .build();
  }

  // ---------------------------------------------------------------------------
  // contains() -- string function, not membership operator
  // ---------------------------------------------------------------------------

  @TestFactory
  Stream<DynamicTest> testContainsFunction() {
    return builder()
        .group("contains() spec examples")
        .testTrue("'abc'.contains('b')")
        .testTrue("'abc'.contains('bc')")
        .testFalse("'abc'.contains('d')")
        .group("contains() edge cases")
        .testTrue("'abc'.contains('')", "Empty substring always returns true")
        .group("contains() empty propagation")
        .testEmpty("{}.contains('b')", "Empty input returns empty")
        .build();
  }

  // ---------------------------------------------------------------------------
  // indexOf()
  // ---------------------------------------------------------------------------

  @TestFactory
  Stream<DynamicTest> testIndexOf() {
    return builder()
        .group("indexOf() spec examples")
        .testEquals(1, "'abcdefg'.indexOf('bc')")
        .testEquals(-1, "'abcdefg'.indexOf('x')")
        .testEquals(0, "'abcdefg'.indexOf('abcdefg')")
        .group("indexOf() edge cases")
        .testEquals(0, "'abcdefg'.indexOf('')", "Empty substring returns 0")
        .group("indexOf() empty propagation")
        .testEmpty("{}.indexOf('bc')", "Empty input returns empty")
        .testEmpty("'abc'.indexOf({})", "Empty argument returns empty")
        .build();
  }

  // ---------------------------------------------------------------------------
  // substring()
  // ---------------------------------------------------------------------------

  @TestFactory
  Stream<DynamicTest> testSubstring() {
    return builder()
        .group("substring() spec examples")
        .testEquals("defg", "'abcdefg'.substring(3)")
        .testEquals("bc", "'abcdefg'.substring(1, 2)")
        .testEquals("g", "'abcdefg'.substring(6, 2)", "Fewer remaining chars than length")
        .testEmpty("'abcdefg'.substring(7, 1)", "Start beyond string length returns empty")
        .testEmpty("'abcdefg'.substring(-1, 1)", "Negative start returns empty")
        .testEquals("", "'abcdefg'.substring(3, 0)", "Zero length returns empty string")
        .testEquals("", "'abcdefg'.substring(3, -1)", "Negative length returns empty string")
        .testEmpty(
            "'abcdefg'.substring(-1, -1)", "Negative start with negative length returns empty")
        .group("substring() empty propagation")
        .testEmpty("{}.substring(1)", "Empty input returns empty")
        .testEmpty("'abc'.substring({})", "Empty start returns empty")
        .build();
  }

  // ---------------------------------------------------------------------------
  // replace()
  // ---------------------------------------------------------------------------

  @TestFactory
  Stream<DynamicTest> testReplace() {
    return builder()
        .group("replace() spec examples")
        .testEquals("ab123fg", "'abcdefg'.replace('cde', '123')")
        .testEquals("abfg", "'abcdefg'.replace('cde', '')", "Empty substitution removes pattern")
        .testEquals("xaxbxcx", "'abc'.replace('', 'x')", "Empty pattern surrounds each char")
        .group("replace() empty propagation")
        .testEmpty("{}.replace('a', 'b')", "Empty input returns empty")
        .testEmpty("'abc'.replace({}, 'b')", "Empty pattern arg returns empty")
        .testEmpty("'abc'.replace('a', {})", "Empty substitution arg returns empty")
        .build();
  }

  // ---------------------------------------------------------------------------
  // matches()
  // ---------------------------------------------------------------------------

  @TestFactory
  Stream<DynamicTest> testMatches() {
    return builder()
        .group("matches() spec examples")
        .testTrue(
            "'http://fhir.org/guides/cqf/common/Library/FHIR-ModelInfo|4.0.1'.matches('Library')",
            "Partial match returns true")
        .testFalse(
            "'N8000123123'.matches('^N[0-9]{8}$')", "Full-match anchors reject longer string")
        .testTrue("'N8000123123'.matches('N[0-9]{8}')", "Partial match without anchors succeeds")
        .group("matches() dotAll semantics (FHIRPath spec §5.6.8)")
        .testTrue(
            "'first line\nsecond line'.matches('line.second')",
            "'.' matches newline between lines (DOTALL enabled)")
        .testTrue(
            "'a\nb\nc'.matches('a.b.c')", "'.' matches multiple newlines across a multiline string")
        .testTrue(
            "'abc'.matches('a.c')", "'.' still matches a non-newline character when DOTALL is on")
        .testTrue("'a\rb'.matches('a.b')", "'.' matches carriage return under DOTALL")
        .testFalse(
            "'a\nb'.matches('^b$')",
            "DOTALL does not imply MULTILINE — ^/$ remain single-line anchors")
        .testTrue(
            "'ABC\nDEF'.matches('(?i)abc.def')",
            "User-provided (?i) inline flag combines with prepended (?s)")
        .group("matches() empty propagation")
        .testEmpty("{}.matches('abc')", "Empty input returns empty")
        .testEmpty("'abc'.matches({})", "Empty regex returns empty")
        .build();
  }

  // ---------------------------------------------------------------------------
  // replaceMatches()
  // ---------------------------------------------------------------------------

  @TestFactory
  Stream<DynamicTest> testReplaceMatches() {
    return builder()
        .group("replaceMatches() core semantics")
        .testEquals(
            "30-11-1972",
            "'11/30/1972'.replaceMatches('(\\\\d{1,2})/(\\\\d{1,2})/(\\\\d{2,4})', '$2-$1-$3')",
            "Capture group replacement")
        .testEquals(
            "xbcdx", "'abcda'.replaceMatches('a', 'x')", "Replaces all occurrences of 'a' with 'x'")
        .group("replaceMatches() empty propagation")
        .testEmpty("{}.replaceMatches('a', 'b')", "Empty input returns empty")
        .testEmpty("'abc'.replaceMatches({}, 'b')", "Empty regex returns empty")
        .testEmpty("'abc'.replaceMatches('a', {})", "Empty substitution returns empty")
        .build();
  }

  // ---------------------------------------------------------------------------
  // split()
  // ---------------------------------------------------------------------------

  @TestFactory
  Stream<DynamicTest> testSplit() {
    return builder()
        .group("split() spec examples")
        .testEquals(List.of("A", "B", "C"), "'A,B,C'.split(',')")
        .testEquals(
            List.of("ABC"), "'ABC'.split(',')", "No match returns single-element collection")
        .testEquals(
            List.of("A", "", "C"), "'A,,C'.split(',')", "Adjacent separators yield empty strings")
        .group("split() literal separator (not regex)")
        .testEquals(List.of("a", "b", "c"), "'a.b.c'.split('.')", "Dot is literal, not regex")
        .testEquals(List.of("a", "b"), "'a+b'.split('+')", "Plus is literal, not regex")
        .group("split() empty propagation")
        .testEmpty("{}.split(',')", "Empty input returns empty")
        .build();
  }

  // ---------------------------------------------------------------------------
  // join()
  // ---------------------------------------------------------------------------

  @TestFactory
  Stream<DynamicTest> testJoin() {
    return builder()
        .group("join() spec examples")
        .testEquals("ABC", "('A' | 'B' | 'C').join()", "No separator concatenates")
        .testEquals("A,B,C", "('A' | 'B' | 'C').join(',')", "Comma separator")
        .group("join() edge cases")
        .testEquals("hello", "'hello'.join(',')", "Single string returns itself")
        .group("join() empty propagation")
        .testEmpty("{}.join(',')", "Empty input returns empty")
        .build();
  }

  // ---------------------------------------------------------------------------
  // toChars()
  // ---------------------------------------------------------------------------

  @TestFactory
  Stream<DynamicTest> testToChars() {
    return builder()
        .group("toChars() spec examples")
        .testEquals(List.of("a", "b", "c"), "'abc'.toChars()")
        .group("toChars() edge cases")
        .testEmpty("''.toChars()", "Empty string returns empty collection")
        .group("toChars() empty propagation")
        .testEmpty("{}.toChars()")
        .build();
  }
}
