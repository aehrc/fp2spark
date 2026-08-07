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
package au.csiro.fhirpath.ir.fhir;

import au.csiro.fhirpath.test.FhirPathTestBase;
import au.csiro.fhirpath.typing.FhirPrimitiveType;
import au.csiro.fhirpath.typing.FieldSpec;
import au.csiro.fhirpath.typing.InlineComplexType;
import au.csiro.fhirpath.typing.InlineResourceType;
import au.csiro.fhirpath.typing.ResourceType;
import au.csiro.fhirpath.typing.Shape;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Integration tests for FHIR complex type traversal with Spark.
 *
 * <p>Verifies:
 *
 * <ul>
 *   <li>Patient with HumanName: {@code name.family}, {@code name.given}
 *   <li>MANY cardinality traversal with flattening
 *   <li>Nested complex types
 *   <li>Filtering: {@code name.where(use = 'official').family}
 * </ul>
 */
class FhirComplexTypeTraversalTest extends FhirPathTestBase {

  private static ResourceType patientWithNameType() {
    final InlineComplexType humanNameType =
        new InlineComplexType(
            "HumanName",
            List.of(
                new FieldSpec("family", Shape.single(FhirPrimitiveType.of("string"))),
                new FieldSpec("given", Shape.many(FhirPrimitiveType.of("string"))),
                new FieldSpec("use", Shape.single(FhirPrimitiveType.of("code")))));

    return new InlineResourceType(
        "Patient",
        new FieldSpec("id", Shape.single(FhirPrimitiveType.of("id"))),
        new FieldSpec("name", Shape.many(humanNameType)));
  }

  @TestFactory
  Stream<DynamicTest> testNameFamilyTraversal() {
    return builder()
        .withSubject(
            patientWithNameType(),
            sb ->
                sb.string("id", "patient-1")
                    .elementArray(
                        "name",
                        n ->
                            n.string("family", "Smith")
                                .stringArray("given", "John", "James")
                                .string("use", "official"),
                        n ->
                            n.string("family", "Doe")
                                .stringArray("given", "Jane")
                                .string("use", "nickname")))
        .group("Complex type traversal")
        .testEquals(
            List.of("Smith", "Doe"), "name.family", "Traverse MANY → SINGLE yields flat list")
        .testEquals(
            List.of("John", "James", "Jane"),
            "name.given",
            "Traverse MANY → MANY yields flattened list")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testFilteredNameTraversal() {
    return builder()
        .withSubject(
            patientWithNameType(),
            sb ->
                sb.string("id", "patient-1")
                    .elementArray(
                        "name",
                        n ->
                            n.string("family", "Smith")
                                .stringArray("given", "John")
                                .string("use", "official"),
                        n ->
                            n.string("family", "Doe")
                                .stringArray("given", "Jane")
                                .string("use", "nickname")))
        .group("Filtered complex type traversal")
        .testEquals(
            List.of("Smith"),
            "name.where(use = 'official').family",
            "Filter then traverse yields filtered result")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testEmptyNestedCollectionTraversal() {
    return builder()
        .withSubject(
            patientWithNameType(),
            sb -> sb.string("id", "p1").elementArray("name", n -> n.string("family", "Smith")))
        .group("Empty nested collection")
        .testFalse("name.given.exists()")
        .testTrue("name.given.empty()")
        .testEquals(0, "name.given.count()")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testSingleNameTraversal() {
    return builder()
        .withSubject(
            patientWithNameType(),
            sb ->
                sb.string("id", "patient-1")
                    .elementArray(
                        "name",
                        n ->
                            n.string("family", "Only")
                                .stringArray("given", "One")
                                .string("use", "official")))
        .group("Single element complex type")
        .testEquals(List.of("Only"), "name.family", "Single name yields single-element list")
        .build();
  }
}
