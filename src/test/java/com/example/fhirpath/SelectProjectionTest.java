package com.example.fhirpath;

import com.example.fhirpath.test.FhirPathTestBase;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Tests for FHIRPath select() function.
 *
 * <p>Based on FHIRPath specification section 5.2 (Filtering and projection).
 *
 * <p>Covers:
 *
 * <ul>
 *   <li>Core semantics: select with $this, arithmetic projection, string projection
 *   <li>Empty propagation: empty input, empty lambda results
 *   <li>Singular input: select on a single value
 *   <li>Flattening: when projection returns multiple items per element
 *   <li>Resource field access and nested field access in projection
 *   <li>Chaining: select().where(), where().select(), select().select()
 *   <li>$this rebinding in chained select()
 * </ul>
 */
public class SelectProjectionTest extends FhirPathTestBase {

  // ========== select() on literal collections ==========

  @TestFactory
  Stream<DynamicTest> testSelectCoreSemanticsOnLiterals() {
    return builder()
        .group("select() with $this identity")
        .testEquals(List.of(1, 2, 3), "(1 ; 2 ; 3).select($this)", "Identity projection")
        .testEquals(
            List.of("a", "b", "c"),
            "('a' ; 'b' ; 'c').select($this)",
            "Identity projection on strings")
        .group("select() with arithmetic on $this")
        .testEquals(List.of(11, 12, 13), "(1 ; 2 ; 3).select($this + 10)", "Addition projection")
        .testEquals(List.of(2, 4, 6), "(1 ; 2 ; 3).select($this * 2)", "Multiplication projection")
        .group("select() with string projection")
        .testEquals(
            List.of("ax", "bx"),
            "('a' ; 'b').select($this + 'x')",
            "String concatenation projection")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testSelectOnSingularValue() {
    return builder()
        .group("select() on singular value")
        .testEquals(43, "42.select($this + 1)", "Singular integer projection")
        .testEquals("hello!", "'hello'.select($this + '!')", "Singular string projection")
        .build();
  }

  // ========== select() empty propagation ==========

  @TestFactory
  Stream<DynamicTest> testSelectEmptyPropagation() {
    return builder()
        .group("select() empty input")
        .testEmpty("{}.select($this)", "Empty input returns empty")
        .testEmpty("{}.select($this + 1)", "Empty input with arithmetic returns empty")
        .testEmpty("(1 ; 2 ; 3).select({})", "Empty lambda result produces empty collection")
        .group("select() empty lambda result")
        .withSubject(
            "Patient",
            p ->
                p.elementArray(
                    "name",
                    n -> n.string("family", "Smith").string("use", "official"),
                    n -> n.string("use", "alias")))
        .testEquals(
            "Smith",
            "name.select(family).first()",
            "Absent field in some elements produces fewer results")
        .testEquals(
            1,
            "name.select(family).count()",
            "Element without family does not contribute to output")
        .build();
  }

  // ========== select() flattening ==========

  @TestFactory
  Stream<DynamicTest> testSelectFlattening() {
    return builder()
        .group("select() flattens multi-valued projection results")
        .withSubject(
            "Patient",
            p ->
                p.elementArray(
                    "name",
                    n -> n.stringArray("given", "John", "James"),
                    n -> n.stringArray("given", "Jane")))
        .testEquals(
            List.of("John", "James", "Jane"),
            "name.select(given)",
            "All given names flattened into single collection")
        .testEquals(3, "name.select(given).count()", "Count reflects flattened total")
        .build();
  }

  // ========== select() on resource fields ==========

  @TestFactory
  Stream<DynamicTest> testSelectOnResourceFields() {
    return builder()
        .group("select() with field access")
        .withSubject(
            "Patient",
            p ->
                p.elementArray(
                    "name",
                    n ->
                        n.string("family", "Smith")
                            .stringArray("given", "John", "James")
                            .string("use", "official"),
                    n ->
                        n.string("family", "Doe")
                            .stringArray("given", "Jane")
                            .string("use", "usual")))
        .testEquals(
            List.of("Smith", "Doe"),
            "name.select(family)",
            "Project singular field from each element")
        .testEquals(
            List.of("Smith", "Doe"),
            "name.select($this.family)",
            "Explicit $this.field access in select")
        .group("select() with nested field access")
        .testEquals(
            List.of("John", "Jane"),
            "name.select(given.first())",
            "Project first given from each name element")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testSelectSpecExampleStringConcatenation() {
    return builder()
        .group("select() spec example: string concatenation")
        .withSubject(
            "Patient",
            p ->
                p.elementArray(
                    "name",
                    n ->
                        n.string("family", "Smith")
                            .stringArray("given", "John")
                            .string("use", "usual"),
                    n ->
                        n.string("family", "Doe")
                            .stringArray("given", "Jane")
                            .string("use", "official")))
        .testEquals(
            "John Smith",
            "name.where(use = 'usual').select(given.first() + ' ' + family).first()",
            "Spec example: concatenate first given with family for usual name")
        .build();
  }

  // ========== select() chaining ==========

  @TestFactory
  Stream<DynamicTest> testSelectChaining() {
    return builder()
        .group("select().where() chaining")
        .testEquals(
            List.of(6, 8),
            "(1 ; 2 ; 3 ; 4).select($this * 2).where($this > 4)",
            "select then filter")
        .group("where().select() chaining")
        .testEquals(
            List.of(4, 6), "(1 ; 2 ; 3).where($this > 1).select($this * 2)", "Filter then project")
        .group("select().select() chaining")
        .testEquals(
            List.of(11, 12, 13),
            "(1 ; 2 ; 3).select($this).select($this + 10)",
            "$this rebinds in chained select")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testSelectChainingOnResources() {
    return builder()
        .group("where().select() on resource fields")
        .withSubject(
            "Patient",
            p ->
                p.elementArray(
                    "name",
                    n -> n.string("family", "Smith").string("use", "official"),
                    n -> n.string("family", "Doe").string("use", "usual")))
        .testEquals(
            "Smith",
            "name.where(use = 'official').select(family).first()",
            "Filter then project on resource")
        .group("select().count()")
        .testEquals(2, "name.select(family).count()", "Count projected results")
        .build();
  }
}
