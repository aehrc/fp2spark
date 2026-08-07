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

import au.csiro.fhirpath.analyzer.CardinalityMismatchException;
import au.csiro.fhirpath.test.FhirPathTestBase;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Tests for FHIRPath membership operators ({@code in} and {@code contains}).
 *
 * <p>Based on FHIRPath specification section 6.5 (Membership).
 *
 * <p>{@code element in collection} returns true if the element equals any item in the collection.
 * {@code collection contains element} is the converse. Both use equality ({@code =}) semantics.
 *
 * <p>Covers:
 *
 * <ul>
 *   <li>Core semantics: element found / not found
 *   <li>All primitive types (Integer, Decimal, String, Boolean)
 *   <li>Empty propagation: empty element -> empty, empty collection -> false, both empty -> empty
 *   <li>Incompatible types -> false
 *   <li>Cross-type numeric coercion (Integer/Decimal)
 *   <li>Singular collection (element treated as one-element collection)
 *   <li>Quantity equality (same unit, different unit)
 *   <li>DateTime equality (same precision, different precision)
 *   <li>Resource field membership (spec examples)
 *   <li>Cardinality errors (MANY element -> exception)
 * </ul>
 */
class MembershipOperatorsTest extends FhirPathTestBase {

  // ========== in operator ==========

  @TestFactory
  Stream<DynamicTest> testIn() {
    return builder()
        .group("in: core semantics")
        .testTrue("1 in (1;2;3)", "Element found in collection")
        .testFalse("4 in (1;2;3)", "Element not found in collection")
        .group("in: primitive types")
        .testTrue("'b' in ('a';'b';'c')", "String found")
        .testFalse("'z' in ('a';'b';'c')", "String not found")
        .testTrue("true in (true;false)", "Boolean found")
        .testTrue("1.5 in (1.0;1.5;2.0)", "Decimal found")
        .testFalse("3.0 in (1.0;1.5;2.0)", "Decimal not found")
        .group("in: empty propagation")
        .testEmpty("{} in (1;2;3)", "Empty element returns empty")
        .testFalse("1 in {}", "Empty collection returns false")
        .testEmpty("{} in {}", "Both empty returns empty (element emptiness dominates)")
        .group("in: incompatible types")
        .testFalse("1 in ('a';'b')", "Integer in String collection")
        .testFalse("true in (1;2)", "Boolean in Integer collection")
        .group("in: cross-type numeric coercion")
        .testTrue("1 in (1.0;2.0)", "Integer found in Decimal collection")
        .testFalse("3 in (1.0;2.0)", "Integer not found in Decimal collection")
        .group("in: singular collection")
        .testTrue("1 in 1", "Element equals singular collection")
        .testFalse("2 in 1", "Element does not equal singular collection")
        .group("in: Quantity equality")
        .testTrue("10 'mg' in (5 'mg';10 'mg')", "Same unit, value found")
        .testFalse("15 'mg' in (5 'mg';10 'mg')", "Same unit, value not found")
        .testFalse(
            "10 'mg' in (10 'kg';20 'kg')", "Same dimension, value not found after conversion")
        .group("in: DateTime equality")
        .testTrue("@2014-01-25 in (@2014-01-25;@2014-01-26)", "Same precision, found")
        .testFalse("@2014-01-27 in (@2014-01-25;@2014-01-26)", "Same precision, not found")
        .testEmpty("@2014 in (@2014-01;@2015-01)", "Different precision returns empty")
        .group("in: resource field (spec example)")
        .withSubject(
            "Patient", p -> p.elementArray("name", n -> n.stringArray("given", "Joe", "Jane")))
        .testTrue("'Joe' in name.given", "Spec example: 'Joe' in Patient.name.given")
        .testFalse("'Bob' in name.given", "Element not in resource field array")
        .group("in: MANY element cardinality error")
        .testError(
            CardinalityMismatchException.class,
            "(1;2) in (1;2;3)",
            "MANY element throws CardinalityMismatchException")
        .build();
  }

  // ========== contains operator ==========

  @TestFactory
  Stream<DynamicTest> testContains() {
    return builder()
        .group("contains: core semantics")
        .testTrue("(1;2;3) contains 1", "Element found in collection")
        .testFalse("(1;2;3) contains 4", "Element not found in collection")
        .group("contains: primitive types")
        .testTrue("('a';'b';'c') contains 'b'", "String found")
        .testFalse("('a';'b';'c') contains 'z'", "String not found")
        .testTrue("(true;false) contains true", "Boolean found")
        .testTrue("(1.0;1.5;2.0) contains 1.5", "Decimal found")
        .group("contains: empty propagation")
        .testEmpty("(1;2;3) contains {}", "Empty element returns empty")
        .testFalse("{} contains 1", "Empty collection returns false")
        .testEmpty("{} contains {}", "Both empty returns empty (element emptiness dominates)")
        .group("contains: incompatible types")
        .testFalse("('a';'b') contains 1", "String collection contains Integer")
        .group("contains: cross-type numeric coercion")
        .testTrue("(1.0;2.0) contains 1", "Decimal collection contains Integer")
        .testFalse("(1.0;2.0) contains 3", "Decimal collection does not contain Integer")
        .group("contains: resource field (spec example)")
        .withSubject(
            "Patient", p -> p.elementArray("name", n -> n.stringArray("given", "Joe", "Jane")))
        .testTrue("name.given contains 'Joe'", "Spec example: Patient.name.given contains 'Joe'")
        .testFalse("name.given contains 'Bob'", "Element not in resource field array")
        .group("contains: MANY element cardinality error")
        .testError(
            CardinalityMismatchException.class,
            "(1;2;3) contains (1;2)",
            "MANY element throws CardinalityMismatchException")
        .build();
  }
}
