package com.example.fhirpath.ir.fhir;

import ca.uhn.fhir.context.FhirContext;
import com.example.fhirpath.test.FhirPathTestBase;
import com.example.fhirpath.typing.FhirPrimitiveType;
import com.example.fhirpath.typing.FieldSpec;
import com.example.fhirpath.typing.HapiTypeResolver;
import com.example.fhirpath.typing.ResourceType;
import com.example.fhirpath.typing.Shape;
import com.example.fhirpath.typing.TypeResolver;
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

  private static final TypeResolver HAPI_RESOLVER = new HapiTypeResolver(FhirContext.forR4Cached());

  private static ResourceType patientType() {
    return new ResourceType(
        "Patient",
        new FieldSpec("id", Shape.single(FhirPrimitiveType.of("id"))),
        new FieldSpec("active", Shape.single(FhirPrimitiveType.of("boolean"))),
        new FieldSpec("gender", Shape.single(FhirPrimitiveType.of("code"))),
        new FieldSpec("birthDate", Shape.single(FhirPrimitiveType.of("date"))));
  }

  @TestFactory
  Stream<DynamicTest> testFieldTraversal() {
    return builder()
        .withTypeResolver(HAPI_RESOLVER)
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
        .withTypeResolver(HAPI_RESOLVER)
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
        .withTypeResolver(HAPI_RESOLVER)
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
        .withTypeResolver(HAPI_RESOLVER)
        .withSubject(patientType(), sb -> sb.string("id", "patient-1"))
        .group("hasValue() with null")
        .testFalse("gender.hasValue()", "hasValue() returns false for null field")
        .build();
  }
}
