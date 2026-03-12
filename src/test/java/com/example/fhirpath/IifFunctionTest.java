package com.example.fhirpath;

import com.example.fhirpath.test.FhirPathTestBase;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Tests for FHIRPath iif() function.
 *
 * <p>Based on FHIRPath specification section 5.1 (Utility functions).
 *
 * <p>Covers:
 *
 * <ul>
 *   <li>iif() with literal boolean criterion
 *   <li>iif() with collection-level criterion (implicit $this)
 *   <li>iif() with lambda as true-result
 *   <li>iif() nested
 *   <li>iif() with different result types
 *   <li>iif() combined with other operations
 * </ul>
 */
public class IifFunctionTest extends FhirPathTestBase {

  @TestFactory
  Stream<DynamicTest> testIifWithLiteralBooleanCriterion() {
    return builder()
        .group("iif() with literal boolean criterion")
        .testEquals("true", "{}.iif(true, 'true')", "Empty collection, true criterion")
        .testEmpty("{}.iif(false, 'true')", "Empty collection, false criterion")
        .testEquals("found", "5.iif(true, 'found')", "Singular value, true criterion")
        .testEmpty("5.iif(false, 'found')", "Singular value, false criterion")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testIifWithCollectionLevelCriterion() {
    return builder()
        .group("iif() with collection-level criterion (implicit $this)")
        .testEquals(List.of(1, 2), "(1 ; 2).iif(exists(), $this)", "implicit exists()")
        .testEmpty("(1 ; 2).iif(empty(), $this)", "implicit empty()")
        .testEquals(List.of(1, 2, 3), "(1 ; 2 ; 3).iif(count() > 2, $this)", "implicit count() > 2")
        .testEmpty("(1 ; 2).iif(count() > 2, $this)", "count criterion false")
        .testEquals(1, "(1 ; 2 ; 3).iif(count() = 3, first())", "implicit in both lambdas")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testIifWithLambdaAsTrueResult() {
    return builder()
        .group("iif() with lambda as true-result")
        .testEquals(3, "(5 ; 10 ; 15).iif(exists(), count())", "implicit in both")
        .testEquals(
            List.of(3, 4),
            "(1 ; 2 ; 3 ; 4).iif(count() > 2, where($this > 2))",
            "implicit count(), explicit $this in where")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testIifNested() {
    return builder()
        .group("iif() nested")
        .testEquals("nested", "(1 ; 2).iif(iif(exists(), true), 'nested')", "nested in criterion")
        .testEquals("match", "(1 ; 2).iif(true, iif(count() = 2, 'match'))", "nested in result")
        .testEquals("deep", "5.iif(true, 10.iif(true, 'deep'))", "double nested result")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testIifWithDifferentResultTypes() {
    return builder()
        .group("iif() with different result types")
        .testEquals("found", "(1 ; 2 ; 3).iif(count() > 2, 'found')", "returns string")
        .testEquals(999, "('a' ; 'b').iif(exists(), 999)", "returns integer")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testIifCombinedWithOtherOperations() {
    return builder()
        .group("iif() combined with other operations")
        .testEquals(3, "(1 ; 2 ; 3).iif(exists(), $this).count()", "chained with count()")
        .testEquals(8, "(5 ; 10).iif(count() = 2, first()) + 3", "result used in arithmetic")
        .build();
  }
}
