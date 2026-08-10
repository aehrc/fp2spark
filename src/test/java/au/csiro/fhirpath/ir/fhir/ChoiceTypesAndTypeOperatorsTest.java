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

import au.csiro.fhirpath.analyzer.InvalidExpressionException;
import au.csiro.fhirpath.test.FhirPathTestBase;
import java.util.stream.Stream;
import org.hl7.fhir.r4.model.Enumerations;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Quantity;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Tests for FHIR choice types and type operators (ofType, is, as).
 *
 * <p>Covers:
 *
 * <ul>
 *   <li>{@code ofType()} on choice types — narrows to a specific variant
 *   <li>{@code is} operator — type testing on choice and non-choice types
 *   <li>{@code as} operator — type casting on choice and non-choice types
 *   <li>Chained traversal after type narrowing (e.g., {@code value.ofType(Quantity).value})
 * </ul>
 */
class ChoiceTypesAndTypeOperatorsTest extends FhirPathTestBase {

  private static Observation createQuantityObservation() {
    final Observation obs = new Observation();
    obs.setId("obs-1");
    obs.setValue(
        new Quantity()
            .setValue(42.0)
            .setUnit("mg")
            .setSystem("http://unitsofmeasure.org")
            .setCode("mg"));
    return obs;
  }

  private static Observation createStringObservation() {
    final Observation obs = new Observation();
    obs.setId("obs-2");
    obs.setValue(new org.hl7.fhir.r4.model.StringType("positive"));
    return obs;
  }

  private static Patient createPatient() {
    final Patient patient = new Patient();
    patient.setId("patient-1");
    patient.setActive(true);
    patient.setGender(Enumerations.AdministrativeGender.MALE);
    return patient;
  }

  // --- ofType() on choice types ---

  @TestFactory
  Stream<DynamicTest> testOfTypeOnQuantityValue() {
    return builder()
        .withSubject(createQuantityObservation())
        .group("ofType() on Quantity value")
        .testEquals(42.0, "value.ofType(Quantity).value", "ofType narrows to Quantity, get value")
        .testEquals("mg", "value.ofType(Quantity).unit", "ofType narrows to Quantity, get unit")
        .testEquals(
            "http://unitsofmeasure.org",
            "value.ofType(Quantity).system",
            "ofType narrows to Quantity, get system")
        .testEquals("mg", "value.ofType(Quantity).code", "ofType narrows to Quantity, get code")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testOfTypeWrongVariant() {
    return builder()
        .withSubject(createQuantityObservation())
        .group("ofType() with wrong variant")
        .testEmpty("value.ofType(string)", "ofType with non-matching variant returns empty")
        .build();
  }

  // --- is operator on choice types ---

  @TestFactory
  Stream<DynamicTest> testIsOnChoiceType() {
    return builder()
        .withSubject(createQuantityObservation())
        .group("is operator on choice types")
        .testTrue("value is Quantity", "is returns true for matching variant")
        .testFalse("value is string", "is returns false for non-matching variant")
        .build();
  }

  // --- as operator on choice types ---

  @TestFactory
  Stream<DynamicTest> testAsOnChoiceType() {
    return builder()
        .withSubject(createQuantityObservation())
        .group("as operator on choice types")
        .testEquals(42.0, "(value as Quantity).value", "as narrows to Quantity, then get value")
        .testEquals("mg", "(value as Quantity).unit", "as narrows to Quantity, then get unit")
        .build();
  }

  // --- is operator on non-choice types ---

  @TestFactory
  Stream<DynamicTest> testIsOnNonChoiceType() {
    return builder()
        .withSubject(createPatient())
        .group("is operator on non-choice types")
        .testTrue("active is boolean", "is returns true for matching primitive type")
        .testFalse("active is string", "is returns false for non-matching primitive type")
        .testTrue("gender is code", "is returns true for code type")
        .build();
  }

  // --- as operator on non-choice types ---

  @TestFactory
  Stream<DynamicTest> testAsOnNonChoiceType() {
    return builder()
        .withSubject(createPatient())
        .group("as operator on non-choice types")
        .testEquals(true, "active as boolean", "as with matching type returns value")
        .testEmpty("active as string", "as with non-matching type returns empty")
        .build();
  }

  // --- String value[x] ---

  @TestFactory
  Stream<DynamicTest> testStringValueObservation() {
    return builder()
        .withSubject(createStringObservation())
        .group("String value[x]")
        .testTrue("value is string", "is returns true for string variant")
        .testFalse("value is Quantity", "is returns false for Quantity variant")
        .build();
  }

  // --- ofType() on non-choice types ---

  @TestFactory
  Stream<DynamicTest> testOfTypeOnNonChoiceType() {
    return builder()
        .withSubject(createPatient())
        .group("ofType() on non-choice types")
        .testEquals(true, "active.ofType(boolean)", "ofType with matching type returns value")
        .testEmpty("active.ofType(string)", "ofType with non-matching type returns empty")
        .build();
  }

  // --- Direct field traversal on choice types is rejected (D6) ---

  @TestFactory
  Stream<DynamicTest> testDirectFieldTraversalOnChoiceTypeRejected() {
    return builder()
        .withSubject(createQuantityObservation())
        .group("Direct field traversal on unnarrowed choice type fails at compile time")
        .testError(
            InvalidExpressionException.class,
            "value.value",
            "Field traversal on Choice(value) without ofType/is/as")
        .testError(
            InvalidExpressionException.class,
            "value.unit",
            "Quantity-shaped traversal without explicit narrowing")
        .testError(
            InvalidExpressionException.class,
            "value.value < 100",
            "Comparison consumes traversal that should fail before overload resolution")
        .build();
  }

  // --- FHIR-qualified type names ---

  @TestFactory
  Stream<DynamicTest> testFhirQualifiedTypeNames() {
    return builder()
        .withSubject(createQuantityObservation())
        .group("FHIR-qualified type specifiers")
        .testTrue("value is FHIR.Quantity", "FHIR.Quantity works with is")
        .testEquals(42.0, "value.ofType(FHIR.Quantity).value", "FHIR.Quantity works with ofType")
        .build();
  }
}
