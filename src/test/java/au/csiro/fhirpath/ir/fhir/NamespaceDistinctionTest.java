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
import java.util.stream.Stream;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Tests for strict FHIR/System namespace distinction in {@code is}/{@code as}/{@code ofType} (issue
 * #188).
 *
 * <p>Per fhirpath.js, the three type operators enforce strict namespace+name equality. Bare type
 * names resolve to System.* first, then FHIR.* — so {@code Boolean} and {@code Quantity} default to
 * System.
 *
 * <p>Matrix covered:
 *
 * <ul>
 *   <li>{@code Patient.active} (FHIR.boolean) vs {@code Boolean}, {@code System.Boolean}, {@code
 *       FHIR.boolean}
 *   <li>{@code (1 year)} (System.Quantity literal) vs {@code Quantity}, {@code System.Quantity},
 *       {@code FHIR.Quantity}
 * </ul>
 */
class NamespaceDistinctionTest extends FhirPathTestBase {

  private static Patient createActivePatient() {
    final Patient patient = new Patient();
    patient.setId("patient-1");
    patient.setActive(true);
    return patient;
  }

  // --- Patient.active (FHIR.boolean) vs Boolean / System.Boolean / FHIR.boolean ---

  @TestFactory
  Stream<DynamicTest> testIsOnFhirBooleanField() {
    return builder()
        .withSubject(createActivePatient())
        .group("is() on Patient.active (FHIR.boolean) across namespaces")
        .testFalse(
            "active.is(Boolean)",
            "is(Boolean) returns false — bare Boolean = System.Boolean ≠ FHIR.boolean")
        .testFalse(
            "active.is(System.Boolean)", "is(System.Boolean) returns false — strict namespace")
        .testTrue("active.is(FHIR.boolean)", "is(FHIR.boolean) returns true — strict match")
        .testTrue("active.is(boolean)", "is(boolean) returns true — bare 'boolean' = FHIR.boolean")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testAsOnFhirBooleanField() {
    return builder()
        .withSubject(createActivePatient())
        .group("as() on Patient.active (FHIR.boolean) across namespaces")
        .testEmpty("active.as(Boolean)", "as(Boolean) returns empty — strict namespace")
        .testEmpty("active.as(System.Boolean)", "as(System.Boolean) returns empty — strict")
        .testEquals(true, "active.as(FHIR.boolean)", "as(FHIR.boolean) returns the value")
        .testEquals(true, "active.as(boolean)", "as(boolean) returns the value — bare = FHIR")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testOfTypeOnFhirBooleanField() {
    return builder()
        .withSubject(createActivePatient())
        .group("ofType() on Patient.active (FHIR.boolean) across namespaces")
        .testEmpty("active.ofType(Boolean)", "ofType(Boolean) returns empty — strict namespace")
        .testEmpty("active.ofType(System.Boolean)", "ofType(System.Boolean) returns empty — strict")
        .testEquals(true, "active.ofType(FHIR.boolean)", "ofType(FHIR.boolean) returns the value")
        .testEquals(true, "active.ofType(boolean)", "ofType(boolean) returns the value")
        .build();
  }

  // --- 1 year (System.Quantity literal) vs Quantity / System.Quantity / FHIR.Quantity ---

  @TestFactory
  Stream<DynamicTest> testIsOnSystemQuantityLiteral() {
    return builder()
        .group("is() on (1 year) System.Quantity literal across namespaces")
        .testFalse(
            "(1 year).is(FHIR.Quantity)",
            "is(FHIR.Quantity) returns false — System.Quantity ≠ FHIR.Quantity")
        .testTrue("(1 year).is(System.Quantity)", "is(System.Quantity) returns true — strict match")
        .testTrue("(1 year).is(Quantity)", "is(Quantity) returns true — bare = System.Quantity")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testAsOnSystemQuantityLiteral() {
    return builder()
        .group("as() on (1 year) System.Quantity literal across namespaces")
        .testEmpty("(1 year).as(FHIR.Quantity)", "as(FHIR.Quantity) returns empty — strict")
        .testEquals(
            1,
            "(1 year).as(System.Quantity).value",
            "as(System.Quantity) returns the value (traverse .value)")
        .testEquals(
            1, "(1 year).as(Quantity).value", "as(Quantity) returns the value — bare = System")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testOfTypeOnSystemQuantityLiteral() {
    return builder()
        .group("ofType() on (1 year) System.Quantity literal across namespaces")
        .testEmpty("(1 year).ofType(FHIR.Quantity)", "ofType(FHIR.Quantity) returns empty — strict")
        .testEquals(
            1,
            "(1 year).ofType(System.Quantity).value",
            "ofType(System.Quantity) returns the value")
        .testEquals(
            1, "(1 year).ofType(Quantity).value", "ofType(Quantity) returns the value — bare")
        .build();
  }
}
