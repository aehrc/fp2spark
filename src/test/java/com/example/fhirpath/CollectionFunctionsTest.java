package com.example.fhirpath;

import com.example.fhirpath.test.FhirPathTestBase;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Tests for FHIRPath collection functions: count(), exists(), empty(), first().
 *
 * <p>Based on FHIRPath specification section 5.1 (Existence) and 5.2 (Filtering and projection).
 *
 * <p>Covers:
 *
 * <ul>
 *   <li>count() on singular values, empty collections, and multi-element collections
 *   <li>empty() on singular values, empty collections, and multi-element collections
 *   <li>exists() on singular values, empty collections, and multi-element collections
 *   <li>first() on singular values, empty collections, and multi-element collections
 *   <li>first() on resource fields with nested structures
 *   <li>empty() after filtering (all elements removed, partial filtering)
 *   <li>empty() chained with not()
 * </ul>
 */
public class CollectionFunctionsTest extends FhirPathTestBase {

  // ========== count() ==========

  @TestFactory
  Stream<DynamicTest> testCountOnSingularValues() {
    return builder()
        .group("count() on singular values")
        .testEquals(1, "10.count()", "Count of single integer")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testCountOnEmptyCollections() {
    return builder()
        .group("count() on empty collections")
        .testEquals(0, "{}.count()", "Count of empty collection")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testCountOnCollections() {
    return builder()
        .group("count() on collections")
        .testEquals(2, "(1 ; 2).count()", "Count of 2-element collection")
        .testEquals(3, "('a' ; 'b' ; 'c').count()", "Count of 3-element collection")
        .build();
  }

  // ========== empty() ==========

  @TestFactory
  Stream<DynamicTest> testEmptyOnEmptyCollection() {
    return builder()
        .group("empty() on empty collection")
        .testTrue("{}.empty()", "Empty collection is empty")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testEmptyOnSingularValues() {
    return builder()
        .group("empty() on singular values")
        .testFalse("'xxx'.empty()", "String is not empty")
        .testFalse("1.empty()", "Integer is not empty")
        .testFalse("true.empty()", "Boolean is not empty")
        .testFalse("5.5.empty()", "Decimal is not empty")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testEmptyOnCollections() {
    return builder()
        .group("empty() on collections")
        .testFalse("(1 ; 2).empty()", "2-element collection is not empty")
        .testFalse("(1 ; 2 ; 3).empty()", "3-element collection is not empty")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testEmptyAfterFiltering() {
    return builder()
        .group("empty() after filtering")
        .testTrue("(1 ; 2 ; 3).where($this > 5).empty()", "Empty after filtering all elements")
        .testFalse("(1 ; 2 ; 3).where($this > 1).empty()", "Not empty after partial filtering")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testEmptyChainedWithNot() {
    return builder()
        .group("empty() chained with not()")
        .testFalse("{}.empty().not()", "Empty collection: empty().not() is false")
        .testTrue("(1 ; 2).empty().not()", "Non-empty collection: empty().not() is true")
        .build();
  }

  // ========== exists() ==========

  @TestFactory
  Stream<DynamicTest> testExistsOnSingularValues() {
    return builder()
        .group("exists() on singular values")
        .testTrue("'xxx'.exists()", "String exists")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testExistsOnEmptyCollections() {
    return builder()
        .group("exists() on empty collections")
        .testFalse("{}.exists()", "Empty collection does not exist")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testExistsOnCollections() {
    return builder()
        .group("exists() on collections")
        .testTrue("(1 ; 2).exists()", "Collection exists")
        .build();
  }

  // ========== first() ==========

  @TestFactory
  Stream<DynamicTest> testFirstOnMultiElementCollections() {
    return builder()
        .group("first() on multi-element collections")
        .testEquals(1, "(1 ; 2 ; 3).first()", "Integer collection")
        .testEquals("a", "('a' ; 'b' ; 'c').first()", "String collection")
        .testEquals(5.2, "(5.2 ; 10.5 ; 15.3).first()", "Decimal collection")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testFirstOnEmptyCollection() {
    return builder()
        .group("first() on empty collection")
        .testEmpty("{}.first()", "Returns empty")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testFirstOnSingularValue() {
    return builder()
        .group("first() on singular value")
        .testEquals(5, "5.first()", "Integer singular value")
        .testEquals("hello", "'hello'.first()", "String singular value")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testFirstOnResourceFields() {
    return builder()
        .group("first() on resource fields")
        .withSubject(
            "Patient",
            p ->
                p.elementArray(
                    "name",
                    n -> n.string("family", "Szul").stringArray("given", "Piotr", "Jaroslaw"),
                    n -> n.string("family", "Brown").stringArray("given", "John", "Mark")))
        .testEquals("Szul", "name.first().family")
        // Chaining first() on nested collections
        .testEquals("Piotr", "name.first().given.first()")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testFirstWithNestedFieldNavigation() {
    return builder()
        .group("first() with nested field navigation")
        .withSubject(
            "Patient",
            p ->
                p.string("id", "id1")
                    .elementArray(
                        "name",
                        n ->
                            n.string("family", "Szul")
                                .stringArray("given", "Piotr", "Jaroslaw")
                                .string("use", "official"),
                        n ->
                            n.string("family", "Brown")
                                .stringArray("given", "John", "Mark")
                                .string("use", "alias"),
                        n -> {} // Empty name element
                        ))
        .testEquals("Szul", "name.first().family")
        .build();
  }
}
