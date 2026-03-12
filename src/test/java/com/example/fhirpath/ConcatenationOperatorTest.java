package com.example.fhirpath;

import com.example.fhirpath.test.FhirPathTestBase;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Tests for FHIRPath concatenation operator (;) aka combine operator.
 *
 * <p>Based on FHIRPath specification section 6.6 (Collections).
 *
 * <p>Covers:
 *
 * <ul>
 *   <li>Integer, Decimal, String concatenation
 *   <li>Concatenation with empty collections
 *   <li>Preserves order and duplicates
 *   <li>Nested concatenation (flattens)
 * </ul>
 */
public class ConcatenationOperatorTest extends FhirPathTestBase {

  @TestFactory
  Stream<DynamicTest> testIntegerConcatenation() {
    return builder()
        .group("Integer concatenation")
        .testEquals(List.of(5, 10), "5 ; 10", "Two integers")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testDecimalConcatenation() {
    return builder()
        .group("Decimal concatenation")
        .testEquals(List.of(5.2, 10.5), "5.2 ; 10.5", "Two decimals")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testStringConcatenation() {
    return builder()
        .group("String concatenation")
        .testEquals(List.of("a", "b", "c"), "'a' ; 'b' ; 'c'", "Three strings")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testConcatenationWithEmptyCollections() {
    return builder()
        .group("Concatenation with empty collections")
        .testEquals(List.of(1), "1 ; {}", "Integer ; Empty")
        .testEquals(List.of(1), "{} ; 1", "Empty ; Integer")
        // DISABLED: testEmpty("{} ; {}", "Empty ; Empty")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testPreservesOrderAndDuplicates() {
    return builder()
        .group("Preserves order and duplicates")
        .testEquals(List.of(true, false, true), "true ; false ; true", "Boolean with duplicate")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testNestedConcatenationFlattens() {
    return builder()
        .group("Nested concatenation (flattens)")
        .testEquals(List.of(1.1, 2, 3), "1.1 ; (2 ; 3)", "Decimal ; (Integer ; Integer)")
        .testEquals(
            List.of(2, 3, 1.1, 2.3, 2.0),
            "(2 ; 3) ; (1.1 ; 2.3 ; 2.0)",
            "Two collections concatenated")
        .build();
  }
}
