package au.csiro.fhirpath;

import au.csiro.fhirpath.test.FhirPathTestBase;
import java.util.stream.Stream;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.HumanName;
import org.hl7.fhir.r4.model.IntegerType;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.StringType;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Tests for the FHIRPath extension() function.
 *
 * <p>Per the FHIRPath spec, {@code extension(url)} is syntactic sugar for {@code
 * .extension.where(url = '<url>')}. The no-arg {@code extension()} variant returns all extensions
 * on the element.
 *
 * <p>Uses HAPI Patient objects with Pathling's FhirEncoders which produce the flat schema with
 * {@code _fid}/{@code _extension} map.
 */
class ExtensionFunctionTest extends FhirPathTestBase {

  private static Patient createPatientWithExtensions() {
    final Patient patient = new Patient();
    patient.setId("test-patient");
    patient.addExtension("http://example.org/stringExt", new StringType("hello"));
    patient.addExtension("http://example.org/intExt", new IntegerType(42));
    patient.addExtension("http://example.org/multiExt", new StringType("first"));
    patient.addExtension("http://example.org/multiExt", new StringType("second"));
    final HumanName name = patient.addName().setFamily("Smith");
    name.addExtension("http://example.org/nameExt", new StringType("name-ext-val"));
    // Nested extension: parent extension with two sub-extensions
    final Extension parentExt = new Extension("http://example.org/parent");
    parentExt.addExtension("http://example.org/nested-a", new StringType("nested-a-val"));
    parentExt.addExtension("http://example.org/nested-b", new IntegerType(99));
    patient.addExtension(parentExt);
    return patient;
  }

  @TestFactory
  Stream<DynamicTest> testExtensionByUrl() {
    return builder()
        .withSubject(createPatientWithExtensions())
        .group("extension(url) basic")
        .testTrue(
            "extension('http://example.org/stringExt').exists()", "Extension matching URL exists")
        .testEquals(
            "hello",
            "extension('http://example.org/stringExt').value.ofType(string).first()",
            "Extract string extension value")
        .testEquals(
            42,
            "extension('http://example.org/intExt').value.ofType(integer).first()",
            "Extract integer extension value")
        .testEmpty("extension('http://example.org/nonExistent')", "Non-matching URL returns empty")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testExtensionNoArg() {
    return builder()
        .withSubject(createPatientWithExtensions())
        .group("extension() no-arg")
        .testTrue("extension().exists()", "No-arg extension returns all extensions")
        .testEquals(5, "extension().count()", "Count of all resource-level extensions")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testMultipleSameUrlExtensions() {
    return builder()
        .withSubject(createPatientWithExtensions())
        .group("Multiple same-URL extensions")
        .testEquals(
            2,
            "extension('http://example.org/multiExt').count()",
            "Count of extensions with same URL")
        .testEquals(
            "first",
            "extension('http://example.org/multiExt').value.ofType(string).first()",
            "First value of multi-extension")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testExtensionOnSubElements() {
    return builder()
        .withSubject(createPatientWithExtensions())
        .group("Extensions on sub-elements")
        .testTrue(
            "name.extension('http://example.org/nameExt').exists()",
            "Extension on nested HumanName element")
        .testEquals(
            "name-ext-val",
            "name.extension('http://example.org/nameExt').value.ofType(string).first()",
            "Extract extension value from sub-element")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testNestedExtensions() {
    return builder()
        .withSubject(createPatientWithExtensions())
        .group("Nested extensions")
        .testTrue("extension('http://example.org/parent').exists()", "Parent extension exists")
        .testEquals(
            "nested-a-val",
            "extension('http://example.org/parent').extension('http://example.org/nested-a').value.ofType(string).first()",
            "Access nested extension by URL")
        .testEquals(
            99,
            "extension('http://example.org/parent').extension('http://example.org/nested-b').value.ofType(integer).first()",
            "Access nested integer extension")
        .testEquals(
            2,
            "extension('http://example.org/parent').extension().count()",
            "Count nested extensions")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testExtensionEdgeCases() {
    return builder()
        .group("extension() edge cases")
        .withSubject(createPatientWithExtensions())
        .testEmpty("extension('')", "Empty URL returns empty")
        .withSubject(new Patient())
        .testEmpty("extension('http://example.org/any')", "No extensions on resource")
        .testEquals(0, "extension().count()", "No-arg count on resource with no extensions")
        .build();
  }
}
