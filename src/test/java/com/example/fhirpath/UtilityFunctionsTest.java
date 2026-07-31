package com.example.fhirpath;

import com.example.fhirpath.operation.OverloadResolutionException;
import com.example.fhirpath.test.FhirPathTestBase;
import java.util.List;
import java.util.stream.Stream;
import org.hl7.fhir.r4.model.HumanName;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Tests for FHIRPath utility functions: trace().
 *
 * <p>Based on FHIRPath specification section 5.9 (Utility functions).
 *
 * <p>{@code trace()} is a pass-through: it returns its input collection unaltered. fp2sql
 * implements the pass-through only — the diagnostic output is a deliberate no-op, since there is no
 * evaluation context to carry a diagnostic sink (see #277). The tests therefore assert that the
 * input survives untouched, that the optional projection never influences the result, and that the
 * result's type and cardinality are preserved exactly.
 *
 * <p>Covers:
 *
 * <ul>
 *   <li>Core semantics: literal collections, singular values, chained traces
 *   <li>Projection: ignored for the result, evaluated against the input collection ($this)
 *   <li>Emptiness: empty literal, computed empty, absent resource field
 *   <li>Cardinality: singular (0..1) and non-singular (0..*) resource fields
 *   <li>Type/cardinality transparency: downstream operations behave as if trace() were absent
 *   <li>Arity errors: too few / too many arguments
 * </ul>
 */
class UtilityFunctionsTest extends FhirPathTestBase {

  private static Patient createPatient() {
    final Patient patient = new Patient();
    patient.setId("patient-1");
    patient.setGender(org.hl7.fhir.r4.model.Enumerations.AdministrativeGender.MALE);
    patient.addName(new HumanName().setFamily("Smith").addGiven("John").addGiven("Jane"));
    patient.addName(new HumanName().setFamily("Doe"));
    return patient;
  }

  @TestFactory
  Stream<DynamicTest> testTraceCoreSemantics() {
    return builder()
        .group("trace() returns the input collection unaltered")
        .testEquals(
            List.of(1, 2, 3, 4, 5, 6),
            "(1 ; 2 ; 3 ; 4 ; 5 ; 6).trace('coll')",
            "Integer collection")
        .testEquals(List.of("a", "b"), "('a' ; 'b').trace('t')", "String collection")
        .testEquals("abc", "'abc'.trace('t')", "Singular string")
        .testEquals(42, "42.trace('t')", "Singular integer")
        .testTrue("true.trace('t')", "Singular boolean")
        .group("trace() name is not restricted to a literal")
        .testEquals("abc", "'abc'.trace('a' + 'b')", "Computed name is accepted")
        .group("trace() chained")
        .testEquals(
            List.of("a", "b"), "('a' ; 'b').trace('first').trace('second')", "Two traces in a row")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testTraceProjectionDoesNotAffectResult() {
    return builder()
        .group("trace(name, projection) still returns the input")
        .testEquals(
            List.of(1, 2, 3),
            "(1 ; 2 ; 3).trace('t', $this.count())",
            "Aggregating projection is discarded")
        .testEquals(
            List.of(1, 2, 3),
            "(1 ; 2 ; 3).trace('t', $this.select($this * 2))",
            "Projection producing different values is discarded")
        .testEquals(List.of(1, 2, 3), "(1 ; 2 ; 3).trace('t', 'unrelated')", "Constant projection")
        .testEquals(List.of(1, 2, 3), "(1 ; 2 ; 3).trace('t', {})", "Empty projection")
        .testEquals("abc", "'abc'.trace('t', $this.upper())", "Projection on a singular input")
        .group("projection is evaluated against the input collection")
        .withSubject(createPatient())
        .testEquals(
            List.of("Smith", "Doe"),
            "name.trace('names', given).family",
            "Spec-style projection over a traced collection leaves it unchanged")
        .testEquals(
            2,
            "name.trace('names', $this.count()).count()",
            "$this in the projection is the input collection")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testTraceEmptyPropagation() {
    return builder()
        .group("trace() on empty input")
        .testEmpty("{}.trace('t')", "Empty literal returns empty")
        .testEmpty("{}.trace('t', $this)", "Empty literal with projection returns empty")
        .testEmpty("(1 ; 2 ; 3).where(false).trace('t')", "Computed empty returns empty")
        .group("trace() on an absent resource field")
        .withSubject(createPatient())
        .testEmpty("birthDate.trace('bd')", "Absent singular field returns empty")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testTraceCardinalityAndTypeTransparency() {
    return builder()
        .withSubject(createPatient())
        .group("trace() on resource fields")
        .testEquals("male", "gender.trace('g')", "Singular field (0..1)")
        .testEquals(List.of("Smith", "Doe"), "name.family.trace('f')", "Non-singular field (0..*)")
        .testEquals(
            List.of("Smith", "Doe"), "name.trace('n').family", "Complex-typed collection traversed")
        .group("trace() preserves cardinality for downstream operations")
        .testEquals(1, "gender.trace('g').count()", "Singular stays singular")
        .testEquals(2, "name.family.trace('f').count()", "Collection stays a collection")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testTraceTypeTransparencyOnLiterals() {
    return builder()
        .group("trace() preserves the static type")
        .testEquals(2, "1.trace('t') + 1", "Integer arithmetic after trace")
        .testEquals(
            List.of(2, 4, 6), "(1 ; 2 ; 3).trace('t').select($this * 2)", "Lambda after trace")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testTraceArityErrors() {
    return builder()
        .group("trace() arity")
        .testError(OverloadResolutionException.class, "1.trace()", "The name argument is required")
        .testError(
            OverloadResolutionException.class,
            "1.trace('a', $this, 'extra')",
            "At most two arguments are accepted")
        .build();
  }
}
