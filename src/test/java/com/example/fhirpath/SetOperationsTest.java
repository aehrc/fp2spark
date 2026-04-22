package com.example.fhirpath;

import com.example.fhirpath.test.FhirPathTestBase;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Tests for FHIRPath set operations: {@code |} (union operator), {@code union()}, {@code
 * distinct()}, {@code isDistinct()}, {@code intersect()}, {@code exclude()}, {@code subsetOf()},
 * {@code supersetOf()}.
 *
 * <p>Based on FHIRPath specification sections 5.6.3, 5.6.4, and 6.6.
 *
 * <p>Covers:
 *
 * <ul>
 *   <li>Core semantics for each operation
 *   <li>Empty collection propagation
 *   <li>Singular value handling
 *   <li>Primitive type coverage (Integer, String, Boolean, Decimal)
 *   <li>Spec examples
 * </ul>
 */
class SetOperationsTest extends FhirPathTestBase {

  // ========== | (union operator) ==========

  @TestFactory
  Stream<DynamicTest> testUnionOperator() {
    return builder()
        .group("| operator: core semantics")
        .testEquals(List.of(1, 2, 3), "(1 ; 2) | (2 ; 3)", "Merge with deduplication")
        .testEquals(List.of(1, 2, 3), "(1 ; 1 ; 2) | (2 ; 3)", "Duplicates eliminated")
        .group("| operator: empty operands")
        .testEquals(List.of(1, 2), "(1 ; 2) | {}", "Right empty")
        .testEquals(List.of(1, 2), "{} | (1 ; 2)", "Left empty")
        .testEmpty("{} | {}", "Both empty")
        .group("| operator: singular values")
        .testEquals(List.of(1, 2), "1 | 2", "Two singular values")
        .testEquals(List.of(1), "1 | 1", "Same singular value deduplicated")
        .group("| operator: string type")
        .testEquals(List.of("a", "b", "c"), "('a' ; 'b') | ('b' ; 'c')", "String deduplication")
        .build();
  }

  // ========== union() function ==========

  @TestFactory
  Stream<DynamicTest> testUnionFunction() {
    return builder()
        .group("union(): core semantics (synonymous with |)")
        .testEquals(List.of(1, 2, 3), "(1 ; 2).union(2 ; 3)", "Merge with deduplication")
        .group("union(): spec example")
        .testEquals(List.of(1, 2, 3), "(1 ; 1 ; 2 ; 3).union(2 ; 3)", "A.union(B)")
        .testEquals(List.of(1, 2, 3), "(1 ; 1 ; 2 ; 3).union({})", "A.union({})")
        .group("union(): empty")
        .testEmpty("{}.union({})", "Both empty")
        .build();
  }

  // ========== distinct() ==========

  @TestFactory
  Stream<DynamicTest> testDistinct() {
    return builder()
        .group("distinct(): core semantics")
        .testEquals(List.of(1, 2, 3), "(1 ; 1 ; 2 ; 3).distinct()", "Removes duplicates")
        .testEquals(List.of(1, 2, 3), "(1 ; 2 ; 3).distinct()", "No duplicates unchanged")
        .group("distinct(): string type")
        .testEquals(List.of("a", "b"), "('a' ; 'b' ; 'a').distinct()", "String deduplication")
        .group("distinct(): empty")
        .testEmpty("{}.distinct()", "Empty returns empty")
        .group("distinct(): singular value")
        .testEquals(List.of(5), "5.distinct()", "Singular returns itself as collection")
        .build();
  }

  // ========== isDistinct() ==========

  @TestFactory
  Stream<DynamicTest> testIsDistinct() {
    return builder()
        .group("isDistinct(): core semantics")
        .testTrue("(1 ; 2 ; 3).isDistinct()", "All distinct")
        .testFalse("(1 ; 2 ; 1).isDistinct()", "Has duplicates")
        .group("isDistinct(): empty")
        .testTrue("{}.isDistinct()", "Empty returns true")
        .group("isDistinct(): singular value")
        .testTrue("5.isDistinct()", "Singular is always distinct")
        .group("isDistinct(): string type")
        .testTrue("('a' ; 'b').isDistinct()", "Distinct strings")
        .testFalse("('a' ; 'a').isDistinct()", "Duplicate strings")
        .build();
  }

  // ========== intersect() ==========

  @TestFactory
  Stream<DynamicTest> testIntersect() {
    return builder()
        .group("intersect(): core semantics")
        .testEquals(List.of(2, 3), "(1 ; 2 ; 3).intersect(2 ; 3 ; 4)", "Common elements")
        .testEmpty("(1 ; 2).intersect(3 ; 4)", "No common elements")
        .group("intersect(): duplicates eliminated")
        .testEquals(List.of(2), "(1 ; 2 ; 2).intersect(2 ; 2 ; 3)", "Duplicates removed")
        .group("intersect(): empty operands")
        .testEmpty("(1 ; 2).intersect({})", "Right empty")
        .testEmpty("{}.intersect(1 ; 2)", "Left empty")
        .testEmpty("{}.intersect({})", "Both empty")
        .group("intersect(): singular values")
        .testEquals(List.of(1), "1.intersect(1)", "Same singular")
        .testEmpty("1.intersect(2)", "Different singular")
        .group("intersect(): string type")
        .testEquals(List.of("b"), "('a' ; 'b').intersect('b' ; 'c')", "String intersection")
        .build();
  }

