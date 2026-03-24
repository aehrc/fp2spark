package com.example.fhirpath;

import com.example.fhirpath.test.FhirPathTestBase;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Tests for FHIRPath type conversion functions (toX) and validation functions (convertsToX).
 *
 * <p>Based on FHIRPath spec section 5.7 — Type Functions.
 */
public class TypeConversionTest extends FhirPathTestBase {

  // ========== toBoolean() ==========

  @TestFactory
  Stream<DynamicTest> testToBoolean() {
    return builder()
        .group("toBoolean - identity")
        .testTrue("true.toBoolean()")
        .testFalse("false.toBoolean()")
        .group("toBoolean - from Integer")
        .testTrue("1.toBoolean()", "Integer 1 → true")
        .testFalse("0.toBoolean()", "Integer 0 → false")
        .testEmpty("2.toBoolean()", "Integer 2 → empty")
        .group("toBoolean - from Decimal")
        .testTrue("1.0.toBoolean()", "Decimal 1.0 → true")
        .testFalse("0.0.toBoolean()", "Decimal 0.0 → false")
        .testEmpty("2.5.toBoolean()", "Decimal 2.5 → empty")
        .group("toBoolean - from String")
        .testTrue("'true'.toBoolean()", "String 'true' → true")
        .testFalse("'false'.toBoolean()", "String 'false' → false")
        .testTrue("'t'.toBoolean()", "String 't' → true")
        .testFalse("'f'.toBoolean()", "String 'f' → false")
        .testTrue("'1'.toBoolean()", "String '1' → true")
        .testFalse("'0'.toBoolean()", "String '0' → false")
        .testTrue("'1.0'.toBoolean()", "String '1.0' → true")
        .testFalse("'0.0'.toBoolean()", "String '0.0' → false")
        .testTrue("'yes'.toBoolean()", "String 'yes' → true")
        .testFalse("'no'.toBoolean()", "String 'no' → false")
        .testTrue("'y'.toBoolean()", "String 'y' → true")
        .testFalse("'n'.toBoolean()", "String 'n' → false")
        .group("toBoolean - case insensitive strings")
        .testTrue("'TRUE'.toBoolean()", "Case insensitive 'TRUE' → true")
        .testFalse("'FALSE'.toBoolean()", "Case insensitive 'FALSE' → false")
        .testTrue("'Yes'.toBoolean()", "Case insensitive 'Yes' → true")
        .testFalse("'No'.toBoolean()", "Case insensitive 'No' → false")
        .testTrue("'T'.toBoolean()", "Case insensitive 'T' → true")
        .testFalse("'F'.toBoolean()", "Case insensitive 'F' → false")
        .testEmpty("'abc'.toBoolean()", "Invalid string → empty")
        .group("toBoolean - empty propagation")
        .testEmpty("{}.toBoolean()", "Empty → empty")
        .build();
  }

  // ========== toInteger() ==========

  @TestFactory
  Stream<DynamicTest> testToInteger() {
    return builder()
        .group("toInteger - identity")
        .testEquals(42, "42.toInteger()")
        .testEquals(0, "0.toInteger()")
        .group("toInteger - from Boolean")
        .testEquals(1, "true.toInteger()", "true → 1")
        .testEquals(0, "false.toInteger()", "false → 0")
        .group("toInteger - from String")
        .testEquals(123, "'123'.toInteger()", "Valid integer string")
        .testEquals(-42, "'-42'.toInteger()", "Negative integer string")
        .testEmpty("'3.14'.toInteger()", "Decimal string → empty")
        .testEmpty("'abc'.toInteger()", "Non-numeric string → empty")
        .group("toInteger - non-convertible types")
        .testEmpty("1.5.toInteger()", "Decimal → empty")
        .group("toInteger - empty propagation")
        .testEmpty("{}.toInteger()", "Empty → empty")
        .build();
  }

  // ========== toDecimal() ==========

