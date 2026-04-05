package com.example.fhirpath.ir.type;

import com.example.fhirpath.test.FhirPathTestBase;
import java.util.stream.Stream;
import org.hl7.fhir.r4.model.Enumerations;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Quantity;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Tests for the FHIRPath {@code type()} reflection function.
 *
 * <p>The {@code type()} function returns type information for each element as a struct with three
 * fields: {@code namespace}, {@code name}, and {@code baseType}.
 *
 * <p>Covers:
 *
 * <ul>
 *   <li>System primitive literals (Integer, Boolean, String, Decimal, Date, DateTime, Time)
 *   <li>System complex types (Quantity, Coding)
 *   <li>FHIR primitive elements (boolean, date)
 *   <li>FHIR complex type elements (CodeableConcept, HumanName)
 *   <li>FHIR resource types (Patient)
 *   <li>Empty collection propagation
 *   <li>Nested {@code type().type()} call
 *   <li>Choice type per-row type resolution
 *   <li>Integration with equality and other functions
 * </ul>
 */
class TypeReflectionTest extends FhirPathTestBase {

  private static Patient createPatient() {
    final Patient patient = new Patient();
    patient.setId("type-test");
    patient.setActive(true);
    patient.setGender(Enumerations.AdministrativeGender.MALE);
    patient.setBirthDateElement(new org.hl7.fhir.r4.model.DateType("1990-01-01"));
    patient.addName().setFamily("Smith").addGiven("John").addGiven("David");
    return patient;
  }

  private static Observation createQuantityObservation() {
    final Observation obs = new Observation();
    obs.setId("obs-qty");
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
    obs.setId("obs-str");
    obs.setValue(new org.hl7.fhir.r4.model.StringType("positive"));
    return obs;
  }

  // --- System primitive literals ---

  @TestFactory
  Stream<DynamicTest> testTypeOnSystemPrimitiveLiterals() {
    return builder()
        .group("type() on Integer literal")
        .testEquals("System", "1.type().namespace")
        .testEquals("Integer", "1.type().name")
        .testEquals("System.Any", "1.type().baseType")
        .group("type() on Boolean literal")
        .testEquals("System", "true.type().namespace")
        .testEquals("Boolean", "true.type().name")
        .group("type() on String literal")
        .testEquals("System", "'hello'.type().namespace")
        .testEquals("String", "'hello'.type().name")
        .group("type() on Decimal literal")
        .testEquals("System", "3.14.type().namespace")
        .testEquals("Decimal", "3.14.type().name")
        .group("type() on Date literal")
        .testEquals("System", "@2024-01-01.type().namespace")
        .testEquals("Date", "@2024-01-01.type().name")
        .group("type() on DateTime literal")
        .testEquals("System", "@2024-01-01T12:00:00.type().namespace")
        .testEquals("DateTime", "@2024-01-01T12:00:00.type().name")
        .group("type() on Time literal")
        .testEquals("System", "@T12:00:00.type().namespace")
        .testEquals("Time", "@T12:00:00.type().name")
        .build();
  }

  // --- System complex types ---

  @TestFactory
  Stream<DynamicTest> testTypeOnSystemComplexTypes() {
    return builder()
        .group("type() on Quantity literal")
        .testEquals("System", "(1 'mg').type().namespace")
        .testEquals("Quantity", "(1 'mg').type().name")
        .testEquals("System.Any", "(1 'mg').type().baseType")
        .group("type() on Coding literal")
        .testEquals("System", "(http://example.com|code).type().namespace")
        .testEquals("Coding", "(http://example.com|code).type().name")
        .testEquals("System.Any", "(http://example.com|code).type().baseType")
        .build();
  }

  // --- FHIR primitive elements ---