  // ========== exclude() ==========

  @TestFactory
  Stream<DynamicTest> testExclude() {
    return builder()
        .group("exclude(): core semantics")
        .testEquals(List.of(1), "(1 ; 2 ; 3).exclude(2 ; 3)", "Remove matching elements")
        .testEquals(List.of(1, 2, 3), "(1 ; 2 ; 3).exclude(4 ; 5)", "Nothing to exclude")
        .group("exclude(): spec example")
        .testEquals(List.of(1, 3), "(1 | 2 | 3).exclude(2)", "Spec: (1|2|3).exclude(2)")
        .group("exclude(): empty operands")
        .testEquals(List.of(1, 2), "(1 ; 2).exclude({})", "Right empty returns input")
        .testEmpty("{}.exclude(1 ; 2)", "Left empty")
        .testEmpty("{}.exclude({})", "Both empty")
        .group("exclude(): singular values")
        .testEmpty("1.exclude(1)", "Exclude same singular")
        .testEquals(List.of(1), "1.exclude(2)", "Exclude different singular")
        .group("exclude(): string type")
        .testEquals(List.of("a"), "('a' ; 'b').exclude('b' ; 'c')", "String exclusion")
        .build();
  }

  // ========== subsetOf() ==========

  @TestFactory
  Stream<DynamicTest> testSubsetOf() {
    return builder()
        .group("subsetOf(): core semantics")
        .testTrue("(1 ; 2).subsetOf(1 ; 2 ; 3)", "Is subset")
        .testFalse("(1 ; 4).subsetOf(1 ; 2 ; 3)", "Not subset")
        .testTrue("(1 ; 2 ; 3).subsetOf(1 ; 2 ; 3)", "Equal sets")
        .group("subsetOf(): empty operands")
        .testTrue("{}.subsetOf(1 ; 2)", "Empty input → true")
        .testFalse("(1 ; 2).subsetOf({})", "Empty other → false")
        .testTrue("{}.subsetOf({})", "Both empty → true")
        .group("subsetOf(): singular values")
        .testTrue("1.subsetOf(1 ; 2 ; 3)", "Singular in collection")
        .testFalse("4.subsetOf(1 ; 2 ; 3)", "Singular not in collection")
        .group("subsetOf(): string type")
        .testTrue("('a').subsetOf('a' ; 'b')", "String subset")
        .testFalse("('c').subsetOf('a' ; 'b')", "String not subset")
        .build();
  }

  // ========== supersetOf() ==========

  @TestFactory
  Stream<DynamicTest> testSupersetOf() {
    return builder()
        .group("supersetOf(): core semantics")
        .testTrue("(1 ; 2 ; 3).supersetOf(1 ; 2)", "Is superset")
        .testFalse("(1 ; 2).supersetOf(1 ; 2 ; 3)", "Not superset")
        .testTrue("(1 ; 2 ; 3).supersetOf(1 ; 2 ; 3)", "Equal sets")
        .group("supersetOf(): empty operands")
        .testTrue("(1 ; 2).supersetOf({})", "Empty other → true")
        .testFalse("{}.supersetOf(1 ; 2)", "Empty input → false")
        .testTrue("{}.supersetOf({})", "Both empty → true")
        .group("supersetOf(): singular values")
        .testTrue("(1 ; 2 ; 3).supersetOf(1)", "Collection contains singular")
        .testFalse("(1 ; 2 ; 3).supersetOf(4)", "Collection does not contain singular")
        .group("supersetOf(): string type")
        .testTrue("('a' ; 'b').supersetOf('a')", "String superset")
        .testFalse("('a' ; 'b').supersetOf('c')", "String not superset")
        .build();
  }

  // ========== Decimal field type normalization ==========

  @TestFactory
  Stream<DynamicTest> testSetOpsWithDecimalFields() {
    return builder()
        .withSubject(
            "Test",
            sb ->
                sb.decimalArray("dec12", 1.1, 2.2)
                    .decimalArray("dec23", 2.2, 3.3)
                    .decimalEmpty("emptyDec"))
        .group("union: decimal literal vs field")
        .testEquals(2.5, "2.5 | emptyDec", "Literal union empty decimal field")
        .testEquals(2.5, "emptyDec | 2.5", "Empty decimal field union literal")
        .testEmpty("emptyDec | emptyDec", "Empty decimal fields")
        .testEquals(List.of(1.1, 2.2, 3.3), "dec12 | dec23", "Decimal field union with overlap")
        .group("intersect: decimal field")
        .testEquals(2.2, "dec12.intersect(dec23)", "Decimal field intersection")
        .testEmpty("dec12.intersect(emptyDec)", "Decimal field intersect empty")
        .group("exclude: decimal field")
        .testEquals(1.1, "dec12.exclude(dec23)", "Decimal field exclusion")
        .testEquals(List.of(1.1, 2.2), "dec12.exclude(emptyDec)", "Decimal field exclude empty")
        .group("subsetOf/supersetOf: decimal field")
        .testTrue("emptyDec.subsetOf(dec12)", "Empty is subset of decimal field")
        .testTrue("dec12.supersetOf(emptyDec)", "Decimal field is superset of empty")
        .build();
  }

