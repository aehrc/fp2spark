package au.csiro.fhirpath.ir.fhir;

import au.csiro.fhirpath.test.FhirPathTestBase;
import au.csiro.fhirpath.typing.FhirPrimitiveType;
import au.csiro.fhirpath.typing.FieldSpec;
import au.csiro.fhirpath.typing.InlineResourceType;
import au.csiro.fhirpath.typing.ResourceType;
import au.csiro.fhirpath.typing.Shape;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Integration tests for FHIR primitive type traversal and conversion with Spark.
 *
 * <p>Verifies:
 *
 * <ul>
 *   <li>Traversing FHIR fields and extracting values
 *   <li>Comparing FHIR fields with System literals (implicit conversion)
 *   <li>{@code getValue()} and {@code hasValue()} semantics
 *   <li>Multiple FHIR type mappings (string, boolean, date, integer)
 * </ul>
 */
class FhirPrimitiveTypeConversionTest extends FhirPathTestBase {

  private static ResourceType patientType() {
    return new InlineResourceType(
        "Patient",
        new FieldSpec("id", Shape.single(FhirPrimitiveType.of("id"))),
        new FieldSpec("active", Shape.single(FhirPrimitiveType.of("boolean"))),
        new FieldSpec("gender", Shape.single(FhirPrimitiveType.of("code"))),
        new FieldSpec("birthDate", Shape.single(FhirPrimitiveType.of("date"))));
  }

  @TestFactory
  Stream<DynamicTest> testFieldTraversal() {
    return builder()
        .withSubject(
            patientType(),
            sb ->
                sb.string("id", "patient-1")
                    .bool("active", true)
                    .string("gender", "male")
                    .string("birthDate", "2024-01-15"))
        .group("FHIR field traversal")
        .testEquals("patient-1", "id", "String field (id)")
        .testEquals(true, "active", "Boolean field")
        .testEquals("male", "gender", "Code field (→ string)")
        .testEquals("2024-01-15", "birthDate", "Date field")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testFhirDateComparisonWithLiteral() {
    return builder()
        .withSubject(patientType(), sb -> sb.string("birthDate", "2024-01-15"))
        .group("FHIR date comparison with System literal")
        .testTrue("birthDate = @2024-01-15", "Date equality with system literal")
        .testTrue("birthDate > @2024-01-01", "Date comparison with system literal")
        .testFalse("birthDate < @2024-01-01", "Date less-than comparison")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testGetValueAndHasValue() {
    return builder()
        .withSubject(patientType(), sb -> sb.string("id", "patient-1").bool("active", true))
        .group("getValue() and hasValue()")
        .testEquals("patient-1", "id.getValue()", "getValue() returns the value")
        .testTrue("id.hasValue()", "hasValue() returns true for non-null")
        .testTrue("active.hasValue()", "hasValue() for boolean")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testGetValueAndHasValueForNull() {
    return builder()
        .withSubject(patientType(), sb -> sb.string("id", "patient-1"))
        .group("hasValue() with null")
        .testFalse("gender.hasValue()", "hasValue() returns false for null field")
        .build();
  }

  /**
   * Regression tests for #180 — conversion functions declared with {@code ANY} parameters (e.g.
   * {@code toString()}, {@code toBoolean()}) must accept FHIR primitive arguments by applying the
   * implicit FHIR→System coercion defined in FHIRPath §5.3. Before the fix the analyzer raised
   * "Expected SystemType" for every FHIR-typed input.
   */
  @TestFactory
  Stream<DynamicTest> testConversionFunctionsAcceptFhirPrimitives() {
    return builder()
        .withSubject(
            patientType(),
            sb ->
                sb.string("id", "patient-1")
                    .bool("active", true)
                    .string("gender", "male")
                    .string("birthDate", "2024-01-15"))
        .group("toString() on FHIR primitives (#180)")
        .testEquals("patient-1", "id.toString()", "FHIR.id → System.String")
        .testEquals("true", "active.toString()", "FHIR.boolean → System.String")
        .testEquals("male", "gender.toString()", "FHIR.code → System.String")
        .testEquals("2024-01-15", "birthDate.toString()", "FHIR.date → System.String")
        .group("toBoolean()/toInteger() on FHIR primitives (#180)")
        .testTrue("active.toBoolean()", "FHIR.boolean → System.Boolean via toBoolean()")
        .testTrue("active.convertsToBoolean()", "convertsToBoolean() accepts FHIR boolean")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testToStringOnFhirDecimalAndInteger() {
    final ResourceType observationType =
        new InlineResourceType(
            "Observation",
            new FieldSpec("status", Shape.single(FhirPrimitiveType.of("code"))),
            new FieldSpec("valueInteger", Shape.single(FhirPrimitiveType.of("integer"))));
    return builder()
        .withSubject(
            observationType, sb -> sb.string("status", "final").integer("valueInteger", 42))
        .group("toString() on FHIR numeric primitives (#180)")
        .testEquals("42", "valueInteger.toString()", "FHIR.integer → System.String")
        .testEquals("final", "status.toString()", "FHIR.code → System.String")
        .build();
  }
}
