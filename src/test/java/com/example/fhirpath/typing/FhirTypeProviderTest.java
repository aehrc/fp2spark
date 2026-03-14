package com.example.fhirpath.typing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ca.uhn.fhir.context.FhirContext;
import java.util.Optional;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * Tests for {@link HapiTypeResolver} — HAPI FHIR R4 field resolution.
 *
 * <p>Verifies:
 *
 * <ul>
 *   <li>Resolving Patient fields (id, active, gender, birthDate, name)
 *   <li>Correct FHIR primitive type mappings
 *   <li>Cardinality detection (SINGLE vs MANY)
 *   <li>Nested complex type resolution (HumanName.family, HumanName.given)
 *   <li>Recursive types don't cause infinite loops
 *   <li>Choice types return empty (deferred to #42)
 * </ul>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FhirTypeProviderTest {

  private HapiTypeResolver resolver;

  @BeforeAll
  void setup() {
    resolver = new HapiTypeResolver(FhirContext.forR4Cached());
  }

  // --- Patient fields ---

  @Test
  void resolvePatientId() {
    final ResourceType patient = new ResourceType("Patient");
    final Optional<FieldSpec> field = resolver.resolveField(patient, "id");
    assertTrue(field.isPresent(), "Patient.id should be resolvable");
    assertInstanceOf(FhirPrimitiveType.class, field.get().getType());
    assertTrue(field.get().isSingular(), "Patient.id should be SINGLE");
  }

  @Test
  void resolvePatientActive() {
    final ResourceType patient = new ResourceType("Patient");
    final Optional<FieldSpec> field = resolver.resolveField(patient, "active");
    assertTrue(field.isPresent(), "Patient.active should be resolvable");
    final FhirPrimitiveType type = assertInstanceOf(FhirPrimitiveType.class, field.get().getType());
    assertEquals(PrimitiveType.BOOLEAN, type.getSystemType());
    assertTrue(field.get().isSingular());
  }

  @Test
  void resolvePatientGender() {
    final ResourceType patient = new ResourceType("Patient");
    final Optional<FieldSpec> field = resolver.resolveField(patient, "gender");
    assertTrue(field.isPresent(), "Patient.gender should be resolvable");
    final FhirPrimitiveType type = assertInstanceOf(FhirPrimitiveType.class, field.get().getType());
    assertEquals(PrimitiveType.STRING, type.getSystemType());
    assertTrue(field.get().isSingular());
  }

  @Test
  void resolvePatientBirthDate() {
    final ResourceType patient = new ResourceType("Patient");
    final Optional<FieldSpec> field = resolver.resolveField(patient, "birthDate");
    assertTrue(field.isPresent(), "Patient.birthDate should be resolvable");
    final FhirPrimitiveType type = assertInstanceOf(FhirPrimitiveType.class, field.get().getType());
    assertEquals(PrimitiveType.DATE, type.getSystemType());
    assertTrue(field.get().isSingular());
  }

  @Test
  void resolvePatientName() {
    final ResourceType patient = new ResourceType("Patient");
    final Optional<FieldSpec> field = resolver.resolveField(patient, "name");
    assertTrue(field.isPresent(), "Patient.name should be resolvable");
    assertInstanceOf(ComplexType.class, field.get().getType());
    assertFalse(field.get().isSingular(), "Patient.name should be MANY");
  }

  // --- Nested complex type resolution ---

  @Test
  void resolveHumanNameFamily() {
    final ComplexType humanName = new ComplexType("HumanName");
    final Optional<FieldSpec> field = resolver.resolveField(humanName, "family");
    assertTrue(field.isPresent(), "HumanName.family should be resolvable");
    final FhirPrimitiveType type = assertInstanceOf(FhirPrimitiveType.class, field.get().getType());
    assertEquals(PrimitiveType.STRING, type.getSystemType());
    assertTrue(field.get().isSingular());
  }

  @Test
  void resolveHumanNameGiven() {
    final ComplexType humanName = new ComplexType("HumanName");
    final Optional<FieldSpec> field = resolver.resolveField(humanName, "given");
    assertTrue(field.isPresent(), "HumanName.given should be resolvable");
    final FhirPrimitiveType type = assertInstanceOf(FhirPrimitiveType.class, field.get().getType());
    assertEquals(PrimitiveType.STRING, type.getSystemType());
    assertFalse(field.get().isSingular(), "HumanName.given should be MANY");
  }

  // --- Edge cases ---

  @Test
  void resolveNonExistentField() {
    final ResourceType patient = new ResourceType("Patient");
    final Optional<FieldSpec> field = resolver.resolveField(patient, "nonExistentField");
    assertFalse(field.isPresent(), "Non-existent field should return empty");
  }

  @Test
  void resolveFieldOnPrimitiveType() {
    final Optional<FieldSpec> field =
        resolver.resolveField(FhirPrimitiveType.of("string"), "value");
    assertFalse(field.isPresent(), "Primitive types should have no child fields");
  }

  @Test
  void resolveFieldOnSystemPrimitiveType() {
    final Optional<FieldSpec> field = resolver.resolveField(PrimitiveType.STRING, "value");
    assertFalse(field.isPresent(), "System primitive types should have no child fields");
  }

  @Test
  void resolveChoiceTypeReturnsEmpty() {
    // Patient.deceased[x] is a choice type — should return empty (deferred to #42)
    final ResourceType patient = new ResourceType("Patient");
    final Optional<FieldSpec> field = resolver.resolveField(patient, "deceased");
    assertFalse(field.isPresent(), "Choice types should return empty (deferred to #42)");
  }

  @Test
  void recursiveTypesDoNotLoop() {
    // Reference contains identifier, which could reference back
    // Just verify we can resolve a few levels deep without infinite loop
    final ComplexType reference = new ComplexType("Reference");
    final Optional<FieldSpec> displayField = resolver.resolveField(reference, "display");
    assertTrue(displayField.isPresent(), "Reference.display should be resolvable");
  }
}
