package com.example.fhirpath.ir.fhir;

import com.example.fhirpath.test.FhirPathTestBase;
import com.example.fhirpath.typing.FhirPrimitiveType;
import com.example.fhirpath.typing.FieldSpec;
import com.example.fhirpath.typing.InlineResourceType;
import com.example.fhirpath.typing.ResourceType;
import com.example.fhirpath.typing.Shape;
import java.util.List;
import java.util.stream.Stream;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.StringType;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Tests for #170 — traversal to a field that is not defined in the FHIR schema resolves to an empty
 * collection in the analyzer, regardless of whether the underlying data happens to carry a value
 * for that key.
 *
 * <p>This is the FHIR-schema-bound interpretation that fp2sql shares with Pathling. The fhirpath.js
 * reference implementation diverges by reading the raw JSON value (see SPEC_DIVERGENCES.md R8); the
 * fhirpath-js compat suite excludes the affected cases.
 */
class UndefinedFieldTraversalTest extends FhirPathTestBase {

  @TestFactory
  Stream<DynamicTest> hapiResourceUndefinedField() {
    final Observation obs = new Observation();
    obs.setId("obs-1");
    obs.setValue(new StringType("high"));
    return builder()
        .withSubject(obs)
        .group("HAPI Observation — undefined field returns empty")
        .testEmpty("CustomField", "Bare undefined field on resource context")
        .testEmpty("Observation.CustomField", "Qualified undefined field on resource")
        .testEmpty("CustomField.value", "Chained traversal off undefined field is empty")
        .testEmpty(
            "CustomField = 'test'",
            "Equality with undefined field returns empty per empty-propagation")
        .testTrue("CustomField.empty()", "empty() on undefined field is true")
        .testFalse("CustomField.exists()", "exists() on undefined field is false")
        .testEquals(0, "CustomField.count()", "count() on undefined field is 0")
        .testEmpty("CustomField.where($this = 'x')", "where() on undefined field returns empty")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> inlineResourceUndefinedField() {
    final ResourceType obsType =
        new InlineResourceType(
            "Observation",
            new FieldSpec("id", Shape.single(FhirPrimitiveType.of("id"))),
            new FieldSpec("status", Shape.single(FhirPrimitiveType.of("code"))));
    return builder()
        .withSubject(obsType, sb -> sb.string("id", "obs-1").string("status", "final"))
        .group("Inline complex type — undefined field returns empty")
        .testEmpty("CustomField", "Undefined field on inline-typed resource is empty")
        .testEquals(List.of("final"), "status", "Defined fields still resolve normally")
        .build();
  }
}
