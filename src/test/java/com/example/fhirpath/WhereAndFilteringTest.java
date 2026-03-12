package com.example.fhirpath;

import com.example.fhirpath.test.FhirPathTestBase;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Tests for FHIRPath where() function and exists(criteria) function.
 *
 * <p>Based on FHIRPath specification section 5.2 (Filtering and projection).
 *
 * <p>Covers:
 *
 * <ul>
 *   <li>where() with $this reference on literal collections
 *   <li>where() edge cases (empty collection, singular value, empty lambda)
 *   <li>where() on resource fields with equality and comparison operators
 *   <li>where() with explicit $this on resource fields
 *   <li>Chained where() clauses
 *   <li>where() with nested field access
 *   <li>where() preserves collection structure
 *   <li>exists(criteria) on literal and resource collections
 *   <li>exists(criteria) with comparison operators
 *   <li>exists(criteria) on empty collection
 *   <li>Nested exists(criteria)
 * </ul>
 */
public class WhereAndFilteringTest extends FhirPathTestBase {

  // ========== where() on literal collections ==========

  @TestFactory
  Stream<DynamicTest> testWhereWithThisReference() {
    return builder()
        .group("where() with $this reference")
        .testEquals(List.of(2, 3), "(1 ; 2 ; 3).where($this > 1)", "Filter integers > 1")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testWhereEdgeCases() {
    return builder()
        .group("where() edge cases")
        .testEmpty("{}.where($this > 1)", "where() on empty collection")
        .testEquals(2, "2.where($this > 1)", "where() on singular value (match)")
        .testEmpty("'foo'.where($this = 'bar')", "where() on singular value (no match)")
        .testEmpty("(1 ; 2 ; 3).where({})", "where() with empty lambda")
        .build();
  }

  // ========== where() on resource fields ==========

  @TestFactory
  Stream<DynamicTest> testWhereOnResourceFields() {
    return builder()
        .group("where() on resource fields")
        .withSubject(
            "Patient",
            p ->
                p.elementArray(
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
        // Basic filtering with equality
        .testEquals("Szul", "name.where(use = 'official').family.first()")
        .testEquals("Brown", "name.where(use = 'alias').family.first()")
        // Filtering that returns empty when no match
        .testFalse("name.where(use = 'nonexistent').exists()")
        .testEquals(0, "name.where(family = 'Unknown').count()")
        // where() with implicit $this in criteria
        .testEquals("Piotr", "name.where(family = 'Szul').given.first().first()")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testWhereWithExplicitThis() {
    return builder()
        .group("where() with explicit $this")
        .withSubject(
            "Patient",
            p ->
                p.elementArray(
                    "name",
                    n -> n.stringArray("given", "Piotr", "Jaroslaw"),
                    n -> n.stringArray("given", "John", "Mark")))
        .testEquals("John", "name.given.where($this = 'John').first()")
        .testEquals("Piotr", "name.given.where($this > 'K').first()")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testChainedWhere() {
    return builder()
        .group("Chained where() clauses")
        .withSubject(
            "Patient",
            p ->
                p.elementArray(
                    "name",
                    n -> n.string("family", "Szul").string("use", "official"),
                    n -> n.string("family", "Brown").string("use", "alias")))
        .testTrue("name.where(use = 'official').where(family = 'Szul').exists()")
        .testFalse("name.where(use = 'official').where(family = 'Brown').exists()")
        // where() on empty input collection returns empty
        .testEquals(0, "name.where(family = 'Unknown').where(use = 'official').count()")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testWhereWithNestedFieldAccess() {
    return builder()
        .group("where() with nested field access")
        .withSubject(
            "Patient",
            p ->
                p.elementArray(
                    "name",
                    n -> n.string("family", "Szul").stringArray("given", "Piotr", "Jaroslaw"),
                    n -> n.string("family", "Brown").stringArray("given", "John", "Mark")))
        .testEquals(2, "name.where(given.exists()).count()")
        .testEquals("Szul", "name.where(given.count() > 1).family.first()")
        // Nested where() with implicit $this in both levels
        .testEquals("Szul", "name.where(given.where($this = 'Piotr').exists()).family.first()")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testWherePreservesCollectionStructure() {
    return builder()
        .group("where() preserves collection structure")
        .withSubject(
            "Patient",
            p ->
                p.elementArray(
                    "name", n -> n.string("use", "official"), n -> n.string("use", "alias")))
        .testEquals(1, "name.where(use = 'official').count()")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testWhereWithComparisonOperators() {
    return builder()
        .group("where() with comparison operators")
        .withSubject(
            "Patient",
            p ->
                p.elementArray(
                    "name",
                    n -> n.stringArray("given", "Piotr", "Jaroslaw"),
                    n -> n.stringArray("given", "John", "Mark")))
        .testEquals("Jaroslaw", "name.given.where($this < 'K').first()")
        .testEquals(3, "name.given.where($this >= 'John').count()")
        .build();
  }

  // ========== exists(criteria) on literal collections ==========

  @TestFactory
  Stream<DynamicTest> testExistsWithCriteriaOnLiterals() {
    return builder()
        .group("exists(criteria) on empty collection")
        .testFalse("{}.exists($this > 1)", "Empty collection returns false")
        .group("exists(criteria) on singular values")
        .testTrue("2.exists($this > 1)", "Singular value matching")
        .testFalse("'foo'.exists($this = 'bar')", "Singular value not matching")
        .group("exists(criteria) on collections")
        .testTrue("(1 ; 2 ; 3).exists($this > 1)", "Collection with matches")
        .testFalse("(1 ; 2 ; 3).exists($this > 5)", "Collection without matches")
        .testTrue("('a' ; 'b' ; 'c').exists($this = 'b')", "String collection with match")
        .build();
  }

  // ========== exists(criteria) on resource fields ==========

  @TestFactory
  Stream<DynamicTest> testExistsWithCriteriaOnResources() {
    return builder()
        .group("exists(criteria) function on resources")
        .withSubject(
            "Patient",
            p ->
                p.elementArray(
                    "name",
                    n ->
                        n.string("family", "Szul")
                            .stringArray("given", "Piotr", "Jaroslaw")
                            .string("use", "official"),
                    n -> n.string("family", "Brown").string("use", "alias")))
        // exists(criteria) with equality
        .testTrue("name.exists(use = 'official')")
        .testFalse("name.exists(use = 'nonexistent')")
        .testTrue("name.exists(family = 'Szul')")
        .testFalse("name.exists(family = 'Unknown')")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testExistsWithComparisonOperators() {
    return builder()
        .group("exists(criteria) with comparison operators")
        .withSubject(
            "Patient",
            p ->
                p.elementArray(
                    "name",
                    n -> n.stringArray("given", "Piotr", "Jaroslaw"),
                    n -> n.stringArray("given", "John", "Mark")))
        .testTrue("name.given.exists($this > 'K')")
        .testFalse("name.given.exists($this > 'Z')")
        .testTrue("name.given.exists($this = 'Piotr')")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testExistsOnEmptyFilteredCollection() {
    return builder()
        .group("exists(criteria) on empty filtered collection")
        .withSubject("Patient", p -> p.elementArray("name", n -> n.string("family", "Szul")))
        .testFalse("name.where(family = 'Unknown').exists(use = 'official')")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testNestedExists() {
    return builder()
        .group("Nested exists(criteria)")
        .withSubject(
            "Patient",
            p ->
                p.elementArray(
                    "name",
                    n -> n.stringArray("given", "Piotr", "Jaroslaw"),
                    n -> n.stringArray("given", "John", "Mark")))
        .testTrue("name.exists(given.exists())")
        .testTrue("name.exists(given.count() > 1)")
        .testTrue("name.given.exists($this = 'John')")
        .build();
  }
}
