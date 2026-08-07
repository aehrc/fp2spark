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
import au.csiro.fhirpath.typing.InlineResourceType;
import au.csiro.fhirpath.typing.ResourceType;
import au.csiro.fhirpath.typing.Shape;
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
