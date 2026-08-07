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
 * Tests for FHIRPath Quantity literal expressions.
 *
 * <p>Based on FHIRPath specification section 2.3 (Literals): Quantity literals consist of a number
 * followed by an optional unit (UCUM or calendar duration keyword).
 *
 * <p>Covers:
 *
 * <ul>
 *   <li>UCUM unit quantities (e.g. {@code 10 'mg'})
 *   <li>Calendar duration keywords (singular and plural)
 *   <li>Decimal value quantities
 * </ul>
 */
public class QuantityLiteralsTest extends FhirPathTestBase {

  @TestFactory
  Stream<DynamicTest> testUcumQuantities() {
    return builder()
        .group("UCUM unit quantities")
        .testTrue("10 'mg' = 10 'mg'", "Integer UCUM quantity")
        .testTrue("10.5 'kg' = 10.5 'kg'", "Decimal UCUM quantity")
        .testTrue("100 '[degF]' = 100 '[degF]'", "Special UCUM unit")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testCalendarDurationsSingular() {
    return builder()
        .group("Calendar duration keywords (singular)")
        .testTrue("1 year = 1 year", "year")
        .testTrue("1 month = 1 month", "month")
        .testTrue("1 week = 1 week", "week")
        .testTrue("1 day = 1 day", "day")
        .testTrue("1 hour = 1 hour", "hour")
        .testTrue("1 minute = 1 minute", "minute")
        .testTrue("1 second = 1 second", "second")
        .testTrue("1 millisecond = 1 millisecond", "millisecond")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testCalendarDurationsPlural() {
    return builder()
        .group("Calendar duration keywords (plural)")
        .testTrue("4 days = 4 days", "plural days")
        .testTrue("2 years = 2 years", "plural years")
        .testTrue("3 hours = 3 hours", "plural hours")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testPluralNormalizesToSingularCode() {
    return builder()
        .group("Plural normalizes to singular code")
        .testTrue("4 days = 4 day", "days == day (same code)")
        .testTrue("2 years = 2 year", "years == year (same code)")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testDecimalValueQuantities() {
    return builder()
        .group("Decimal values")
        .testTrue("4.5 'mg' = 4.5 'mg'", "Decimal UCUM quantity")
        .testTrue("0.5 day = 0.5 day", "Decimal calendar duration")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testEdgeCases() {
    return builder()
        .group("Edge cases")
        .testTrue("0 'mg' = 0 'mg'", "Zero value quantity")
        .testTrue("0 day = 0 day", "Zero value calendar duration")
        .build();
  }
}