  @TestFactory
  Stream<DynamicTest> testToDecimal() {
    return builder()
        .group("toDecimal - identity")
        .testEquals(3.14, "3.14.toDecimal()")
        .group("toDecimal - from Integer")
        .testEquals(42.0, "42.toDecimal()", "Integer → Decimal")
        .group("toDecimal - from Boolean")
        .testEquals(1.0, "true.toDecimal()", "true → 1.0")
        .testEquals(0.0, "false.toDecimal()", "false → 0.0")
        .group("toDecimal - from String")
        .testEquals(3.14, "'3.14'.toDecimal()", "Decimal string")
        .testEquals(42.0, "'42'.toDecimal()", "Integer string → Decimal")
        .testEmpty("'abc'.toDecimal()", "Non-numeric string → empty")
        .group("toDecimal - empty propagation")
        .testEmpty("{}.toDecimal()", "Empty → empty")
        .build();
  }

  // ========== toString() ==========

  @TestFactory
  Stream<DynamicTest> testToString() {
    return builder()
        .group("toString - identity")
        .testEquals("hello", "'hello'.toString()")
        .group("toString - from Boolean")
        .testEquals("true", "true.toString()", "true → 'true'")
        .testEquals("false", "false.toString()", "false → 'false'")
        .group("toString - from Integer")
        .testEquals("42", "42.toString()", "Integer → String")
        .testEquals("0", "0.toString()", "Zero → '0'")
        .group("toString - from Decimal")
        .testEquals("3.14", "3.14.toString()", "Decimal → String")
        .group("toString - from Date")
        .testEquals("2023-06-15", "@2023-06-15.toString()", "Date → String")
        .group("toString - from DateTime")
        .testEquals("2023-06-15T10:30:00", "@2023-06-15T10:30:00.toString()", "DateTime → String")
        .group("toString - from Time")
        .testEquals("14:30:00", "@T14:30:00.toString()", "Time → String")
        .group("toString - from Quantity")
        .testEquals("10 'mg'", "10 'mg'.toString()", "Quantity with unit → String")
        .testEquals("42", "42 '1'.toString()", "Quantity with default unit → just value")
        .group("toString - empty propagation")
        .testEmpty("{}.toString()", "Empty → empty")
        .build();
  }

  // ========== toDate() ==========

  @TestFactory
  Stream<DynamicTest> testToDate() {
    return builder()
        .group("toDate - identity")
        .testEquals("2023-06-15", "@2023-06-15.toDate()")
        .group("toDate - from String")
        .testEquals("2023-06-15", "'2023-06-15'.toDate()", "Full date string")
        .testEquals("2023-06", "'2023-06'.toDate()", "Partial date: year-month")
        .testEquals("2023", "'2023'.toDate()", "Partial date: year only")
        .testEmpty("'not-a-date'.toDate()", "Invalid date string → empty")
        .testEmpty("'2023-06-15T10:00'.toDate()", "DateTime string → empty")
        .group("toDate - from DateTime")
        .testEquals("2023-06-15", "@2023-06-15T10:30:00.toDate()", "DateTime → Date")
        .group("toDate - non-convertible types")
        .testEmpty("42.toDate()", "Integer → empty")
        .group("toDate - empty propagation")
        .testEmpty("{}.toDate()", "Empty → empty")
        .build();
  }

  // ========== toDateTime() ==========

  @TestFactory
  Stream<DynamicTest> testToDateTime() {
    return builder()
        .group("toDateTime - identity")
        .testEquals("2023-06-15T10:30:00", "@2023-06-15T10:30:00.toDateTime()")
        .group("toDateTime - from String")
        .testEquals(
            "2023-06-15T10:30:00", "'2023-06-15T10:30:00'.toDateTime()", "Full datetime string")
        .testEquals("2023-06-15", "'2023-06-15'.toDateTime()", "Date-only datetime string")
        .testEquals("2023", "'2023'.toDateTime()", "Partial: year only")
        .testEmpty("'not-a-datetime'.toDateTime()", "Invalid → empty")
        .group("toDateTime - from Date")
        .testEquals("2023-06-15", "@2023-06-15.toDateTime()", "Date → DateTime")
        .group("toDateTime - non-convertible types")
        .testEmpty("42.toDateTime()", "Integer → empty")
        .group("toDateTime - empty propagation")
        .testEmpty("{}.toDateTime()", "Empty → empty")
        .build();
  }

  // ========== toTime() ==========

