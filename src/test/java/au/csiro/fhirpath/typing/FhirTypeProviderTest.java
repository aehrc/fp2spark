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
package au.csiro.fhirpath.typing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.RuntimeResourceDefinition;
import java.util.Optional;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * Tests for {@link FhirComplexType} and {@link FhirResourceType} — HAPI FHIR R4 field resolution.
 *
 * <p>Verifies:
 *
 * <ul>
 *   <li>Resolving Patient fields (id, active, gender, birthDate, name)
 *   <li>Correct FHIR primitive type mappings
 *   <li>Cardinality detection (SINGLE vs MANY)
 *   <li>Nested complex type resolution (HumanName.family, HumanName.given)
 *   <li>Recursive types don't cause infinite loops
 *   <li>Choice types return ChoiceType with variant resolution
 * </ul>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FhirTypeProviderTest {

  private FhirContext fhirContext;
  private FhirResourceType patient;

  @BeforeAll
  void setup() {
    fhirContext = FhirContext.forR4Cached();
    final RuntimeResourceDefinition resDef = fhirContext.getResourceDefinition("Patient");
    patient = new FhirResourceType(resDef);
  }

  // --- Patient fields ---

  @Test
  void resolvePatientId() {
    final Optional<FieldSpec> field = patient.resolveField("id");
    assertTrue(field.isPresent(), "Patient.id should be resolvable");
    assertInstanceOf(FhirPrimitiveType.class, field.get().getType());
    assertTrue(field.get().isSingular(), "Patient.id should be SINGLE");
  }

  @Test
  void resolvePatientActive() {
    final Optional<FieldSpec> field = patient.resolveField("active");
    assertTrue(field.isPresent(), "Patient.active should be resolvable");
    final FhirPrimitiveType type = assertInstanceOf(FhirPrimitiveType.class, field.get().getType());
    assertEquals(SystemType.BOOLEAN, type.getSystemType());
    assertTrue(field.get().isSingular());
  }

  @Test
  void resolvePatientGender() {
    final Optional<FieldSpec> field = patient.resolveField("gender");
    assertTrue(field.isPresent(), "Patient.gender should be resolvable");
    final FhirPrimitiveType type = assertInstanceOf(FhirPrimitiveType.class, field.get().getType());
    assertEquals(SystemType.STRING, type.getSystemType());
    assertTrue(field.get().isSingular());
  }

  @Test
  void resolvePatientBirthDate() {
    final Optional<FieldSpec> field = patient.resolveField("birthDate");
    assertTrue(field.isPresent(), "Patient.birthDate should be resolvable");
    final FhirPrimitiveType type = assertInstanceOf(FhirPrimitiveType.class, field.get().getType());
    assertEquals(SystemType.DATE, type.getSystemType());
    assertTrue(field.get().isSingular());
  }

  @Test
  void resolvePatientName() {
    final Optional<FieldSpec> field = patient.resolveField("name");
    assertTrue(field.isPresent(), "Patient.name should be resolvable");
    assertInstanceOf(FhirComplexType.class, field.get().getType());
    assertFalse(field.get().isSingular(), "Patient.name should be MANY");
  }

  // --- Nested complex type resolution ---

  @Test
  void resolveHumanNameFamily() {
    // Resolve HumanName via Patient.name, then resolve family on it
    final Optional<FieldSpec> nameField = patient.resolveField("name");
    assertTrue(nameField.isPresent());
    final FhirComplexType humanName =
        assertInstanceOf(FhirComplexType.class, nameField.get().getType());

    final Optional<FieldSpec> field = humanName.resolveField("family");
    assertTrue(field.isPresent(), "HumanName.family should be resolvable");
    final FhirPrimitiveType type = assertInstanceOf(FhirPrimitiveType.class, field.get().getType());
    assertEquals(SystemType.STRING, type.getSystemType());
    assertTrue(field.get().isSingular());
  }

  @Test
  void resolveHumanNameGiven() {
    final Optional<FieldSpec> nameField = patient.resolveField("name");
    assertTrue(nameField.isPresent());
    final FhirComplexType humanName =
        assertInstanceOf(FhirComplexType.class, nameField.get().getType());

    final Optional<FieldSpec> field = humanName.resolveField("given");
    assertTrue(field.isPresent(), "HumanName.given should be resolvable");
    final FhirPrimitiveType type = assertInstanceOf(FhirPrimitiveType.class, field.get().getType());
    assertEquals(SystemType.STRING, type.getSystemType());
    assertFalse(field.get().isSingular(), "HumanName.given should be MANY");
  }

  // --- Edge cases ---

  @Test
  void resolveNonExistentField() {
    final Optional<FieldSpec> field = patient.resolveField("nonExistentField");
    assertFalse(field.isPresent(), "Non-existent field should return empty");
  }

  @Test
  void resolveFieldOnPrimitiveType() {
    final Optional<FieldSpec> field = FhirPrimitiveType.of("string").resolveField("value");
    assertFalse(field.isPresent(), "Primitive types should have no child fields");
  }

  @Test
  void resolveFieldOnSystemType() {
    final Optional<FieldSpec> field = SystemType.STRING.resolveField("value");
    assertFalse(field.isPresent(), "Simple system types should have no child fields");
  }

  @Test
  void resolveQuantityFields() {
    final Optional<FieldSpec> value = SystemType.QUANTITY.resolveField("value");
    assertTrue(value.isPresent(), "Quantity.value should be resolvable");
    assertEquals(SystemType.DECIMAL, value.get().getType());
    assertTrue(value.get().isSingular());

    final Optional<FieldSpec> unit = SystemType.QUANTITY.resolveField("unit");
    assertTrue(unit.isPresent(), "Quantity.unit should be resolvable");
    assertEquals(SystemType.STRING, unit.get().getType());

    final Optional<FieldSpec> system = SystemType.QUANTITY.resolveField("system");
    assertTrue(system.isPresent(), "Quantity.system should be resolvable");
    assertEquals(SystemType.STRING, system.get().getType());

    final Optional<FieldSpec> code = SystemType.QUANTITY.resolveField("code");
    assertTrue(code.isPresent(), "Quantity.code should be resolvable");
    assertEquals(SystemType.STRING, code.get().getType());

    assertFalse(
        SystemType.QUANTITY.resolveField("bogus").isPresent(),
        "Unknown Quantity field should return empty");
  }

  @Test
  void resolveChoiceTypeReturnsChoiceType() {
    // Patient.deceased[x] is a choice type — should return ChoiceType
    final Optional<FieldSpec> field = patient.resolveField("deceased");
    assertTrue(field.isPresent(), "Choice types should be resolvable");
    assertInstanceOf(ChoiceType.class, field.get().getType());
    assertTrue(field.get().isSingular(), "Patient.deceased should be SINGLE");
  }

  @Test
  void choiceTypeVariantResolution() {
    // Resolve deceased as ChoiceType, then resolve variants
    final ChoiceType choiceType =
        assertInstanceOf(ChoiceType.class, patient.resolveField("deceased").get().getType());

    // deceasedBoolean should resolve
    final Optional<FieldSpec> boolVariant = choiceType.resolveVariant("boolean");
    assertTrue(boolVariant.isPresent(), "deceased.boolean variant should resolve");
    assertEquals("deceasedBoolean", boolVariant.get().getName());

    // deceasedDateTime should resolve
    final Optional<FieldSpec> dtVariant = choiceType.resolveVariant("dateTime");
    assertTrue(dtVariant.isPresent(), "deceased.dateTime variant should resolve");
    assertEquals("deceasedDateTime", dtVariant.get().getName());

    // Invalid variant should not resolve
    final Optional<FieldSpec> invalidVariant = choiceType.resolveVariant("Quantity");
    assertFalse(invalidVariant.isPresent(), "deceased.Quantity should not resolve");
  }

  @Test
  void recursiveTypesDoNotLoop() {
    // Resolve Reference type via HAPI and verify we can traverse it
    final RuntimeResourceDefinition patientDef = fhirContext.getResourceDefinition("Patient");
    final FhirResourceType patientType = new FhirResourceType(patientDef);

    // Patient.generalPractitioner is a Reference
    final Optional<FieldSpec> gpField = patientType.resolveField("generalPractitioner");
    assertTrue(gpField.isPresent(), "Patient.generalPractitioner should be resolvable");
    final FhirComplexType reference =
        assertInstanceOf(FhirComplexType.class, gpField.get().getType());

    final Optional<FieldSpec> displayField = reference.resolveField("display");
    assertTrue(displayField.isPresent(), "Reference.display should be resolvable");
  }
}
