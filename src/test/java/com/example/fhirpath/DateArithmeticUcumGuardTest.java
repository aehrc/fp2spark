package com.example.fhirpath;

import com.example.fhirpath.test.FhirPathTestBase;
import java.util.stream.Stream;
import org.apache.spark.SparkException;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Tests for the runtime guard that rejects UCUM definite-duration codes above the second boundary
 * as right-hand operands of Date/DateTime/Time {@code +} and {@code -}.
 *
 * <p>Per FHIRPath spec §9, only calendar-duration keywords ({@code year}, {@code month}, {@code
 * week}, {@code day}, {@code hour}, {@code minute}) and UCUM codes at or below the second boundary
 * ({@code 's'}, {@code 'ms'}) are valid. UCUM codes above seconds ({@code 'a'}, {@code 'mo'},
 * {@code 'wk'}, {@code 'd'}, {@code 'h'}, {@code 'min'}) must raise a runtime error because their
 * calendar meaning is ambiguous (e.g. UCUM {@code 'a'} = 365.25 days ≠ calendar year).
 *
 * <p>The guard is emitted inside the {@code TemporalArithmetic} UDF so it fires per-row. Both
 * literal and non-literal Quantity right-hand operands are covered. Empty/null Quantities propagate
 * to empty per spec §1.5 rather than erroring.
 *
 * <p>Because the guard throws from inside a Spark UDF, the exception observed at query-execution
 * time is a {@link SparkException} whose cause chain contains the originating {@code
 * IllegalArgumentException}.
 */
public class DateArithmeticUcumGuardTest extends FhirPathTestBase {

  // ===== Forbidden UCUM codes with literal RHS (the six cases from 6.6_math.yaml) =====

  @TestFactory
  Stream<DynamicTest> testForbiddenUcumCodesLiteralRhs() {
    return builder()
        .group("Forbidden UCUM duration codes above second boundary — literal RHS")
        .testError(SparkException.class, "@2018 + 1 'a'", "UCUM year 'a' on Date")
        .testError(SparkException.class, "@2018-02 + 1 'mo'", "UCUM month 'mo' on Date")
        .testError(SparkException.class, "@2016-01 + 29 'd'", "UCUM day 'd' on Date (29d)")
        .testError(SparkException.class, "@2016-01 + 30 'd'", "UCUM day 'd' on Date (30d)")
        .testError(SparkException.class, "@2016-02-28 + 1 'wk'", "UCUM week 'wk' on Date")
        .testError(
            SparkException.class, "@2016-02-28 + 1.5 'wk'", "UCUM week 'wk' on Date (decimal)")
        .group("Forbidden UCUM codes: hour 'h' and minute 'min' on DateTime/Time")
        .testError(
            SparkException.class, "@2014-01-25T14:00:00 + 2 'h'", "UCUM hour 'h' on DateTime")
        .testError(SparkException.class, "@T14:30:00 + 30 'min'", "UCUM minute 'min' on Time")
        .group("Forbidden UCUM codes with subtraction")
        .testError(SparkException.class, "@2018 - 1 'a'", "UCUM year 'a' on Date (subtraction)")
        .testError(
            SparkException.class, "@2016-02-28 - 1 'wk'", "UCUM week 'wk' on Date (subtraction)")
        .build();
  }

  // ===== Non-literal RHS — Quantity comes from a resource field =====

  @TestFactory
  Stream<DynamicTest> testForbiddenUcumCodesNonLiteralRhs() {
    return builder()
        .withSubject(
            "Observation",
            sb ->
                sb.quantity("dayDuration", "3 'd'")
                    .quantity("yearDuration", "1 'a'")
                    .quantity("weekDuration", "2 'wk'"))
        .group("Forbidden UCUM codes — Quantity derived from resource field")
        .testError(
            SparkException.class,
            "@2018-01-01 + dayDuration",
            "UCUM day 'd' from resource Quantity field")
        .testError(
            SparkException.class,
            "@2018 + yearDuration",
            "UCUM year 'a' from resource Quantity field")
        .testError(
            SparkException.class,
            "@2018-02-15 + weekDuration",
            "UCUM week 'wk' from resource Quantity field")
        .build();
  }

  // ===== Allowed units — sanity checks that the guard doesn't over-fire =====

  @TestFactory
  Stream<DynamicTest> testAllowedUnitsStillWork() {
    return builder()
        .group("Calendar-duration keywords — unaffected by guard")
        .testTrue("@2018 + 1 year = @2019", "Calendar 'year' still works on Date")
        .testTrue("@2018-02 + 1 month = @2018-03", "Calendar 'month' still works")
        .testTrue("@2016-01 + 30 days = @2016-02", "Calendar 'days' still works (30d → 1 month)")
        .testTrue("@2016-02-28 + 1 week = @2016-03-06", "Calendar 'week' still works")
        .group("UCUM seconds and milliseconds — below second boundary, allowed")
        .testTrue(
            "@2014-01-25T14:00:00 + 60 's' = @2014-01-25T14:01:00",
            "UCUM seconds 's' valid in date arithmetic (spec §5.3)")
        .testTrue("@T14:30:00 + 30 's' = @T14:30:30", "UCUM seconds 's' valid on Time (spec §5.3)")
        .build();
  }

  // ===== Empty propagation — empty Quantity should NOT trigger the guard =====

  @TestFactory
  Stream<DynamicTest> testEmptyQuantityPropagatesNotErrors() {
    return builder()
        .withSubject("Observation", sb -> sb.quantityEmpty("missingDuration"))
        .group("Empty Quantity RHS — propagates to empty, no error")
        .testEmpty(
            "@2018 + missingDuration",
            "Empty Quantity on RHS propagates to empty (does not fire guard)")
        .testEmpty(
            "@2018-01-01 - missingDuration",
            "Empty Quantity on RHS of subtraction propagates to empty")
        .testEmpty("@2018 + {}", "Literal {} RHS propagates to empty")
        .build();
  }
}
