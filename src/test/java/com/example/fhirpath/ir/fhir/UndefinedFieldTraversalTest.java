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
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Regression for #170 — traversal to a field not defined in the FHIR schema resolves to an empty
 * collection in the analyzer, regardless of whether the underlying data carries a value for that
 * key. Matches Pathling's behavior; diverges from fhirpath.js (see SPEC_DIVERGENCES.md R8).
 */
class UndefinedFieldTraversalTest extends FhirPathTestBase {

  @TestFactory
  Stream<DynamicTest> hapiResourceUndefinedField() {
    final Observation obs = new Observation();
    obs.setId("obs-1");
    return builder()
        .withSubject(obs)
        .group("HAPI Observation — undefined field returns empty")
        .testEmpty("CustomField", "Bare undefined field on resource context")
        .testEmpty("Observation.CustomField", "Qualified undefined field on resource")
        .testEmpty("CustomField.value", "Chained traversal off undefined field stays empty")
        .testEmpty("CustomField = 'test'", "Equality with empty propagates empty")
        .testTrue("CustomField.empty()")
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
        .testEmpty("CustomField")
        .testEquals(List.of("final"), "status", "Defined fields still resolve normally")
        .build();
  }
}
