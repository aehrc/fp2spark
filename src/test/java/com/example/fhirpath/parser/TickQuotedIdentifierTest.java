package com.example.fhirpath.parser;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.fhirpath.test.FhirPathTestBase;
import java.util.List;
import java.util.stream.Stream;
import org.hl7.fhir.r4.model.Enumerations;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/**
 * Tests for tick-quoted (backtick-delimited) identifiers per FHIRPath §Lexical Elements.
 *
 * <p>Covers issue #211:
 *
 * <ul>
 *   <li>Gap 1 (bug): Tick-quoted FHIR type names in {@code is} / {@code as} / {@code ofType} must
 *       behave identically to bare type names (e.g. {@code FHIR.`Patient`} ≡ {@code FHIR.Patient}).
 *   <li>Gap 2 (bug): Tick-quoted identifiers as path segments must behave identically to bare
 *       identifiers (e.g. {@code name.`family`} ≡ {@code name.family}).
 *   <li>Gap 3 (bug): Backtick-wrapped content with invalid identifier escapes must raise a parse
 *       error. The grammar {@code DELIMITEDIDENTIFIER : '`' (ESC | ~[\\`])* '`'} rejects stray
 *       backslashes inside backticks unless they begin a valid escape sequence.
 * </ul>
 */
class TickQuotedIdentifierTest extends FhirPathTestBase {

  private static Patient createPatient() {
    final Patient patient = new Patient();
    patient.setId("patient-1");
    patient.setActive(true);
    patient.setGender(Enumerations.AdministrativeGender.MALE);
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
  Stream<DynamicTest> testTickQuotedIdentifiersAsPathSegments() {
    return builder()
        .withSubject(createPatient())
        .group("Tick-quoted path segments resolve as normal identifiers")
        .testEquals(List.of("Smith", "Doe"), "name.`family`", "Single backtick-quoted segment")
        .testEquals(
            List.of("John", "James", "Jane"),
            "name.`given`",
            "Backtick-quoted segment traversing MANY → MANY")
        .testEquals(List.of("Smith", "Doe"), "`name`.`family`", "Multiple backtick-quoted segments")
        .testEquals("patient-1", "`id`", "Bare-root backtick-quoted identifier")
        .group("Equivalence with bare identifiers")
        .testTrue("name.`family` = name.family", "Same path whether quoted or not")
        .testTrue(
            "name.`given`.count() = name.given.count()", "Counts match with/without backticks")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testTickQuotedIdentifiersInsideFunctions() {
    return builder()
        .withSubject(createPatient())
        .group("Tick-quoted identifiers inside function arguments")
        .testEquals(
            List.of("Smith"),
            "name.where(`use` = 'official').`family`",
            "Backtick-quoted identifier in where() and result path")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testTickQuotedTypeSpecifiers() {
    return builder()
        .withSubject(createPatient())
        .group("Tick-quoted FHIR type names in is/as/ofType")
        .testTrue("Patient is FHIR.`Patient`", "Binary 'is' with tick-quoted type name")
        .testTrue("Patient.is(FHIR.`Patient`)", "is() function with tick-quoted type name")
        .testTrue(
            "(Patient as FHIR.`Patient`).id = 'patient-1'",
            "Binary 'as' with tick-quoted type name")
        .testTrue(
            "Patient.as(FHIR.`Patient`).id = 'patient-1'",
            "as() function with tick-quoted type name")
        .testEquals(
            "patient-1", "Patient.ofType(FHIR.`Patient`).id", "ofType() with tick-quoted type name")
        .testTrue("Patient.is(`Patient`)", "Tick-quoted unqualified type name in is() function")
        .group("Equivalence with bare type names")
        .testTrue(
            "(Patient is FHIR.`Patient`) = (Patient is FHIR.Patient)",
            "Binary 'is' equivalence with/without backticks")
        .testTrue(
            "Patient.ofType(FHIR.`Patient`).id = Patient.ofType(FHIR.Patient).id",
            "ofType() equivalence with/without backticks")
        .build();
  }

  @Test
  void bareIdentifierPathStillParses() {
    // Regression guard: tightening DELIMITEDIDENTIFIER must not break bare identifiers.
    assertDoesNotThrow(() -> ParserFacade.parse("name.family"));
    assertDoesNotThrow(() -> ParserFacade.parse("Patient.name.given"));
  }

  @Test
  void stringLiteralsStillParse() {
    // Regression guard: grammar change only touches DELIMITEDIDENTIFIER, not STRING.
    assertDoesNotThrow(() -> ParserFacade.parse("'abc'"));
    assertDoesNotThrow(() -> ParserFacade.parse("'line1\\nline2'"));
    assertDoesNotThrow(() -> ParserFacade.parse("'escaped: \\\\ \\' \\\" \\u0041'"));
  }

  @Test
  void backtickContentWithValidEscapesParses() {
    // `\`` is a valid ESC sequence: backslash + backtick.
    assertDoesNotThrow(() -> ParserFacade.parse("`weird\\`field`"));
    // Unicode escape is valid too.
    assertDoesNotThrow(() -> ParserFacade.parse("`P\\u0065ter`"));
  }

  @Test
  void backtickContentWithInvalidEscapeIsParseError() {
    // `\b` — b is not in the allowed ESC character class [`'\\/fnrt], so ~[\\`] excludes
    // the stray backslash and the lexer fails.
    assertThrows(IllegalArgumentException.class, () -> ParserFacade.parse("`a\\b`"));
  }

  @Test
  void backtickContentWithStrayBackslashBeforeQuoteIsParseError() {
    // `abc\"` — " is not in the ESC set, so the stray backslash breaks lexing.
    assertThrows(IllegalArgumentException.class, () -> ParserFacade.parse("`abc\\\"`"));
  }

  @Test
  void backtickWrappedStringWithMixedInvalidEscapesIsParseError() {
    // The exact expression from the compat exclusion — spec says backtick-wrapping a string
    // with many escapes is a parse error.
    assertThrows(
        IllegalArgumentException.class,
        () -> ParserFacade.parse("`a\\b\\'\\\"\\`\\r\\n\\t\\u0065`"));
  }
}
