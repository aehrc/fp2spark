package com.example.fhirpath.ir.fhir;

import ca.uhn.fhir.context.FhirContext;
import com.example.fhirpath.test.FhirPathTestBase;
import com.example.fhirpath.typing.ComplexType;
import com.example.fhirpath.typing.FhirPrimitiveType;
import com.example.fhirpath.typing.FieldSpec;
import com.example.fhirpath.typing.HapiTypeResolver;
import com.example.fhirpath.typing.ResourceType;
import com.example.fhirpath.typing.Shape;
import com.example.fhirpath.typing.TypeResolver;
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

  private static final TypeResolver HAPI_RESOLVER = new HapiTypeResolver(FhirContext.forR4Cached());

  private static ResourceType patientWithNameType() {
    final ComplexType humanNameType =
        new ComplexType(
            "HumanName",
            List.of(
                new FieldSpec("family", Shape.single(FhirPrimitiveType.of("string"))),
                new FieldSpec("given", Shape.many(FhirPrimitiveType.of("string"))),
                new FieldSpec("use", Shape.single(FhirPrimitiveType.of("code")))));

    return new ResourceType(
        "Patient",
        new FieldSpec("id", Shape.single(FhirPrimitiveType.of("id"))),
        new FieldSpec("name", Shape.many(humanNameType)));
  }

  @TestFactory
  Stream<DynamicTest> testNameFamilyTraversal() {
    return builder()
        .withTypeResolver(HAPI_RESOLVER)
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
        .withTypeResolver(HAPI_RESOLVER)
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
  Stream<DynamicTest> testSingleNameTraversal() {
    return builder()
        .withTypeResolver(HAPI_RESOLVER)
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
