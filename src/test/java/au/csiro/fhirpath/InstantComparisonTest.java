package au.csiro.fhirpath;

import au.csiro.fhirpath.test.FhirPathTestBase;
import java.util.Date;
import java.util.stream.Stream;
import org.hl7.fhir.r4.model.DiagnosticReport;
import org.hl7.fhir.r4.model.InstantType;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Regression tests for comparison and equality between a FHIR {@code instant} field and a FHIRPath
 * DateTime value (literal or another temporal field).
 *
 * <p>The Pathling encoder stores {@code FHIR.instant} as a Spark {@code TimestampType} column, but
 * fp2sql represents DateTime values as ISO-8601 strings. Without a bridge, comparing an {@code
 * instant} field against a DateTime literal caused a {@code ClassCastException} at UDF execution
 * time (see issue #172).
 *
 * <p>Per FHIRPath spec §5.1, {@code instant} is a DateTime value (specialization with timezone and
 * millisecond precision), so comparison between {@code instant} and {@code dateTime} is
 * well-defined.
 */
class InstantComparisonTest extends FhirPathTestBase {

  /** 2005-01-27T06:40:01Z — matches the DiagnosticReport example used by the fhirpath.js suite. */
  private static final long INSTANT_EPOCH_MS = 1106808001000L;

  private static DiagnosticReport createDiagnosticReport() {
    final DiagnosticReport report = new DiagnosticReport();
    report.setId("test-report");
    report.setIssuedElement(new InstantType(new Date(INSTANT_EPOCH_MS)));
    return report;
  }

  // ========== Equality ==========

  @TestFactory
  Stream<DynamicTest> testInstantEqualsDateTimeLiteral() {
    return builder()
        .withSubject(createDiagnosticReport())
        .group("Instant field = DateTime literal (#172)")
        .testTrue(
            "DiagnosticReport.issued = @2005-01-27T06:40:01Z",
            "Equal instant and dateTime literal at seconds precision")
        .testFalse(
            "DiagnosticReport.issued = @2005-01-27T06:40:02Z",
            "Unequal instant and dateTime literal")
        .testFalse(
            "DiagnosticReport.issued != @2005-01-27T06:40:01Z",
            "Not-equals: equal values return false")
        .testTrue(
            "DiagnosticReport.issued != @2005-01-27T06:40:02Z",
            "Not-equals: unequal values return true")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testInstantEqualsDateTimeDifferentPrecision() {
    return builder()
        .withSubject(createDiagnosticReport())
        .group("Instant = DateTime literal at coarser precision")
        .testEmpty(
            "DiagnosticReport.issued = @2005-01-27",
            "Seconds-precision instant vs date-only literal returns empty")
        .testEmpty(
            "DiagnosticReport.issued = @2005-01-27T06:40",
            "Seconds-precision instant vs minutes-precision literal returns empty")
        .build();
  }

  // ========== Comparison ==========

  @TestFactory
  Stream<DynamicTest> testInstantComparisonWithDateTimeLiteral() {
    return builder()
        .withSubject(createDiagnosticReport())
        .group("Instant vs DateTime literal comparison")
        .testTrue(
            "DiagnosticReport.issued < @2005-01-27T06:40:02Z",
            "Instant less than later dateTime literal")
        .testFalse(
            "DiagnosticReport.issued > @2005-01-27T06:40:02Z",
            "Instant not greater than later dateTime literal")
        .testTrue(
            "DiagnosticReport.issued > @2005-01-27T06:40:00Z",
            "Instant greater than earlier dateTime literal")
        .testTrue(
            "DiagnosticReport.issued <= @2005-01-27T06:40:01Z", "Instant <= equal dateTime literal")
        .testTrue(
            "DiagnosticReport.issued >= @2005-01-27T06:40:01Z", "Instant >= equal dateTime literal")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testDateTimeLiteralVsInstant() {
    // Same comparisons with operands reversed — exercises the fix from both sides.
    return builder()
        .withSubject(createDiagnosticReport())
        .group("DateTime literal vs Instant (operand order reversed)")
        .testTrue(
            "@2005-01-27T06:40:01Z = DiagnosticReport.issued",
            "DateTime literal = instant field (reversed)")
        .testTrue(
            "@2005-01-27T06:40:02Z > DiagnosticReport.issued",
            "Later literal greater than instant (reversed)")
        .testTrue(
            "@2005-01-27T06:40:00Z < DiagnosticReport.issued",
            "Earlier literal less than instant (reversed)")
        .build();
  }

  // ========== Regression guards ==========

  @TestFactory
  Stream<DynamicTest> testInstantVsInstantStillWorks() {
    return builder()
        .withSubject(createDiagnosticReport())
        .group("Instant = Instant (regression guard)")
        .testTrue("DiagnosticReport.issued = DiagnosticReport.issued", "Instant equals itself")
        .testTrue("DiagnosticReport.issued <= DiagnosticReport.issued", "Instant <= itself")
        .testTrue("DiagnosticReport.issued >= DiagnosticReport.issued", "Instant >= itself")
        .build();
  }
}
