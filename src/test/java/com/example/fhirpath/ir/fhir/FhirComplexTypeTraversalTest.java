package com.example.fhirpath.ir.fhir;

import com.example.fhirpath.test.FhirPathTestBase;
import com.example.fhirpath.typing.FhirPrimitiveType;
import com.example.fhirpath.typing.FieldSpec;
import com.example.fhirpath.typing.InlineComplexType;
import com.example.fhirpath.typing.InlineResourceType;
import com.example.fhirpath.typing.ResourceType;
import com.example.fhirpath.typing.Shape;
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
