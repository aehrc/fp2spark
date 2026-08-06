package au.csiro.fhirpath;

import au.csiro.fhirpath.test.FhirPathTestBase;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Tests for FHIRPath date/time arithmetic operators (Date/DateTime/Time +/- Quantity).
 *
 * <p>Based on FHIRPath specification section 6.3 (Date/Time Arithmetic), lines 3681-3866.
 *
 * <p>Covers:
 *
 * <ul>
 *   <li>Date + Quantity (years, months, weeks, days)
 *   <li>Date - Quantity (years, months, days)
 *   <li>Partial date precision handling (spec examples for year-only, year-month)
 *   <li>DateTime + Quantity (hours, minutes, days)
 *   <li>Time + Quantity (hours, minutes)
 *   <li>DateTime - Quantity (hours, minutes)
 *   <li>Time - Quantity (hours, minutes)
 *   <li>Invalid unit for type (returns empty per Spark-NULL convention)
 *   <li>Calendar duration keyword variants (singular/plural)
 *   <li>Month overflow and leap year edge cases
 * </ul>
 */
public class TemporalArithmeticTest extends FhirPathTestBase {

  // ===== Date + Quantity =====

  @TestFactory
  Stream<DynamicTest> testDateAddition() {
    return builder()
        .group("Date + years")
        .testTrue("@2014-01-25 + 1 year = @2015-01-25", "Full date + 1 year")
        .group("Date + months")
        .testTrue("@2014-01-25 + 1 month = @2014-02-25", "Full date + 1 month")
        .group("Date + weeks")
        .testTrue("@2014-01-01 + 2 weeks = @2014-01-15", "Full date + 2 weeks (14 days)")
        .group("Date + days")
        .testTrue("@2014-01-25 + 5 days = @2014-01-30", "Full date + 5 days")
        .group("Calendar duration keyword variants")
        .testTrue("@2014-01-25 + 1 years = @2015-01-25", "Plural 'years' keyword")
        .build();
  }

  // ===== Date - Quantity =====

  @TestFactory
  Stream<DynamicTest> testDateSubtraction() {
    return builder()
        .group("Date - years")
        .testTrue("@2014-01-25 - 1 year = @2013-01-25", "Full date - 1 year")
        .group("Date - months")
        .testTrue("@2014-03-25 - 2 months = @2014-01-25", "Full date - 2 months")
        .group("Date - days")
        .testTrue("@2014-01-25 - 5 days = @2014-01-20", "Full date - 5 days")
        .build();
  }

  // ===== Partial date precision handling (spec examples) =====

  @TestFactory
  Stream<DynamicTest> testPartialDatePrecision() {
    return builder()
        .group("Year-only date + months (spec examples)")
        .testTrue("@2014 + 24 months = @2016", "24 months converts to 2 years")
        .testTrue("@2014 + 23 months = @2015", "23 months truncates to 1 year")
        .group("Year-only date + days")
        .testTrue("@2016 + 365 days = @2017", "365 days converts to 1 year")
        .group("Year-month date + days")
        .testTrue("@2014-01 + 30 days = @2014-02", "30 days converts to 1 month")
        .group("Year-only date - months (spec example)")
        .testTrue("@2014 - 24 months = @2012", "24 months converts to 2 years (subtraction)")
        .build();
  }

  // ===== DateTime + Quantity =====

  @TestFactory
  Stream<DynamicTest> testDateTimeAddition() {
    return builder()
        .group("DateTime + hours")
        .testTrue("@2014-01-25T14:00:00 + 2 hours = @2014-01-25T16:00:00", "DateTime + 2 hours")
        .group("DateTime + minutes")
        .testTrue(
            "@2014-01-25T14:30 + 30 minutes = @2014-01-25T15:00",
            "DateTime + 30 minutes crosses hour boundary")
        .group("DateTime + days")
        .testTrue(
            "@2014-01-25T14:30 + 1 day = @2014-01-26T14:30",
            "DateTime + 1 day preserves time component")
        .build();
  }

  // ===== Time + Quantity =====

  @TestFactory
  Stream<DynamicTest> testTimeAddition() {
    return builder()
        .group("Time + hours")
        .testTrue("@T14:00 + 2 hours = @T16:00", "Time + 2 hours")
        .group("Time + minutes")
        .testTrue("@T14:30:00 + 30 minutes = @T15:00:00", "Time + 30 minutes crosses hour boundary")
        .build();
  }

  // ===== DateTime - Quantity =====

  @TestFactory
  Stream<DynamicTest> testDateTimeSubtraction() {
    return builder()
        .group("DateTime - hours")
        .testTrue("@2014-01-25T16:00:00 - 2 hours = @2014-01-25T14:00:00", "DateTime - 2 hours")
        .group("DateTime - minutes")
        .testTrue(
            "@2014-01-25T15:00 - 30 minutes = @2014-01-25T14:30",
            "DateTime - 30 minutes crosses hour boundary")
        .build();
  }

  // ===== Time - Quantity =====

  @TestFactory
  Stream<DynamicTest> testTimeSubtraction() {
    return builder()
        .group("Time - hours")
        .testTrue("@T16:00 - 2 hours = @T14:00", "Time - 2 hours")
        .group("Time - minutes")
        .testTrue("@T15:00:00 - 30 minutes = @T14:30:00", "Time - 30 minutes crosses hour boundary")
        .build();
  }

  // ===== Invalid unit for type (spec says "signal error"; returns empty per Spark convention)
  // =====

  @TestFactory
  Stream<DynamicTest> testInvalidUnitForType() {
    return builder()
        .group("Date + time-only units (invalid)")
        .testEmpty("@2014-01-25 + 2 hours", "Hours not valid for Date")
        .group("Time + date-only units (invalid)")
        .testEmpty("@T14:00 + 1 year", "Years not valid for Time")
        .build();
  }

  // ===== Month overflow and leap year edge cases =====

  @TestFactory
  Stream<DynamicTest> testMonthOverflowAndLeapYear() {
    return builder()
        .group("Month overflow (last day of month)")
        .testTrue(
            "@2014-01-31 + 1 month = @2014-02-28", "Jan 31 + 1 month clamps to Feb 28 (non-leap)")
        .group("Leap year edge case")
        .testTrue(
            "@2016-02-29 + 1 year = @2017-02-28",
            "Feb 29 + 1 year clamps to Feb 28 (non-leap target)")
        .build();
  }

  // ===== DateTime partial precision =====

  @TestFactory
  Stream<DynamicTest> testDateTimePartialPrecision() {
    return builder()
        .group("DateTime partial + quantity")
        .testTrue("@2014T + 1 year = @2015T", "Year-only DateTime + 1 year")
        .testTrue("@2014-01T + 1 month = @2014-02T", "Year-month DateTime + 1 month")
        .testTrue("@2014-01-25T + 1 day = @2014-01-26T", "Date-only DateTime + 1 day")
        .build();
  }
}
