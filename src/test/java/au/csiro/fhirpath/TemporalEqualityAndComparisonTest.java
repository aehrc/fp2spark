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
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Tests for FHIRPath temporal equality and comparison operators.
 *
 * <p>Based on FHIRPath specification section 6.1 (Equality) and section 6.2 (Comparison).
 *
 * <p>Key semantic rule: comparison considers each precision in order. If values differ at any
 * shared precision, the result is {@code false}; if values agree through the shared precision but
 * the precisions themselves differ, the result is empty ({@code {}}). DateTime values are
 * normalized to UTC before comparison.
 *
 * <p>Covers:
 *
 * <ul>
 *   <li>Date equality and comparison at same/different precision
 *   <li>DateTime equality and comparison at same/different precision
 *   <li>Time equality and comparison at same/different precision
 *   <li>Cross-type Date vs DateTime comparison
 *   <li>Fractional seconds normalization
 *   <li>DateTime timezone offset handling (spec examples)
 *   <li>Empty collection propagation
 * </ul>
 */
class TemporalEqualityAndComparisonTest extends FhirPathTestBase {

  // ========== Date equality ==========

  @TestFactory
  Stream<DynamicTest> testDateEquality() {
    return builder()
        .group("Date equality")
        .testTrue("@2014-01-25 = @2014-01-25", "Same date")
        .testFalse("@2014-01-25 = @2014-01-26", "Different day")
        .testTrue("@2014 = @2014", "Same year")
        .testFalse("@2014 = @2015", "Different year")
        .testTrue("@2014-01 = @2014-01", "Same year-month")
        .testFalse("@2014-01 = @2014-02", "Different month")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testDateEqualityDifferentPrecision() {
    return builder()
        .group("Date equality - different precision")
        .testEmpty("@2014 = @2014-01", "Year vs year-month")
        .testEmpty("@2014 = @2014-01-25", "Year vs full date")
        .testEmpty("@2014-01 = @2014-01-25", "Year-month vs full date")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testDateNotEquals() {
    return builder()
        .group("Date not-equals")
        .testFalse("@2014-01-25 != @2014-01-25", "Same date")
        .testTrue("@2014-01-25 != @2014-01-26", "Different day")
        .testEmpty("@2014 != @2014-01", "Different precision returns empty")
        .build();
  }

  // ========== DateTime equality ==========

  @TestFactory
  Stream<DynamicTest> testDateTimeEquality() {
    return builder()
        .group("DateTime equality")
        .testTrue("@2014-01-25T14:30:00 = @2014-01-25T14:30:00", "Same datetime with seconds")
        .testFalse("@2014-01-25T14:30:00 = @2014-01-25T14:30:01", "Different second")
        .testTrue("@2014-01-25T14:30 = @2014-01-25T14:30", "Same datetime minutes precision")
        .testFalse("@2014-01-25T14:30 = @2014-01-25T14:31", "Different minute")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testDateTimeEqualityDifferentPrecision() {
    return builder()
        .group("DateTime equality - different precision")
        .testEmpty("@2014T = @2014-01T", "Year vs year-month")
        .testEmpty("@2014-01-25T14:30 = @2014-01-25T14:30:00", "Minutes vs seconds")
        .build();
  }

  // ========== Time equality ==========

  @TestFactory
  Stream<DynamicTest> testTimeEquality() {
    return builder()
        .group("Time equality")
        .testTrue("@T14:30:00 = @T14:30:00", "Same time with seconds")
        .testFalse("@T14:30:00 = @T14:30:01", "Different second")
        .testTrue("@T14:30 = @T14:30", "Same time minutes precision")
        .testFalse("@T14:30 = @T14:31", "Different minute")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testTimeEqualityDifferentPrecision() {
    return builder()
        .group("Time equality - different precision")
        .testEmpty("@T14:30 = @T14:30:00", "Minutes vs seconds")
        .testEmpty("@T14 = @T14:30", "Hour vs minute time precision")
        .build();
  }

  // ========== Fractional seconds normalization ==========

  @TestFactory
  Stream<DynamicTest> testFractionalSecondsNormalization() {
    return builder()
        .group("Fractional seconds normalization")
        .testTrue("@2012-01-01T10:30:31.0 = @2012-01-01T10:30:31", "Trailing .0 equals no fraction")
        .testTrue(
            "@2012-01-01T10:30:31.00 = @2012-01-01T10:30:31", "Trailing .00 equals no fraction")
        .testFalse(
            "@2012-01-01T10:30:31.1 = @2012-01-01T10:30:31",
            "Non-zero fraction not equal to no fraction")
        .testTrue(
            "@2012-01-01T10:30:31.100 = @2012-01-01T10:30:31.1",
            "Trailing zeros in fraction are equal")
        .build();
  }

  // ========== DateTime with timezone offsets (spec examples) ==========

  @TestFactory
  Stream<DynamicTest> testDateTimeWithTimezoneOffsets() {
    return builder()
        .group("DateTime timezone offsets")
        // Spec examples (lines 3059-3062):
        // -04:00 → UTC: 01:30+4=05:30; -05:00 → UTC: 01:15+5=06:15
        .testFalse(
            "@2017-11-05T01:30:00.0-04:00 > @2017-11-05T01:15:00.0-05:00",
            "Spec: 05:30 UTC > 06:15 UTC is false")
        .testTrue(
            "@2017-11-05T01:30:00.0-04:00 < @2017-11-05T01:15:00.0-05:00",
            "Spec: 05:30 UTC < 06:15 UTC is true")
        .testFalse(
            "@2017-11-05T01:30:00.0-04:00 = @2017-11-05T01:15:00.0-05:00",
            "Spec: 05:30 UTC != 06:15 UTC")
        // Both convert to 05:30 UTC: -04:00 → 01:30+4=05:30; -05:00 → 00:30+5=05:30
        .testTrue(
            "@2017-11-05T01:30:00.0-04:00 = @2017-11-05T00:30:00.0-05:00",
            "Spec: same instant across timezones")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testUtcShorthand() {
    return builder()
        .group("UTC shorthand")
        .testTrue("@2014-01-25T14:30:14.559Z = @2014-01-25T14:30:14.559+00:00", "Z equals +00:00")
        .build();
  }

  // ========== Comparison operators ==========

  @TestFactory
  Stream<DynamicTest> testDateComparison() {
    return builder()
        .group("Date comparison")
        .testTrue("@2014-01-26 > @2014-01-25", "Later date greater")
        .testFalse("@2014-01-25 > @2014-01-26", "Earlier date not greater")
        .testTrue("@2014-01-25 < @2014-01-26", "Earlier date less")
        .testFalse("@2014-01-26 < @2014-01-25", "Later date not less")
        .testTrue("@2014-01-25 >= @2014-01-25", "Same date >=")
        .testTrue("@2014-01-25 <= @2014-01-25", "Same date <=")
        .testTrue("@2014-01-26 >= @2014-01-25", "Later date >=")
        .testTrue("@2014-01-25 <= @2014-01-26", "Earlier date <=")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testDateComparisonDifferentPrecision() {
    return builder()
        .group("Date comparison - different precision")
        .testEmpty("@2014 > @2014-01", "Year vs year-month")
        .testEmpty("@2014 < @2014-01-25", "Year vs full date")
        .testEmpty("@2014-01 >= @2014-01-25", "Year-month vs full date")
        .testEmpty("@2014-01 <= @2014-01-25", "Year-month vs full date")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testDateTimeComparison() {
    return builder()
        .group("DateTime comparison")
        .testTrue("@2014-01-25T14:31 > @2014-01-25T14:30", "Later minute greater")
        .testTrue("@2014-01-25T14:30 < @2014-01-25T14:31", "Earlier minute less")
        .testTrue("@2014-01-25T14:30 >= @2014-01-25T14:30", "Same datetime >=")
        .testTrue("@2014-01-25T14:30 <= @2014-01-25T14:30", "Same datetime <=")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testDateTimeComparisonDifferentPrecision() {
    return builder()
        .group("DateTime comparison - different precision")
        .testEmpty("@2014T > @2014-01T", "Year vs year-month")
        .testEmpty("@2014-01-25T14:30 >= @2014-01-25T14:30:00", "Minutes vs seconds")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testDateTimeComparisonWithTimezoneOffsets() {
    return builder()
        .group("DateTime comparison with timezone offsets")
        .testTrue("@2017-11-05T01:30:00.0-04:00 >= @2017-11-05T01:30:00.0-04:00", "Same instant >=")
        .testTrue("@2017-11-05T01:30:00.0-04:00 <= @2017-11-05T01:30:00.0-04:00", "Same instant <=")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testTimeComparison() {
    return builder()
        .group("Time comparison")
        .testTrue("@T14:31 > @T14:30", "Later time greater")
        .testTrue("@T14:30 < @T14:31", "Earlier time less")
        .testTrue("@T14:30 >= @T14:30", "Same time >=")
        .testTrue("@T14:30 <= @T14:30", "Same time <=")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testTimeComparisonDifferentPrecision() {
    return builder()
        .group("Time comparison - different precision")
        .testEmpty("@T14:30 > @T14:30:00", "Minutes vs seconds")
        .testEmpty("@T14:30 < @T14:30:00", "Minutes vs seconds")
        .build();
  }

  // ========== Empty collection propagation ==========

  @TestFactory
  Stream<DynamicTest> testEmptyCollectionPropagation() {
    return builder()
        .group("Empty collection propagation")
        .testEmpty("{} = @2012", "Empty equals date")
        .testEmpty("@2012 = {}", "Date equals empty")
        .testEmpty("{} > @2012", "Empty greater than date")
        .testEmpty("@2012 > {}", "Date greater than empty")
        .testEmpty("{} != @2012", "Empty not-equals date")
        .build();
  }

  // ========== Issue #175: hour-precision DateTime literal sanity ==========

  @TestFactory
  Stream<DynamicTest> testHourPrecisionDateTimeLiterals() {
    return builder()
        .group("Hour-precision DateTime literal sanity")
        .testTrue("@2018-02-02T22 = @2018-02-02T22", "Same hour-precision literal, no offset")
        .testTrue(
            "@2018-02-02T22+04:00 = @2018-02-02T22+04:00",
            "Same hour-precision literal with offset")
        .testTrue("@2018-02-02T11 != @2018-02-02T12", "Different hours, no offset")
        // Full-date bare-T DateTime: post-normalization shares output length with Date; the
        // analyzer prevents cross-type Date vs DateTime equality from reaching the UDF, but
        // same-type comparisons must still work correctly.
        .testTrue("@2014-01-25T = @2014-01-25T", "Full-date bare-T DateTime same as itself")
        .build();
  }

  // ========== Issue #175: timezone normalization at hour precision ==========

  @TestFactory
  Stream<DynamicTest> testTimezoneNormalizationHourPrecision() {
    return builder()
        .group("Timezone normalization — hour precision")
        // Issue #175 case 1: 22:00-04:00 = 02:00 UTC; 06:00+04:00 = 02:00 UTC (same day)
        .testTrue(
            "@2018-02-02T22-04:00 = @2018-02-03T06+04:00",
            "Issue #175 case 1: equal instants across opposite offsets")
        .testTrue(
            "@2018-02-02T22-06:00 = @2018-02-03T04Z", "Day rollover with negative offset to Z")
        .testTrue(
            "@2018-02-02T03+06:00 = @2018-02-01T21Z", "Reverse rollover with positive offset to Z")
        .testTrue(
            "@2018-02-02T11Z = @2018-02-02T07-04:00",
            "Z compared with negative offset, same UTC instant")
        .testFalse(
            "@2018-02-02T11-04:00 = @2018-02-02T11+04:00",
            "Same wall hour but different offsets → different UTC")
        .build();
  }

  // ========== Issue #175: precision mismatch where values differ at shared precision ==========

  @TestFactory
  Stream<DynamicTest> testTimezoneNormalizationPrecisionMismatchValuesDiffer() {
    return builder()
        .group("Timezone normalization — different precision, values differ at shared")
        // Issue #175 case 2: 22:00-04:00 = 02:00 UTC (hour prec);
        // 05:03+04:00 = 01:03 UTC (minute prec); hour 02 vs 01 differs → false (NOT empty).
        .testFalse(
            "@2018-02-02T22-04:00 = @2018-02-03T05:03+04:00",
            "Issue #175 case 2: differ at shared hour precision after UTC normalization")
        .testTrue(
            "@2018-02-02T22-04:00 != @2018-02-03T05:03+04:00",
            "!= inverts to true when equality is false")
        .testTrue(
            "@2018-02-02T22-04:00 > @2018-02-03T05:03+04:00",
            "02 UTC > 01 UTC at shared hour precision")
        .testFalse(
            "@2018-02-02T22-04:00 < @2018-02-03T05:03+04:00",
            "02 UTC not < 01 UTC at shared hour precision")
        .build();
  }

  // ========== Issue #175: precision mismatch where values agree at shared precision ==========

  @TestFactory
  Stream<DynamicTest> testTimezoneNormalizationPrecisionMismatchValuesAgree() {
    return builder()
        .group("Timezone normalization — different precision, values agree at shared")
        // 22:00-04:00 = 02:00 UTC (hour); 02:30+00:00 = 02:30 UTC (minute); hour 02 == 02
        // → empty (precision mismatch with no value disagreement at shared precision).
        .testEmpty(
            "@2018-02-02T22-04:00 = @2018-02-03T02:30+00:00",
            "Hours match in UTC, minute precision differs → empty")
        // Mirror of the fhirpath-js compat case: 22:00-04:00 = 02 UTC vs 06:03+04:00 = 02:03 UTC.
        .testEmpty(
            "@2018-02-02T22-04:00 = @2018-02-03T06:03+04:00",
            "fhirpath-js mirror: hours match in UTC, minute precision differs")
        .testEmpty(
            "@2018-02-02T22-04:00 != @2018-02-03T02:30+00:00",
            "!= propagates empty for precision mismatch")
        .testEmpty(
            "@2018-02-02T22-04:00 < @2018-02-03T02:30+00:00",
            "Cannot determine order: equal at shared precision, precision mismatch")
        .build();
  }

  // ========== Issue #175: comparison operators with timezone normalization ==========

  @TestFactory
  Stream<DynamicTest> testTimezoneNormalizationComparison() {
    return builder()
        .group("Timezone normalization — comparison operators (equivalent instants)")
        .testTrue("@2018-02-02T22-04:00 >= @2018-02-03T06+04:00", "Equivalent instants → >= true")
        .testTrue("@2018-02-02T22-04:00 <= @2018-02-03T06+04:00", "Equivalent instants → <= true")
        .testFalse(
            "@2018-02-02T22-04:00 > @2018-02-03T06+04:00", "Equivalent instants → strict > false")
        .build();
  }

  // ========== Different-precision values differ at shared precision (no timezone) ==========

  @TestFactory
  Stream<DynamicTest> testPrecisionMismatchValuesDifferNoTimezone() {
    return builder()
        .group("Different-precision equality — values differ at shared precision (no tz)")
        .testFalse("@2018 = @2019-02", "Years differ at year precision")
        .testTrue("@2018-02-03 != @2018-01", "Year-month differs; != inverts to true")
        .testFalse("@2014-01-25T14:31 = @2014-01-25T14:30:00", "Minute precision: 31 vs 30 differ")
        .testTrue("@2014T != @2015-01T", "Partial DateTime year vs year-month; years differ")
        .testFalse("@T14:30 = @T15:00:00", "Time minute vs second; 14:30 vs 15:00 differ")
        .group("Different-precision comparison — values differ at shared precision (no tz)")
        .testTrue("@2018 < @2019-02", "At year precision, 2018 < 2019")
        .testTrue("@2018-02-03 > @2018-01", "Year-month: 02 > 01")
        .build();
  }

  // ========== Issue #174: mixed-precision comparison regression coverage ==========

  @TestFactory
  Stream<DynamicTest> testDateMixedPrecisionComparisonValuesDiffer() {
    return builder()
        .group("Date mixed precision comparison — values differ at shared precision")
        .testTrue("@2018 > @2017-01", "Year vs year-month: 2018 > 2017 at year precision")
        .testTrue("@2020-03-01 >= @2020-02", "Full date vs year-month: 03 > 02 at month")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testDateTimeMixedPrecisionComparisonValuesDiffer() {
    return builder()
        .group("DateTime mixed precision comparison — values differ at shared precision")
        .testTrue("@2018-12-20T12 > @2018-12-20T11:01", "Hour vs minute: 12 > 11 at hour precision")
        .testTrue(
            "@2020-01-01T12:00 >= @2020-01-01T11", "Minute vs hour: 12 > 11 at hour precision")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testTimeMixedPrecisionComparisonValuesDiffer() {
    return builder()
        .group("Time mixed precision comparison — values differ at shared precision")
        .testTrue("@T12:02:34.324 > @T12:01", "Second vs minute: 02 > 01 at minute precision")
        .testTrue("@T10 < @T11:30", "Hour vs minute: 10 < 11 at hour precision")
        .testFalse("@T11:45 < @T10", "Minute vs hour: 11 not < 10 at hour precision")
        .testTrue("@T12:31:45 >= @T12:30", "Second vs minute: 31 > 30 at minute precision")
        .testFalse("@T13:15 > @T14:15:30", "Minute vs second: 13 not > 14 at hour precision")
        .testTrue(
            "@T23:59:59.999999999 > @T00:00", "Sub-second vs minute: 23 > 00 at hour precision")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testCrossTypeDateVsDateTimeComparison() {
    return builder()
        .group("Date vs DateTime cross-type comparison")
        .testTrue(
            "@2020-01-02 > @2020-01-01T10:00:00Z",
            "Date > DateTime: differ at day precision (Z offset)")
        .testFalse(
            "@2020-02-01T10 <= @2020-01",
            "DateTime hour vs Date year-month: months differ at month precision")
        .testTrue("@2018-03-01 < @2018-03-02T00:00:00", "Date < DateTime no offset")
        .testTrue("@2018-03-01 < @2018-03-02T00:00:00Z", "Date < DateTime with Z offset")
        .testTrue(
            "@2018-03-01 < @2018-03-02T00:00:00-01:00", "Date < DateTime with negative offset")
        .build();
  }
}