  @TestFactory
  Stream<DynamicTest> testToTime() {
    return builder()
        .group("toTime - identity")
        .testEquals("14:30:00", "@T14:30:00.toTime()")
        .group("toTime - from String")
        .testEquals("14:30:00", "'14:30:00'.toTime()", "Full time string")
        .testEquals("14:30", "'14:30'.toTime()", "Partial: hour:minute")
        .testEquals("14", "'14'.toTime()", "Partial: hour only")
        .testEmpty("'not-a-time'.toTime()", "Invalid → empty")
        .group("toTime - non-convertible types")
        .testEmpty("42.toTime()", "Integer → empty")
        .group("toTime - empty propagation")
        .testEmpty("{}.toTime()", "Empty → empty")
        .build();
  }

  // ========== toQuantity() ==========

  @TestFactory
  Stream<DynamicTest> testToQuantity() {
    return builder()
        .group("toQuantity - identity")
        .testTrue("10 'mg' = 10 'mg'.toQuantity()", "Quantity identity")
        .group("toQuantity - from Integer")
        .testTrue("42.toQuantity() = 42 '1'", "Integer → Quantity with default unit")
        .group("toQuantity - from Decimal")
        .testTrue("3.14.toQuantity() = 3.14 '1'", "Decimal → Quantity with default unit")
        .group("toQuantity - from Boolean")
        .testTrue("true.toQuantity() = 1.0 '1'", "true → 1.0 '1'")
        .testTrue("false.toQuantity() = 0.0 '1'", "false → 0.0 '1'")
        .group("toQuantity - empty propagation")
        .testEmpty("{}.toQuantity()", "Empty → empty")
        .build();
  }

  // ========== toQuantity(unit) ==========

  @TestFactory
  Stream<DynamicTest> testToQuantityWithUnit() {
    return builder()
        .group("toQuantity(unit) - UCUM conversion")
        .testTrue("1000 'g'.toQuantity('kg') = 1 'kg'", "Grams to kilograms")
        .testTrue("1 'kg'.toQuantity('g') = 1000 'g'", "Kilograms to grams")
        .testTrue("100 'cm'.toQuantity('m') = 1 'm'", "Centimeters to meters")
        .group("toQuantity(unit) - same unit identity")
        .testTrue("10 'mg'.toQuantity('mg') = 10 'mg'", "Same unit returns unchanged")
        .group("toQuantity(unit) - incompatible units")
        .testEmpty("10 'kg'.toQuantity('m')", "Mass → length: incompatible → empty")
        .group("toQuantity(unit) - calendar duration conversion")
        .testTrue("1 year.toQuantity('month') = 12 month", "Year to months")
        .testTrue("1 day.toQuantity('hour') = 24 hour", "Day to hours")
        .testTrue("60 minute.toQuantity('hour') = 1 hour", "Minutes to hours")
        .testTrue("1 hour.toQuantity('minute') = 60 minute", "Hours to minutes")
        .group("toQuantity(unit) - string with plural calendar unit")
        .testTrue(
            "'10 years'.toQuantity('month') = 120 month",
            "Plural 'years' normalized to singular before conversion")
        .group("toQuantity(unit) - from non-Quantity input with unit")
        .testEmpty(
            "1000.toQuantity('g')",
            "Integer → Quantity('1') then convert '1' to 'g' → incompatible → empty")
        .group("toQuantity(unit) - empty propagation")
        .testEmpty("{}.toQuantity('kg')", "Empty → empty")
        .build();
  }

  // ========== convertsToBoolean() ==========

  @TestFactory
  Stream<DynamicTest> testConvertsToBoolean() {
    return builder()
        .group("convertsToBoolean - identity")
        .testTrue("true.convertsToBoolean()")
        .testTrue("false.convertsToBoolean()")
        .group("convertsToBoolean - from Integer")
        .testTrue("1.convertsToBoolean()", "1 converts to boolean")
        .testTrue("0.convertsToBoolean()", "0 converts to boolean")
        .testFalse("2.convertsToBoolean()", "2 does not convert")
        .group("convertsToBoolean - from Decimal")
        .testTrue("1.0.convertsToBoolean()", "1.0 converts")
        .testTrue("0.0.convertsToBoolean()", "0.0 converts")
        .testFalse("2.5.convertsToBoolean()", "2.5 does not convert")
        .group("convertsToBoolean - from String")
        .testTrue("'true'.convertsToBoolean()")
        .testTrue("'false'.convertsToBoolean()")
        .testTrue("'1.0'.convertsToBoolean()")
        .testTrue("'yes'.convertsToBoolean()")
        .testTrue("'no'.convertsToBoolean()")
        .testTrue("'YES'.convertsToBoolean()", "Case insensitive")
        .testFalse("'abc'.convertsToBoolean()")
        .group("convertsToBoolean - non-convertible types")
        .testFalse("@2023-06-15.convertsToBoolean()", "Date → false")
        .group("convertsToBoolean - empty propagation")
        .testEmpty("{}.convertsToBoolean()", "Empty → empty")
        .build();
  }

