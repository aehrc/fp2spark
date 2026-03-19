package com.example.fhirpath;

import com.example.fhirpath.test.FhirPathTestBase;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Tests for FHIRPath collection subsetting functions: last(), tail(), skip(), take(), single().
 *
 * <p>Based on FHIRPath specification section 5.2 (Filtering and projection).
 *
 * <p>Covers:
 *
 * <ul>
 *   <li>last() on multi-element collections, empty collections, and singular values
 *   <li>tail() on multi-element collections, single-element collections, empty, and singular values
 *   <li>skip(num) with positive, zero, negative, and boundary values
 *   <li>take(num) with positive, zero, negative, and boundary values
 *   <li>single() on single-element, empty, singular values, and error on multiple
 * </ul>
 */
public class SubsettingFunctionsTest extends FhirPathTestBase {

  // ========== last() ==========

  @TestFactory
  Stream<DynamicTest> testLast() {
    return builder()
        .group("last() core semantics")
        .testEquals(3, "(1 ; 2 ; 3).last()", "Integer collection")
        .testEquals("c", "('a' ; 'b' ; 'c').last()", "String collection")
        .group("last() empty collection")
        .testEmpty("{}.last()", "Returns empty")
        .group("last() singular value")
        .testEquals(5, "5.last()", "Singular returns itself")
        .build();
  }

  // ========== tail() ==========

  @TestFactory
  Stream<DynamicTest> testTail() {
    return builder()
        .group("tail() core semantics")
        .testEquals(List.of(2, 3), "(1 ; 2 ; 3).tail()", "Integer collection")
        .testEquals(List.of("b", "c"), "('a' ; 'b' ; 'c').tail()", "String collection")
        .group("tail() single element")
        .testEmpty("(1).tail()", "Single element returns empty")
        .group("tail() empty collection")
        .testEmpty("{}.tail()", "Returns empty")
        .group("tail() singular value")
        .testEmpty("5.tail()", "Singular returns empty")
        .build();
  }

  // ========== skip() ==========

  @TestFactory
  Stream<DynamicTest> testSkip() {
    return builder()
        .group("skip() core semantics")
        .testEquals(List.of(3, 4), "(1 ; 2 ; 3 ; 4).skip(2)", "Skip first 2")
        .group("skip() boundary: n=0")
        .testEquals(List.of(1, 2, 3), "(1 ; 2 ; 3).skip(0)", "Skip 0 returns all")
        .group("skip() boundary: n<0")
        .testEquals(List.of(1, 2, 3), "(1 ; 2 ; 3).skip(-1)", "Negative returns all")
        .group("skip() boundary: n=size")
        .testEmpty("(1 ; 2).skip(2)", "Skip all returns empty")
        .group("skip() boundary: n>size")
        .testEmpty("(1 ; 2).skip(5)", "Skip more than size returns empty")
        .group("skip() empty collection")
        .testEmpty("{}.skip(2)", "Empty input returns empty")
        .group("skip() singular value")
        .testEquals(List.of(5), "5.skip(0)", "Singular skip(0) returns element")
        .testEmpty("5.skip(1)", "Singular skip(1) returns empty")
        .build();
  }

  // ========== take() ==========

  @TestFactory
  Stream<DynamicTest> testTake() {
    return builder()
        .group("take() core semantics")
        .testEquals(List.of(1, 2), "(1 ; 2 ; 3 ; 4).take(2)", "Take first 2")
        .group("take() boundary: n=0")
        .testEmpty("(1 ; 2 ; 3).take(0)", "Take 0 returns empty")
        .group("take() boundary: n<0")
        .testEmpty("(1 ; 2 ; 3).take(-1)", "Negative returns empty")
        .group("take() boundary: n=size")
        .testEquals(List.of(1, 2), "(1 ; 2).take(2)", "Take all")
        .group("take() boundary: n>size")
        .testEquals(List.of(1, 2), "(1 ; 2).take(5)", "Take more than size returns all")
        .group("take() empty collection")
        .testEmpty("{}.take(2)", "Empty input returns empty")
        .group("take() singular value")
        .testEquals(List.of(5), "5.take(1)", "Singular take(1) returns element")
        .testEmpty("5.take(0)", "Singular take(0) returns empty")
        .build();
  }

  // ========== single() ==========

  @TestFactory
  Stream<DynamicTest> testSingle() {
    return builder()
        .group("single() core semantics")
        .testEquals(1, "(1).single()", "Single element returns it")
        .group("single() empty collection")
        .testEmpty("{}.single()", "Empty returns empty")
        .group("single() multiple elements — error")
        .testError(Exception.class, "(1 ; 2).single()", "Multiple elements signals error")
        .group("single() singular value")
        .testEquals(5, "5.single()", "Singular returns itself")
        .build();
  }
}
