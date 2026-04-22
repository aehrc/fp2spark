package com.example.fhirpath.ir.fhir;

import com.example.fhirpath.test.FhirPathTestBase;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Stream;
import org.hl7.fhir.r4.model.Duration;
import org.hl7.fhir.r4.model.Enumerations;
import org.hl7.fhir.r4.model.MedicationRequest;
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

  /**
   * Regression for #180 — {@code toString()} with an {@code ANY} parameter must accept FHIR
   * primitive arguments via implicit FHIR→System coercion. Previously the analyzer rejected these
   * with "Expected SystemType".
   */
  @TestFactory
  Stream<DynamicTest> testToStringOnHapiFhirPrimitives() {
    return builder()
        .withSubject(createPatient())
        .group("HAPI toString() on FHIR primitives (#180)")
        .testEquals("patient-1", "id.toString()", "FHIR.id → System.String")
        .testEquals("true", "active.toString()", "FHIR.boolean → System.String")
        .testEquals("male", "gender.toString()", "FHIR.code → System.String")
        .testEquals("2024-01-15", "birthDate.toString()", "FHIR.date → System.String")
        .build();
  }

  /**
   * Regression for #180 — {@code toQuantity()} with an {@code ANY} parameter must accept
   * Quantity-compatible FHIR complex types (Duration, Age, Count, Distance, Money, SimpleQuantity)
   * via implicit coercion to System.Quantity. The Pathling encoder attaches extra columns
   * (_value_canonicalized, _fid, ...) to Quantity-family structs; the codegen projects them back to
   * the canonical (value, unit, system, code) layout.
   */
  @TestFactory
  Stream<DynamicTest> testToQuantityOnHapiDuration() {
    final MedicationRequest request = new MedicationRequest();
    request.setId("rx-1");
    final Duration duration = new Duration();
    duration.setValue(new BigDecimal("3"));
    duration.setUnit("days");
    duration.setSystem("http://unitsofmeasure.org");
    duration.setCode("d");
    request.getDispenseRequest().setExpectedSupplyDuration(duration);
    return builder()
        .withSubject(request)
        .group("HAPI toQuantity() on FHIR Duration (#180)")
        .testEquals(
            new BigDecimal("3"),
            "dispenseRequest.expectedSupplyDuration.toQuantity().value",
            "FHIR.Duration → System.Quantity preserves value")
        .testEquals(
            "d",
            "dispenseRequest.expectedSupplyDuration.toQuantity().code",
            "FHIR.Duration → System.Quantity preserves code")
        .testTrue(
            "dispenseRequest.expectedSupplyDuration.convertsToQuantity()",
            "convertsToQuantity() accepts FHIR Duration")
        .build();
  }
}