  // ========== convertsToInteger() ==========

  @TestFactory
  Stream<DynamicTest> testConvertsToInteger() {
    return builder()
        .group("convertsToInteger - identity")
        .testTrue("42.convertsToInteger()")
        .group("convertsToInteger - from Boolean")
        .testTrue("true.convertsToInteger()")
        .group("convertsToInteger - from String")
        .testTrue("'123'.convertsToInteger()")
        .testFalse("'3.14'.convertsToInteger()", "Decimal string → false")
        .testFalse("'abc'.convertsToInteger()", "Non-numeric → false")
        .group("convertsToInteger - non-convertible types")
        .testFalse("1.5.convertsToInteger()", "Decimal → false")
        .group("convertsToInteger - empty propagation")
        .testEmpty("{}.convertsToInteger()", "Empty → empty")
        .build();
  }

  // ========== convertsToDecimal() ==========

  @TestFactory
  Stream<DynamicTest> testConvertsToDecimal() {
    return builder()
        .group("convertsToDecimal - identity")
        .testTrue("3.14.convertsToDecimal()")
        .group("convertsToDecimal - from Integer")
        .testTrue("42.convertsToDecimal()")
        .group("convertsToDecimal - from Boolean")
        .testTrue("true.convertsToDecimal()")
        .group("convertsToDecimal - from String")
        .testTrue("'3.14'.convertsToDecimal()")
        .testTrue("'42'.convertsToDecimal()")
        .testFalse("'abc'.convertsToDecimal()")
        .group("convertsToDecimal - empty propagation")
        .testEmpty("{}.convertsToDecimal()", "Empty → empty")
        .build();
  }

  // ========== convertsToString() ==========

  @TestFactory
  Stream<DynamicTest> testConvertsToString() {
    return builder()
        .group("convertsToString - all types convert")
        .testTrue("'hello'.convertsToString()")
        .testTrue("42.convertsToString()")
        .testTrue("3.14.convertsToString()")
        .testTrue("true.convertsToString()")
        .testTrue("@2023-06-15.convertsToString()")
        .testTrue("@T14:30:00.convertsToString()")
        .group("convertsToString - empty propagation")
        .testEmpty("{}.convertsToString()", "Empty → empty")
        .build();
  }

  // ========== convertsToDate() ==========

  @TestFactory
  Stream<DynamicTest> testConvertsToDate() {
    return builder()
        .group("convertsToDate - identity")
        .testTrue("@2023-06-15.convertsToDate()")
        .group("convertsToDate - from DateTime")
        .testTrue("@2023-06-15T10:30:00.convertsToDate()")
        .group("convertsToDate - from String")
        .testTrue("'2023-06-15'.convertsToDate()")
        .testTrue("'2023-06'.convertsToDate()", "Partial date")
        .testFalse("'not-a-date'.convertsToDate()")
        .group("convertsToDate - non-convertible")
        .testFalse("42.convertsToDate()")
        .group("convertsToDate - empty propagation")
        .testEmpty("{}.convertsToDate()", "Empty → empty")
        .build();
  }

  // ========== convertsToDateTime() ==========

  @TestFactory
  Stream<DynamicTest> testConvertsToDateTime() {
    return builder()
        .group("convertsToDateTime - identity")
        .testTrue("@2023-06-15T10:30:00.convertsToDateTime()")
        .group("convertsToDateTime - from Date")
        .testTrue("@2023-06-15.convertsToDateTime()")
        .group("convertsToDateTime - from String")
        .testTrue("'2023-06-15T10:30:00'.convertsToDateTime()")
        .testTrue("'2023'.convertsToDateTime()", "Partial datetime")
        .testFalse("'not-a-datetime'.convertsToDateTime()")
        .group("convertsToDateTime - non-convertible")
        .testFalse("42.convertsToDateTime()")
        .group("convertsToDateTime - empty propagation")
        .testEmpty("{}.convertsToDateTime()", "Empty → empty")
        .build();
  }

