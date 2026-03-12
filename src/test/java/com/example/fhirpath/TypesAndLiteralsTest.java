package com.example.fhirpath;

import com.example.fhirpath.test.FhirPathTestBase;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Comprehensive tests for FHIRPath System types and literal expressions.
 *
 * <p>Tests all literal types and escape sequences as specified in the FHIRPath specification. Does
 * NOT assume existence of any operations except basic field traversal.
 *
 * <p>Based on FHIRPath spec section 3 (Literals) and Pathling's SystemDslTest.
 */
public class TypesAndLiteralsTest extends FhirPathTestBase {

  @TestFactory
  Stream<DynamicTest> testStringLiterals() {
    return builder()
        .group("String literals - basic")
        .testEquals("test", "'test'")
        .testEquals("test with spaces", "'test with spaces'")
        .testEquals("", "''", "Empty string")
        .testEquals("urn:oid:3.4.5.6.7.8", "'urn:oid:3.4.5.6.7.8'")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testStringLiteralEscapeSequences() {
    return builder()
        .group("String escape sequences")
        .testEquals("'", "'\\''", "Single quote")
        .testEquals("\"", "'\\\"'", "Double quote")
        .testEquals("`", "'\\`'", "Backtick")
        .testEquals("\r", "'\\r'", "Carriage return")
        .testEquals("\n", "'\\n'", "Line feed")
        .testEquals("\t", "'\\t'", "Tab")
        .testEquals("\f", "'\\f'", "Form feed")
        .testEquals("\\", "'\\\\'", "Backslash")
        .testEquals("line1\nline2", "'line1\\nline2'", "Newline in string")
        .testEquals("tab\tcharacter", "'tab\\tcharacter'", "Tab in string")
        .group("String escape sequences - combined")
        .testEquals(
            "All escapes: \\\r\n\t\f\"`'",
            "'All escapes: \\\\\\r\\n\\t\\f\\\"`\\''",
            "All escape sequences")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testStringLiteralUnicodeEscapes() {
    return builder()
        .group("String Unicode escape sequences")
        .testEquals("Peter", "'P\\u0065ter'", "Unicode escape for 'e'")
        .testEquals("A", "'\\u0041'", "Unicode escape for 'A'")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testIntegerLiterals() {
    return builder()
        .group("Integer literals")
        .testEquals(0, "0", "Zero")
        .testEquals(42, "42", "Positive integer")
        .testEquals(999999, "999999", "Large integer")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testDecimalLiterals() {
    return builder()
        .group("Decimal literals")
        .testEquals(0.0, "0.0", "Zero decimal")
        .testEquals(3.14, "3.14", "Positive decimal")
        .testEquals(100.0, "100.0", "Integer with decimal point")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testBooleanLiterals() {
    return builder()
        .group("Boolean literals")
        .testTrue("true", "Boolean true")
        .testFalse("false", "Boolean false")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testEmptyCollectionLiteral() {
    return builder().group("Empty collection literal").testEmpty("{}", "Empty collection").build();
  }

  @TestFactory
  Stream<DynamicTest> testResourceFieldsAllTypes() {
    return builder()
        .group("Resource fields - all System types")
        .withSubject(
            "Patient",
            p ->
                p.string("id", "patient-123")
                    .integer("age", 42)
                    .decimal("score", 98.6)
                    .bool("active", true))
        .testEquals("patient-123", "id", "String field")
        .testEquals(42, "age", "Integer field")
        .testEquals(98.6, "score", "Decimal field")
        .testTrue("active", "Boolean field")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testNestedFieldTraversal() {
    return builder()
        .group("Nested field access")
        .withSubject(
            "Patient",
            p ->
                p.element("name", n -> n.string("family", "Smith").string("given", "John"))
                    .element(
                        "address",
                        a ->
                            a.string("city", "Boston")
                                .element(
                                    "location",
                                    l ->
                                        l.decimal("latitude", 42.3601)
                                            .decimal("longitude", -71.0589))))
        .testEquals("Smith", "name.family", "Nested string field")
        .testEquals("John", "name.given", "Nested string field")
        .testEquals("Boston", "address.city", "Two-level nesting")
        .testEquals(42.3601, "address.location.latitude", "Three-level nesting - decimal")
        .testEquals(-71.0589, "address.location.longitude", "Three-level nesting - decimal")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testArrayFieldsAllTypes() {
    return builder()
        .group("Array fields - String")
        .withSubject("Patient", p -> p.stringArray("identifier", "id1", "id2", "id3"))
        .testEquals(List.of("id1", "id2", "id3"), "identifier")
        .group("Array fields - Integer")
        .withSubject("Observation", o -> o.integerArray("values", 10, 20, 30))
        .testEquals(List.of(10, 20, 30), "values")
        .group("Array fields - Decimal")
        .withSubject("Measurement", m -> m.decimalArray("readings", 1.1, 2.2, 3.3))
        .testEquals(List.of(1.1, 2.2, 3.3), "readings")
        .group("Array fields - Boolean")
        .withSubject("Flags", f -> f.boolArray("flags", true, false, true))
        .testEquals(List.of(true, false, true), "flags")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testNestedArrayFields() {
    return builder()
        .group("Nested array structures")
        .withSubject(
            "Patient",
            p ->
                p.elementArray(
                        "name",
                        n -> n.string("family", "Smith").stringArray("given", "John", "James"),
                        n -> n.string("family", "Doe").stringArray("given", "Jane"))
                    .elementArray(
                        "contact",
                        c ->
                            c.string("type", "phone")
                                .element(
                                    "address",
                                    a -> a.string("city", "Boston").integer("zip", 2101)),
                        c ->
                            c.string("type", "email")
                                .element(
                                    "address",
                                    a -> a.string("city", "Cambridge").integer("zip", 2139))))
        .testEquals(List.of("Smith", "Doe"), "name.family", "Array of nested string fields")
        .testEquals(
            List.of("John", "James", "Jane"),
            "name.given",
            "Flattened array from nested string arrays")
        .testEquals(List.of("phone", "email"), "contact.type", "Array of nested strings")
        .testEquals(
            List.of("Boston", "Cambridge"), "contact.address.city", "Array with deep nesting")
        .testEquals(
            List.of(2101, 2139), "contact.address.zip", "Array with deep nesting - integers")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testEmptyAndMissingFields() {
    return builder()
        .group("Empty and missing fields")
        .withSubject("Patient", p -> p.string("id", "p1"))
        .testEmpty("unknownField", "Unknown field returns empty")
        .testEmpty("id.unknownNested", "Nested unknown field returns empty")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testMixedCardinalityFields() {
    return builder()
        .group("Mixed cardinality in same resource")
        .withSubject(
            "Patient",
            p ->
                p.string("id", "p1") // SINGLE
                    .stringArray("names", "John", "Jane") // MANY
                    .integer("age", 30) // SINGLE
                    .integerArray("scores", 10, 20, 30) // MANY
                    .element(
                        "address",
                        a ->
                            a // SINGLE complex
                                .string("city", "Boston")
                                .stringArray("lines", "123 Main", "Apt 4") // MANY in nested
                        )
                    .elementArray(
                        "contact", // MANY complex
                        c -> c.string("type", "phone"),
                        c -> c.string("type", "email")))
        .testEquals("p1", "id", "Singular string")
        .testEquals(List.of("John", "Jane"), "names", "Array of strings")
        .testEquals(30, "age", "Singular integer")
        .testEquals(List.of(10, 20, 30), "scores", "Array of integers")
        .testEquals("Boston", "address.city", "Nested singular")
        .testEquals(List.of("123 Main", "Apt 4"), "address.lines", "Nested array")
        .testEquals(List.of("phone", "email"), "contact.type", "Array of nested elements")
        .build();
  }
}