  // ========== Quantity equality in set operations ==========

  @TestFactory
  Stream<DynamicTest> testSetOpsWithQuantity() {
    return builder()
        .group("union: calendar duration singular/plural dedup")
        .testEquals(1, "(2 second | 2 seconds).count()", "second/seconds deduplicated")
        .testEquals(1, "(3 day | 3 days).count()", "day/days deduplicated")
        .group("distinct: calendar duration dedup")
        .testEquals(1, "(1 year ; 1 years).distinct().count()", "year/years deduplicated")
        .group("isDistinct: calendar duration")
        .testFalse("(2 month ; 2 months).isDistinct()", "month/months are not distinct")
        .testTrue("(1 day ; 2 days).isDistinct()", "Different values are distinct")
        .group("intersect: calendar duration")
        .testEquals(1, "(5 hour ; 10 hours).intersect(5 hours).count()", "hour/hours intersect")
        .testEmpty("(5 hour).intersect(10 hours)", "Different values no intersection")
        .group("subsetOf/supersetOf: calendar duration")
        .testTrue("(2 minute).subsetOf(2 minutes ; 3 minutes)", "minute/minutes subset")
        .testTrue("(2 seconds ; 3 seconds).supersetOf(2 second)", "second/seconds superset")
        .group("exclude: calendar duration")
        .testEquals(
            1, "(1 week ; 2 weeks).exclude(1 weeks).count()", "week/weeks excluded correctly")
        .group("union/intersect: year ↔ month spec-exact conversion (1 year = 12 months)")
        .testEquals(1, "(1 year | 12 months).count()", "1 year and 12 months deduplicate")
        .testEquals(1, "(2 years | 24 months).count()", "2 years and 24 months deduplicate")
        .testEquals(2, "(1 year | 11 months).count()", "1 year and 11 months stay distinct")
        .testEquals(
            1,
            "(1 year ; 12 months).intersect(12 months).count()",
            "Intersect bridges year ↔ month")
        .testEquals(
            0,
            "(1 year ; 12 months).distinct().exclude(1 year).count()",
            "Exclude bridges year ↔ month")
        .build();
  }

  // ========== DateTime equality in set operations ==========

  @TestFactory
  Stream<DynamicTest> testSetOpsWithDateTime() {
    return builder()
        .group("union: same instant different timezone offset")
        .testEquals(
            1,
            "(@2017-11-05T01:30:00.0-04:00 | @2017-11-05T00:30:00.0-05:00).count()",
            "Same instant deduplicated")
        .testEquals(
            2,
            "(@2017-11-05T01:30:00.0-04:00 | @2017-11-05T01:15:00.0-05:00).count()",
            "Different instants not deduplicated")
        .group("distinct: same instant different offset")
        .testEquals(
            1,
            "(@2017-11-05T01:30:00.0-04:00 ; @2017-11-05T00:30:00.0-05:00).distinct().count()",
            "Same instant deduplicated by distinct")
        .group("isDistinct: same instant different offset")
        .testFalse(
            "(@2017-11-05T01:30:00.0-04:00 ; @2017-11-05T00:30:00.0-05:00).isDistinct()",
            "Same instant not distinct")
        .testTrue(
            "(@2017-11-05T01:30:00.0-04:00 ; @2017-11-05T01:15:00.0-05:00).isDistinct()",
            "Different instants are distinct")
        .group("intersect: same instant different offset")
        .testEquals(
            1,
            "(@2017-11-05T01:30:00.0-04:00).intersect(@2017-11-05T00:30:00.0-05:00).count()",
            "Same instant found in intersection")
        .testEmpty(
            "(@2017-11-05T01:30:00.0-04:00).intersect(@2017-11-05T01:15:00.0-05:00)",
            "Different instants not in intersection")
        .group("subsetOf/supersetOf: same instant different offset")
        .testTrue(
            "(@2017-11-05T01:30:00.0-04:00).subsetOf(@2017-11-05T00:30:00.0-05:00)",
            "Same instant recognized as subset")
        .testTrue(
            "(@2017-11-05T00:30:00.0-05:00).supersetOf(@2017-11-05T01:30:00.0-04:00)",
            "Same instant recognized as superset")
        .build();
  }
}