  // ========== convertsToTime() ==========

  @TestFactory
  Stream<DynamicTest> testConvertsToTime() {
    return builder()
        .group("convertsToTime - identity")
        .testTrue("@T14:30:00.convertsToTime()")
        .group("convertsToTime - from String")
        .testTrue("'14:30:00'.convertsToTime()")
        .testTrue("'14:30'.convertsToTime()", "Partial time")
        .testFalse("'not-a-time'.convertsToTime()")
        .group("convertsToTime - non-convertible")
        .testFalse("42.convertsToTime()")
        .group("convertsToTime - empty propagation")
        .testEmpty("{}.convertsToTime()", "Empty → empty")
        .build();
  }

  // ========== convertsToQuantity() ==========

  @TestFactory
  Stream<DynamicTest> testConvertsToQuantity() {
    return builder()
        .group("convertsToQuantity - identity")
        .testTrue("10 'mg'.convertsToQuantity()")
        .group("convertsToQuantity - from numeric types")
        .testTrue("42.convertsToQuantity()")
        .testTrue("3.14.convertsToQuantity()")
        .testTrue("true.convertsToQuantity()")
        .group("convertsToQuantity - from String")
        .testTrue("'10'.convertsToQuantity()", "Numeric string")
        .testFalse("'abc'.convertsToQuantity()", "Non-numeric string")
        .group("convertsToQuantity - non-convertible")
        .testFalse("@2023-06-15.convertsToQuantity()")
        .group("convertsToQuantity - empty propagation")
        .testEmpty("{}.convertsToQuantity()", "Empty → empty")
        .build();
  }

  // ========== convertsToQuantity(unit) ==========

  @TestFactory
  Stream<DynamicTest> testConvertsToQuantityWithUnit() {
    return builder()
        .group("convertsToQuantity(unit) - compatible UCUM units")
        .testTrue("1000 'g'.convertsToQuantity('kg')", "Grams to kilograms")
        .testTrue("100 'cm'.convertsToQuantity('m')", "Centimeters to meters")
        .group("convertsToQuantity(unit) - same unit")
        .testTrue("10 'mg'.convertsToQuantity('mg')", "Same unit → true")
        .group("convertsToQuantity(unit) - incompatible units")
        .testFalse("10 'kg'.convertsToQuantity('m')", "Mass → length → false")
        .group("convertsToQuantity(unit) - calendar duration")
        .testTrue("1 year.convertsToQuantity('month')", "Year to months")
        .testTrue("1 day.convertsToQuantity('hour')", "Day to hours")
        .group("convertsToQuantity(unit) - non-convertible input type")
        .testFalse("@2023-06-15.convertsToQuantity('kg')", "Date → false")
        .group("convertsToQuantity(unit) - non-quantity input with unit")
        .testFalse("42.convertsToQuantity('kg')", "Integer → '1' → incompatible with 'kg'")
        .group("convertsToQuantity(unit) - empty propagation")
        .testEmpty("{}.convertsToQuantity('kg')", "Empty → empty")
        .build();
  }

  // ========== Implicit Type Coercions (FHIRPath Spec 6.2) ==========

  @TestFactory
  Stream<DynamicTest> testImplicitCoercions() {
    return builder()
        .group("Implicit coercion: Integer → Decimal")
        .testEquals(15.5, "5 + 10.5", "Integer + Decimal promotes to Decimal")
        .testTrue("5 = 5.0", "Integer = Decimal comparison")
        .group("Implicit coercion: Integer → Quantity")
        .testTrue("5 + 10 '1' = 15 '1'", "Integer + Quantity (same unit '1')")
        .group("Implicit coercion: Decimal → Quantity")
        .testTrue("5.0 + 10 '1' = 15.0 '1'", "Decimal + Quantity (same unit '1')")
        .group("Implicit coercion: Date → DateTime")
        .testTrue(
            "'2023-06-15'.toDate() = '2023-06-15'.toDateTime()",
            "Date promoted to DateTime for comparison")
        .build();
  }
}