  @TestFactory
  Stream<DynamicTest> testTypeOnFhirPrimitiveElements() {
    return builder()
        .withSubject(createPatient())
        .group("type() on FHIR boolean element")
        .testEquals("FHIR", "active.type().namespace")
        .testEquals("boolean", "active.type().name")
        .testEquals("FHIR.Element", "active.type().baseType")
        .group("type() on FHIR date element")
        .testEquals("FHIR", "birthDate.type().namespace")
        .testEquals("date", "birthDate.type().name")
        .build();
  }

  // --- FHIR complex type elements ---

  @TestFactory
  Stream<DynamicTest> testTypeOnFhirComplexTypeElements() {
    return builder()
        .withSubject(createPatient())
        .group("type() on FHIR HumanName element (singular via first())")
        .testEquals("FHIR", "name.first().type().namespace")
        .testEquals("HumanName", "name.first().type().name")
        .testEquals("FHIR.Element", "name.first().type().baseType")
        .build();
  }

  // --- FHIR resource type ---

  @TestFactory
  Stream<DynamicTest> testTypeOnFhirResourceType() {
    return builder()
        .withSubject(createPatient())
        .group("type() on Patient resource itself")
        .testEquals("FHIR", "Patient.type().namespace")
        .testEquals("Patient", "Patient.type().name")
        .testEquals("FHIR.Resource", "Patient.type().baseType")
        .build();
  }

  // --- Empty collection ---

  @TestFactory
  Stream<DynamicTest> testTypeOnEmptyCollection() {
    return builder()
        .group("type() on empty collection")
        .testEmpty("{}.type()", "type() returns empty for empty collection")
        .build();
  }

  // --- Nested type().type() ---

  @TestFactory
  Stream<DynamicTest> testNestedTypeCall() {
    return builder()
        .group("Nested type().type() returns System.Object")
        .testEquals("System", "1.type().type().namespace")
        .testEquals("Object", "1.type().type().name")
        .testEquals("System.Any", "1.type().type().baseType")
        .build();
  }

  // --- Choice type per-row resolution ---

  @TestFactory
  Stream<DynamicTest> testTypeOnChoiceTypeQuantity() {
    return builder()
        .withSubject(createQuantityObservation())
        .group("type() on choice element with Quantity value")
        .testEquals("FHIR", "value.type().namespace")
        .testEquals("Quantity", "value.type().name")
        .testEquals("FHIR.Element", "value.type().baseType")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testTypeOnChoiceTypeString() {
    return builder()
        .withSubject(createStringObservation())
        .group("type() on choice element with string value")
        .testEquals("FHIR", "value.type().namespace")
        .testEquals("string", "value.type().name")
        .build();
  }

  // --- Integration with other functions ---

  @TestFactory
  Stream<DynamicTest> testTypeIntegrationWithEquality() {
    return builder()
        .group("type() results usable with equality")
        .testTrue("1.type().namespace = 'System'", "namespace equality check")
        .testTrue("1.type().name = 'Integer'", "name equality check")
        .testFalse("1.type().namespace = 'FHIR'", "namespace inequality check")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testTypeOnOperationResults() {
    return builder()
        .withSubject(createPatient())
        .group("type() on operation results produces System types")
        .testEquals("System", "active.not().type().namespace", "not() produces System namespace")
        .testEquals("Boolean", "active.not().type().name", "not() produces System Boolean")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testTypeOnMultipleElements() {
    return builder()
        .group("type() on collection returns one TypeInfo per element")
        .testEquals(2, "('a' | 'b').type().count()")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testTypeOnPluralFhirCollection() {
    return builder()
        .withSubject(createPatient())
        .group("type() on plural FHIR collection")
        .testEquals(
            "FHIR",
            "name.given.type().first().namespace",
            "Plural FHIR collection returns FHIR namespace")
        .testEquals(
            "string",
            "name.given.type().first().name",
            "Plural FHIR collection returns FHIR type name")
        .testEquals(
            2,
            "name.given.type().count()",
            "Plural collection returns one TypeInfo per non-null element")
        .build();
  }
}
