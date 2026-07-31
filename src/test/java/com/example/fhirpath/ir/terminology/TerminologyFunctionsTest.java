package com.example.fhirpath.ir.terminology;

import com.example.fhirpath.analyzer.CardinalityMismatchException;
import com.example.fhirpath.terminology.MockTerminologyService;
import com.example.fhirpath.test.FhirPathTestBase;
import java.util.stream.Stream;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Tests for FHIR terminology functions: {@code memberOf()}.
 *
 * <p>Based on the FHIR-specific FHIRPath binding, "Additional functions" — {@code memberOf(valueset
 * : string) : Boolean}.
 *
 * <p>Covers:
 *
 * <ul>
 *   <li>Core membership semantics for {@code Coding} inputs, from both Coding literals and resource
 *       traversal
 *   <li>Any-match semantics for {@code CodeableConcept} inputs
 *   <li>The specification's empty result when the value set cannot be resolved, which is distinct
 *       from a resolvable value set that does not contain the code
 *   <li>Empty propagation for both the input and the value set argument
 *   <li>Rejection of multi-valued input, per SPEC_DIVERGENCES D1
 * </ul>
 *
 * <p>Membership is declared up front through {@link MockTerminologyService} rather than contacting
 * a terminology server, so these tests exercise code generation and the UDF contract without
 * network access.
 */
public class TerminologyFunctionsTest extends FhirPathTestBase {

  private static final String LOINC = "http://loinc.org";
  private static final String SNOMED = "http://snomed.info/sct";

  /** A value set declared to contain a single LOINC code. */
  private static final String VITAL_SIGNS =
      "http://hl7.org/fhir/ValueSet/observation-vitalsignresult";

  /** A value set that resolves but contains no codes. */
  private static final String EMPTY_VALUE_SET = "http://example.org/ValueSet/empty";

  /** A value set that the terminology service cannot resolve. */
  private static final String UNKNOWN_VALUE_SET = "http://example.org/ValueSet/does-not-exist";

  /** A code that is a member of {@link #VITAL_SIGNS}. */
  private static final String MEMBER_CODE = "55915-3";

  /** A code that is not a member of any declared value set. */
  private static final String NON_MEMBER_CODE = "99999-9";

  /** The terminology service backing every test in this class. */
  private static MockTerminologyService terminology() {
    return MockTerminologyService.builder()
        .withMember(VITAL_SIGNS, LOINC, MEMBER_CODE)
        .withEmptyValueSet(EMPTY_VALUE_SET)
        .build();
  }

  /** An Observation whose code's first coding is a member of {@link #VITAL_SIGNS}. */
  private static Observation observationWithMemberFirst() {
    final Observation observation = new Observation();
    observation.setCode(
        new CodeableConcept()
            .addCoding(new Coding().setSystem(LOINC).setCode(MEMBER_CODE))
            .addCoding(new Coding().setSystem(SNOMED).setCode("123456")));
    return observation;
  }

  /** An Observation whose code has a member only in a later coding. */
  private static Observation observationWithMemberLast() {
    final Observation observation = new Observation();
    observation.setCode(
        new CodeableConcept()
            .addCoding(new Coding().setSystem(SNOMED).setCode("123456"))
            .addCoding(new Coding().setSystem(LOINC).setCode(MEMBER_CODE)));
    return observation;
  }

  /** An Observation whose code contains no member of any declared value set. */
  private static Observation observationWithoutMember() {
    final Observation observation = new Observation();
    observation.setCode(
        new CodeableConcept().addCoding(new Coding().setSystem(LOINC).setCode(NON_MEMBER_CODE)));
    return observation;
  }

  /** An Observation whose code carries text but no codings. */
  private static Observation observationWithTextOnlyCode() {
    final Observation observation = new Observation();
    observation.setCode(new CodeableConcept().setText("Nothing coded here"));
    return observation;
  }

  /** A Patient with no maritalStatus, for testing an absent CodeableConcept field. */
  private static Patient patientWithoutMaritalStatus() {
    final Patient patient = new Patient();
    patient.setId("patient-1");
    return patient;
  }

  @TestFactory
  Stream<DynamicTest> testMemberOf() {
    return builder(terminology())
        .group("memberOf() on a Coding literal")
        .testTrue("(" + LOINC + "|" + MEMBER_CODE + ").memberOf('" + VITAL_SIGNS + "')")
        .testFalse("(" + LOINC + "|" + NON_MEMBER_CODE + ").memberOf('" + VITAL_SIGNS + "')")
        .testTrue(
            "(" + LOINC + "|" + MEMBER_CODE + "|'2.74').memberOf('" + VITAL_SIGNS + "')",
            "Version does not affect membership")
        .testFalse(
            "(" + LOINC + "|" + MEMBER_CODE + ").memberOf('" + EMPTY_VALUE_SET + "')",
            "A value set that resolves but contains no codes yields false")
        .testEmpty(
            "(" + LOINC + "|" + MEMBER_CODE + ").memberOf('" + UNKNOWN_VALUE_SET + "')",
            "An unresolvable value set yields empty, not false")
        .group("memberOf() on a CodeableConcept")
        .withSubject(observationWithMemberFirst())
        .testTrue("code.memberOf('" + VITAL_SIGNS + "')", "First coding is a member")
        .withSubject(observationWithMemberLast())
        .testTrue("code.memberOf('" + VITAL_SIGNS + "')", "Any coding being a member suffices")
        .withSubject(observationWithoutMember())
        .testFalse("code.memberOf('" + VITAL_SIGNS + "')", "No coding is a member")
        .withSubject(observationWithTextOnlyCode())
        .testFalse(
            "code.memberOf('" + VITAL_SIGNS + "')", "A concept with no codings has no member")
        .withSubject(observationWithMemberFirst())
        .testEmpty(
            "code.memberOf('" + UNKNOWN_VALUE_SET + "')",
            "Unresolvable value set stays empty through the any-match over codings")
        .group("memberOf() on a Coding traversed from a resource")
        .withSubject(observationWithMemberFirst())
        .testTrue("code.coding.first().memberOf('" + VITAL_SIGNS + "')")
        .withSubject(observationWithMemberLast())
        .testFalse(
            "code.coding.first().memberOf('" + VITAL_SIGNS + "')",
            "Only the selected coding is tested, unlike the concept-valued form")
        .group("memberOf() empty propagation")
        .testEmpty("{}.memberOf('" + VITAL_SIGNS + "')", "Empty input yields empty")
        .withSubject(observationWithMemberFirst())
        .testEmpty("code.memberOf({})", "Empty value set argument yields empty")
        .withSubject(patientWithoutMaritalStatus())
        .testEmpty("maritalStatus.memberOf('" + VITAL_SIGNS + "')", "Absent field yields empty")
        .group("memberOf() cardinality")
        .withSubject(observationWithMemberFirst())
        .testError(
            CardinalityMismatchException.class,
            "code.coding.memberOf('" + VITAL_SIGNS + "')",
            "Multi-valued input is rejected at compile time per D1")
        .build();
  }
}
