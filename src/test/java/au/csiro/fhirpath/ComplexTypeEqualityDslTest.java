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
package au.csiro.fhirpath;

import au.csiro.fhirpath.test.FhirPathTestBase;
import java.time.Instant;
import java.util.Date;
import java.util.stream.Stream;
import org.hl7.fhir.r4.model.Address;
import org.hl7.fhir.r4.model.HumanName;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Period;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Tests for FHIRPath equality on FHIR complex types (HumanName, Address, Period, etc.).
 *
 * <p>Covers the #134 fix: Pathling's flat-schema encoder injects a synthetic {@code _fid} field on
 * every composite struct, so native Spark {@code equalTo} on two structurally identical values at
 * different positions returned {@code false}. The {@code ComplexEquals} UDF strips {@code
 * _}-prefixed fields at every depth to give pure structural equality.
 *
 * <p>Uses HAPI Patient objects via {@link au.csiro.fhirpath.test.FhirPathTestBuilder#withSubject(
 * org.hl7.fhir.instance.model.api.IBaseResource)}, which runs through Pathling's encoder producing
 * the flat schema with {@code _fid}.
 */
class ComplexTypeEqualityDslTest extends FhirPathTestBase {

  private static Patient patientWithTwoIdenticalNames() {
    final Patient patient = new Patient();
    patient.setId("p1");
    patient.addName(new HumanName().setFamily("Smith").addGiven("John"));
    patient.addName(new HumanName().setFamily("Smith").addGiven("John"));
    return patient;
  }

  private static Patient patientWithTwoDifferentNames() {
    final Patient patient = new Patient();
    patient.setId("p2");
    patient.addName(new HumanName().setFamily("Smith").addGiven("John"));
    patient.addName(new HumanName().setFamily("Jones").addGiven("John"));
    return patient;
  }

  private static Patient patientWithIdenticalPeriods() {
    final Patient patient = new Patient();
    patient.setId("p3");
    final Date start = Date.from(Instant.parse("2020-01-01T00:00:00Z"));
    final Date end = Date.from(Instant.parse("2020-12-31T00:00:00Z"));
    patient.addName(
        new HumanName().setFamily("Smith").setPeriod(new Period().setStart(start).setEnd(end)));
    patient.addName(
        new HumanName().setFamily("Smith").setPeriod(new Period().setStart(start).setEnd(end)));
    return patient;
  }

  private static Patient patientWithTwoIdenticalAddresses() {
    final Patient patient = new Patient();
    patient.setId("p4");
    patient.addAddress(new Address().setCity("Sydney").setCountry("Australia"));
    patient.addAddress(new Address().setCity("Sydney").setCountry("Australia"));
    return patient;
  }

  @TestFactory
  Stream<DynamicTest> structurallyIdenticalNamesAtDifferentPositionsAreEqual() {
    return builder()
        .withSubject(patientWithTwoIdenticalNames())
        .group("HumanName equality ignoring _fid")
        .testTrue("name.first() = name.last()", "Identical names at different positions")
        .testFalse("name.first() != name.last()", "Negation of identical names")
        .testTrue("name = name", "Collection equals itself")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> differingFamilyMakesNamesUnequal() {
    return builder()
        .withSubject(patientWithTwoDifferentNames())
        .group("HumanName inequality on visible field")
        .testFalse("name.first() = name.last()", "Differing family → not equal")
        .testTrue("name.first() != name.last()", "Differing family → not-equals true")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> nestedPeriodEqualityIgnoresNestedFid() {
    return builder()
        .withSubject(patientWithIdenticalPeriods())
        .group("Nested complex equality")
        .testTrue("name.first().period = name.last().period", "Nested Period ignoring nested _fid")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> addressEqualityIgnoresFid() {
    return builder()
        .withSubject(patientWithTwoIdenticalAddresses())
        .group("Address equality ignoring _fid")
        .testTrue("address.first() = address.last()", "Identical addresses at different positions")
        .testTrue("address = address", "Collection equals itself")
        .build();
  }
}
