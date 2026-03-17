package com.example.fhirpath;

import com.example.fhirpath.analyzer.InvalidExpressionException;
import com.example.fhirpath.test.FhirPathTestBase;
import java.util.stream.Stream;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Reference;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Tests for SQL on FHIR v2 key functions: getResourceKey() and getReferenceKey([type]).
 *
 * <p>These functions produce compatible keys (format "ResourceType/id") enabling SQL joins between
 * resources and their references.
 */
class KeyFunctionsTest extends FhirPathTestBase {

  private static Observation createObservation() {
    final Observation obs = new Observation();
    obs.setId("obs-1");
    obs.setSubject(new Reference("Patient/p1"));
    obs.addBasedOn(new Reference("ServiceRequest/sr1"));
    obs.addBasedOn(new Reference("CarePlan/cp1"));
    return obs;
  }

  @TestFactory
  Stream<DynamicTest> testGetResourceKey() {
    return builder()
        .withSubject(createObservation())
        .group("getResourceKey()")
        .testEquals("Observation/obs-1", "getResourceKey()", "Returns ResourceType/id")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testGetReferenceKeyNoType() {
    return builder()
        .withSubject(createObservation())
        .group("getReferenceKey() no type")
        .testEquals(
            "Patient/p1",
            "subject.getReferenceKey()",
            "Returns reference string from singular Reference")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testGetReferenceKeyWithTypeMatch() {
    return builder()
        .withSubject(createObservation())
        .group("getReferenceKey(Type) match")
        .testEquals(
            "Patient/p1",
            "subject.getReferenceKey(Patient)",
            "Type matches — returns reference string")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testGetReferenceKeyWithTypeNoMatch() {
    return builder()
        .withSubject(createObservation())
        .group("getReferenceKey(Type) no match")
        .testEmpty("subject.getReferenceKey(Encounter)", "Type does not match — returns empty")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testGetReferenceKeyCollection() {
    return builder()
        .withSubject(createObservation())
        .group("getReferenceKey() on collection")
        .testEquals(
            "ServiceRequest/sr1",
            "basedOn.getReferenceKey(ServiceRequest).first()",
            "Type filter on collection — matching element")
        .testEquals(
            "CarePlan/cp1",
            "basedOn.getReferenceKey(CarePlan).first()",
            "Type filter on collection — other matching element")
        .testEmpty(
            "basedOn.getReferenceKey(Encounter).first()",
            "Type filter on collection — no match returns empty")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testGetResourceKeyErrors() {
    return builder()
        .withSubject(createObservation())
        .group("getResourceKey() errors")
        .testError(
            InvalidExpressionException.class,
            "subject.getResourceKey()",
            "Called on non-resource target")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testGetReferenceKeyErrors() {
    return builder()
        .withSubject(createObservation())
        .group("getReferenceKey() errors")
        .testError(
            InvalidExpressionException.class,
            "getReferenceKey()",
            "Called on resource root, not Reference")
        .build();
  }
}
