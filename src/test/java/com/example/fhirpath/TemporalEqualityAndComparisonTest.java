package com.example.fhirpath;

import com.example.fhirpath.test.FhirPathTestBase;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Tests for FHIRPath temporal equality and comparison operators.
 *
 * <p>Based on FHIRPath specification section 6.1 (Equality) and section 6.2 (Comparison).
 *
 * <p>Key semantic rule: when two temporal values have different precision levels, equality and
 * comparison return empty ({@code {}}), not false.
 *
 * <p>Covers:
 *
 * <ul>
 *   <li>Date equality and comparison at same/different precision
 *   <li>DateTime equality and comparison at same/different precision
 *   <li>Time equality and comparison at same/different precision
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
}
