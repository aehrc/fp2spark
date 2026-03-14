package com.example.fhirpath.ir.fhir;

import com.example.fhirpath.test.FhirPathTestBase;
import java.util.List;
import java.util.stream.Stream;
import org.hl7.fhir.r4.model.Enumerations;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Integration tests using actual HAPI FHIR resource objects as test subjects.
 *
 * <p>Verifies that the HAPI → JSON → Spark pipeline produces correct results for:
 *
 * <ul>
 *   <li>Primitive field traversal (id, active, gender, birthDate)
 *   <li>{@code getValue()} and {@code hasValue()} on real FHIR structures
 *   <li>Implicit FHIR→System type conversion (date comparison, boolean)
 *   <li>Complex type traversal (name.family, name.given with MANY→MANY flattening)
 *   <li>Filtered traversal with {@code where()}
 * </ul>
 */
class HapiResourceTraversalTest extends FhirPathTestBase {

  private static Patient createPatient() {
    final Patient patient = new Patient();
    patient.setId("patient-1");
    patient.setActive(true);
    patient.setGender(Enumerations.AdministrativeGender.MALE);
    patient.setBirthDateElement(new org.hl7.fhir.r4.model.DateType("2024-01-15"));
    patient
        .addName()
        .setFamily("Smith")
        .addGiven("John")
        .addGiven("James")
        .setUse(org.hl7.fhir.r4.model.HumanName.NameUse.OFFICIAL);
    patient
        .addName()
        .setFamily("Doe")
        .addGiven("Jane")
        .setUse(org.hl7.fhir.r4.model.HumanName.NameUse.NICKNAME);
    return patient;
  }

  @TestFactory
  Stream<DynamicTest> testPrimitiveFieldTraversal() {
    return builder()
        .withSubject(createPatient())
        .group("HAPI primitive field traversal")
        .testEquals("patient-1", "id", "String field (id)")
        .testEquals(true, "active", "Boolean field")
        .testEquals("male", "gender", "Code field (→ string)")
        .testEquals("2024-01-15", "birthDate", "Date field")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testGetValueAndHasValue() {
    return builder()
        .withSubject(createPatient())
        .group("HAPI getValue() and hasValue()")
        .testEquals("patient-1", "id.getValue()", "getValue() returns the value")
        .testTrue("id.hasValue()", "hasValue() returns true for non-null")
        .testTrue("active.hasValue()", "hasValue() for boolean")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testHasValueForNullField() {
    // The full patient has gender set; birthDate is also set.
    // With Pathling encoders, the full FHIR schema is present even for absent fields.
    final Patient patient = new Patient();
    patient.setId("patient-2");
    // gender is NOT set — should be null in Spark
    return builder()
        .withSubject(patient)
        .group("HAPI hasValue() with null")
        .testFalse("gender.hasValue()", "hasValue() returns false for null field")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testFhirDateComparisonWithLiteral() {
    return builder()
        .withSubject(createPatient())
        .group("HAPI FHIR date comparison")
        .testTrue("birthDate = @2024-01-15", "Date equality with system literal")
        .testTrue("birthDate > @2024-01-01", "Date greater-than comparison")
        .testFalse("birthDate < @2024-01-01", "Date less-than comparison")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testComplexTypeTraversal() {
    return builder()
        .withSubject(createPatient())
        .group("HAPI complex type traversal")
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
        .withSubject(createPatient())
        .group("HAPI filtered complex type traversal")
        .testEquals(
            List.of("Smith"),
            "name.where(use = 'official').family",
            "Filter then traverse yields filtered result")
        .build();
  }
}
