package com.example.fhirpath.codegen.spark.ops;

import static org.apache.spark.sql.functions.coalesce;
import static org.apache.spark.sql.functions.lit;
import static org.apache.spark.sql.functions.when;

import com.example.fhirpath.codegen.spark.udf.QuantityCanonicalize;
import com.example.fhirpath.typing.QuantityValue;
import jakarta.annotation.Nonnull;
import java.math.BigDecimal;
import java.util.function.BinaryOperator;
import org.apache.spark.sql.Column;

/**
 * Quantity comparison helpers with UCUM-aware unit conversion.
 *
 * <p>Uses a three-tier comparison strategy (modeled after Pathling's {@code QuantityComparator},
 * with an additional calendar-duration tier for year↔month per FHIRPath spec §3.3):
 *
 * <ol>
 *   <li><b>Tier 1 — Canonical comparison:</b> Both operands are canonicalized to UCUM base units
 *       via {@link QuantityCanonicalize}. If both canonical codes match, canonical values are
 *       compared. This handles cross-unit comparison (e.g., {@code cm} vs {@code m}).
 *   <li><b>Tier 2 — Year↔month comparison:</b> When both operands are calendar durations with codes
 *       in {@code {year, month}}, both values are normalized to months (multiplying year values by
 *       12) and compared. This implements the spec-defined exact calendar conversion factor {@code
 *       1 year = 12 months}. Other non-definite calendar pairs (day, week, hour, minute) are NOT
 *       handled here — they lack an exact spec-defined conversion.
 *   <li><b>Tier 3 — Direct comparison:</b> Falls back to same system+code exact match. This
 *       preserves behavior for non-UCUM quantities or when canonicalization fails.
 * </ol>
 *
 * <p>Returns {@code null} (empty collection) when units are incompatible (different dimensions) or
 * when no tier produces a defined comparison.
 */
final class QuantitySupport {

  private static final BigDecimal MONTHS_IN_YEAR = new BigDecimal(12);
  private static final String YEAR_CODE = "year";
  private static final String MONTH_CODE = "month";

  private QuantitySupport() {}

  @Nonnull
  private static Column sameUnit(@Nonnull final Column left, @Nonnull final Column right) {
    return left.getField("system")
        .equalTo(right.getField("system"))
        .and(left.getField("code").equalTo(right.getField("code")));
  }

  /**
   * Checks whether both operands are calendar-duration quantities with codes in {year, month}.
   *
   * <p>Such pairs can be compared by normalizing both to months using the spec-exact factor {@code
   * 1 year = 12 months} (FHIRPath §3.3).
   */
  @Nonnull
  private static Column bothYearOrMonth(@Nonnull final Column left, @Nonnull final Column right) {
    return isYearOrMonth(left).and(isYearOrMonth(right));
  }

  @Nonnull
  private static Column isYearOrMonth(@Nonnull final Column operand) {
    return operand
        .getField("system")
        .equalTo(lit(QuantityValue.CALENDAR_SYSTEM))
        .and(operand.getField("code").isin(YEAR_CODE, MONTH_CODE));
  }

  /**
   * Returns the operand's value expressed in months, given that the operand is a calendar-duration
   * quantity with code in {year, month}. Year values are multiplied by 12; month values pass
   * through.
   */
  @Nonnull
  private static Column valueInMonths(@Nonnull final Column operand) {
    return when(
            operand.getField("code").equalTo(lit(YEAR_CODE)),
            operand.getField("value").multiply(lit(MONTHS_IN_YEAR)))
        .otherwise(operand.getField("value"));
  }

  @Nonnull
  static Column quantityEquals(@Nonnull final Column left, @Nonnull final Column right) {
    return quantityComparator(Column::equalTo).apply(left, right);
  }

  @Nonnull
  static BinaryOperator<Column> quantityComparator(
      @Nonnull final BinaryOperator<Column> comparator) {
    return (left, right) -> {
      // Tier 1: Canonicalize both, compare when canonical codes match
      final Column canonLeft = QuantityCanonicalize.UDF.apply(left);
      final Column canonRight = QuantityCanonicalize.UDF.apply(right);
      final Column canonCompare =
          when(
              canonLeft.getField("code").equalTo(canonRight.getField("code")),
              comparator.apply(canonLeft.getField("value"), canonRight.getField("value")));

      // Tier 2: Year↔month calendar-duration comparison (spec: 1 year = 12 months)
      final Column yearMonthCompare =
          when(
              bothYearOrMonth(left, right),
              comparator.apply(valueInMonths(left), valueInMonths(right)));

      // Tier 3: Fall back to same system+code direct comparison
      final Column directCompare =
          when(
              sameUnit(left, right),
              comparator.apply(left.getField("value"), right.getField("value")));

      return coalesce(canonCompare, yearMonthCompare, directCompare);
    };
  }
}
