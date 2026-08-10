/*
 * Copyright © 2025-2026 Commonwealth Scientific and Industrial Research
 * Organisation (CSIRO) ABN 41 687 119 230.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package au.csiro.fhirpath;

import au.csiro.fhirpath.test.FhirPathTestBase;
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
        .testEmpty("{} ; {}", "Empty ; Empty")
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
  Stream<DynamicTest> testIncompatibleTypes() {
    return builder()
        .group("Incompatible types")
        .testError(
            IllegalArgumentException.class, "1 ; 'xxx'", "Integer ; String is type-incompatible")
        .testError(
            IllegalArgumentException.class, "true ; 1", "Boolean ; Integer is type-incompatible")
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
