package com.example.fhirpath.ir.type;

import com.example.fhirpath.analyzer.InvalidExpressionException;
import com.example.fhirpath.test.FhirPathTestBase;
import java.math.BigDecimal;
import java.util.stream.Stream;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Enumerations;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.StringType;
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

  @TestFactory
  Stream<DynamicTest> testTypeOnEmptyCollection() {
    return builder()
        .group("type() on empty collection")
        .testEmpty("{}.type()", "type() returns empty for empty collection")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testNestedTypeCall() {
    return builder()
        .group("Nested type().type() returns System.Object")
        .testEquals("System", "1.type().type().namespace")
        .testEquals("Object", "1.type().type().name")
        .testEquals("System.Any", "1.type().type().baseType")
        .build();
  }

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
            2, "name.given.type().count()", "Plural collection returns one TypeInfo per element")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testTypeAndOfTypeOnCollectionWithNullElements() {
    final Patient patient = new Patient();
    patient.setId("null-given");
    final org.hl7.fhir.r4.model.HumanName name = patient.addName().setFamily("Chalmers");
    // Build a given list containing a null-valued StringType followed by two real values. Per
    // issue #192 and the FHIRPath spec ("Null and empty"), collections cannot contain null, so
    // the null-valued primitive slot is filtered out during traversal. type() and ofType()
    // therefore see the two remaining non-null elements.
    name.getGiven().add(new StringType());
    name.getGiven().add(new StringType("Peter"));
    name.getGiven().add(new StringType("James"));

    return builder()
        .withSubject(patient)
        .group("type() on plural primitive collection with a null element")
        .testEquals(
            2,
            "name.given.type().count()",
            "Null-valued primitive slots are filtered during traversal, so type() sees only the"
                + " two non-null elements")
        .testEquals(
            "string",
            "name.given.type().first().name",
            "Declared type is preserved for the surviving non-null elements")
        .group("ofType() on plural primitive collection with a null element")
        .testEquals(
            2,
            "name.given.ofType(string).count()",
            "ofType() operates on the already-filtered collection (nulls removed at traversal)")
        .testEquals(
            "Peter",
            "name.given.ofType(string).first()",
            "First non-null element survives ofType() filtering")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testTypeOnPluralChoiceType() {
    final Observation obs = new Observation();
    obs.setId("multi-component");
    final Observation.ObservationComponentComponent c1 =
        new Observation.ObservationComponentComponent();
    c1.setCode(new CodeableConcept().addCoding(new Coding().setCode("bp")));
    c1.setValue(new Quantity().setValue(new BigDecimal("120")).setUnit("mmHg"));
    obs.addComponent(c1);
    final Observation.ObservationComponentComponent c2 =
        new Observation.ObservationComponentComponent();
    c2.setCode(new CodeableConcept().addCoding(new Coding().setCode("note")));
    c2.setValue(new StringType("normal"));
    obs.addComponent(c2);

    return builder()
        .withSubject(obs)
        .group("type() on plural choice element (component.value)")
        .testEquals(2, "component.value.type().count()", "Returns one TypeInfo per component")
        .testEquals(
            "Quantity", "component.value.type().first().name", "First component value is Quantity")
        .testEquals("string", "component.value.type()[1].name", "Second component value is string")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testTypeWithArgumentErrors() {
    return builder()
        .group("type() rejects arguments")
        .testError(
            InvalidExpressionException.class, "1.type(String)", "type() with argument throws error")
        .build();
  }
}
