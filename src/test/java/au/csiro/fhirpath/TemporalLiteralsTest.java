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
 * Tests for FHIRPath temporal literal expressions.
 *
 * <p>Based on FHIRPath specification section 2.3 (Literals): Date, DateTime, and Time literals use
 * a subset of ISO 8601 prefixed with {@code @}.
 *
 * <p>Covers:
 *
 * <ul>
 *   <li>Date literals at various precisions (year, year-month, full date)
 *   <li>DateTime literals with time components and timezone offsets
 *   <li>Time literals at various precisions
 * </ul>
 */
public class TemporalLiteralsTest extends FhirPathTestBase {

  @TestFactory
  Stream<DynamicTest> testDateLiterals() {
    return builder()
        .group("Date literals")
        .testEquals("2014", "@2014", "Year precision")
        .testEquals("2014-01", "@2014-01", "Year-month precision")
        .testEquals("2014-01-25", "@2014-01-25", "Full date")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testDateTimeLiterals() {
    return builder()
        .group("DateTime literals")
        .testEquals("2014-01-25T14:30:14.559", "@2014-01-25T14:30:14.559", "Full DateTime")
        .testEquals(
            "2014-01-25T14:30:14.559Z", "@2014-01-25T14:30:14.559Z", "DateTime with UTC timezone")
        .testEquals("2014-01-25T14:30", "@2014-01-25T14:30", "Hour-minute precision DateTime")
        .testEquals("2014T", "@2014T", "Year precision DateTime")
        .testEquals("2014-01T", "@2014-01T", "Year-month precision DateTime")
        .testEquals("2014-01-25T", "@2014-01-25T", "Date precision DateTime")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testTimeLiterals() {
    return builder()
        .group("Time literals")
        .testEquals("12:00", "@T12:00", "Hour-minute precision")
        .testEquals("14:30:14.559", "@T14:30:14.559", "Full time with milliseconds")
        .build();
  }
}
