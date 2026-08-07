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
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Tests for FHIRPath indexer operator ([]).
 *
 * <p>Based on FHIRPath specification section 5.1.1: Indexer expression
 *
 * <p>The indexer operator returns the index-th item (0-based) from a collection. Returns empty
 * collection for empty input or out-of-bounds index.
 *
 * <p>Covers: - Basic 0-based indexing - Out-of-bounds (returns empty) - Negative index (returns
 * empty) - Empty collection indexing - Expression as index - Singular value indexing
 */
public class IndexerTest extends FhirPathTestBase {

  @TestFactory
  Stream<DynamicTest> testBasicIndexing() {
    return builder()
        .group("Basic indexing")
        .testEquals(1, "(1 ; 2 ; 3)[0]", "First element")
        .testEquals(2, "(1 ; 2 ; 3)[1]", "Second element")
        .testEquals(3, "(1 ; 2 ; 3)[2]", "Third element")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testOutOfBounds() {
    return builder()
        .group("Out-of-bounds index")
        .testEmpty("(1 ; 2 ; 3)[3]", "Index equals size")
        .testEmpty("(1 ; 2 ; 3)[5]", "Index exceeds size")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testNegativeIndex() {
    return builder()
        .group("Negative index")
        .testEmpty("(1 ; 2)[(-1)]", "Negative index returns empty")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testEmptyCollection() {
    return builder()
        .group("Empty collection")
        .testEmpty("{}[0]", "Index into empty collection")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testExpressionIndex() {
    return builder()
        .group("Expression as index")
        .testEquals(3, "(1 ; 2 ; 3)[1 + 1]", "Arithmetic expression as index")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testNullIndex() {
    return builder()
        .group("Null index")
        .testEmpty("(1 ; 2 ; 3)[{}]", "Null/empty index returns empty")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testSingularValue() {
    return builder()
        .group("Singular value indexing")
        .testEquals(5, "5[0]", "Index 0 of singular value")
        .testEmpty("5[1]", "Index 1 of singular value returns empty")
        .build();
  }
}
