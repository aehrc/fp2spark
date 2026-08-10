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
package au.csiro.fhirpath.spark.udf;

import au.csiro.fhirpath.spark.SparkTypeMapper;
import au.csiro.fhirpath.typing.QuantityValue;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Map;
import java.util.Set;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.RowFactory;
import org.apache.spark.sql.api.java.UDF2;
import org.apache.spark.sql.expressions.UserDefinedFunction;
import org.apache.spark.sql.functions;

/**
 * Spark UDF that converts a FHIR Quantity to a specified target unit.
 *
 * <p>Modeled after Pathling's {@code ConvertQuantityToUnit}. Handles:
 *
 * <ul>
 *   <li>UCUM → UCUM conversion via {@link UcumService}
 *   <li>Calendar → Calendar conversion using FHIRPath spec conversion factors
 *   <li>Cross-system conversion where possible (second ↔ 's', millisecond ↔ 'ms')
 * </ul>
 *
 * <p>Returns {@code null} if conversion is not possible (incompatible units/dimensions).
 */
public final class QuantityConvertToUnit {

  private QuantityConvertToUnit() {}

  /**
   * Calendar duration conversion factors in milliseconds, per FHIRPath spec.
   *
   * <p>Year and month use spec-defined day approximations (365 and 30 days respectively) for
   * ms-based conversion. However, year↔month uses a direct factor of 12, and week↔year/month are
   * incompatible (per Pathling's approach).
   */
  static final Map<String, BigDecimal> CALENDAR_FACTORS_MS =
      Map.of(
          "year", BigDecimal.valueOf(365L * 24 * 60 * 60 * 1000),
          "month", BigDecimal.valueOf(30L * 24 * 60 * 60 * 1000),
          "week", BigDecimal.valueOf(7L * 24 * 60 * 60 * 1000),
          "day", BigDecimal.valueOf(24L * 60 * 60 * 1000),
          "hour", BigDecimal.valueOf(60L * 60 * 1000),
          "minute", BigDecimal.valueOf(60L * 1000),
          "second", BigDecimal.valueOf(1000),
          "millisecond", BigDecimal.ONE);

  private static final BigDecimal MONTHS_IN_YEAR = new BigDecimal(12);
  private static final BigDecimal MILLIS_PER_SECOND = BigDecimal.valueOf(1000);

  /**
   * Incompatible calendar conversions: week has no clean relationship to year or month (a month
   * isn't a whole number of weeks).
   */
  private static final Set<String> INCOMPATIBLE_CALENDAR =
      Set.of("week:year", "year:week", "week:month", "month:week");

  /** Spark UDF: (quantity_struct, target_unit_string) → converted_quantity_struct or null. */
  @Nonnull
  public static final UserDefinedFunction UDF =
      functions.udf(
          (UDF2<Row, String, Row>) QuantityConvertToUnit::convert, SparkTypeMapper.QUANTITY_TYPE);

  @Nullable
  static Row convert(@Nullable final Row quantityRow, @Nullable final String targetUnit) {
    if (quantityRow == null || targetUnit == null) {
      return null;
    }

    final BigDecimal value = quantityRow.getDecimal(SparkTypeMapper.Q_VALUE);
    final String system = quantityRow.getString(SparkTypeMapper.Q_SYSTEM);
    final String code = quantityRow.getString(SparkTypeMapper.Q_CODE);

    if (value == null || system == null || code == null) {
      return null;
    }

    // Normalize target unit: resolve plural calendar keywords to canonical singular form
    final String normalizedTarget = QuantityValue.canonicalCalendarCode(targetUnit);

    // Exact match — no conversion needed
    if (code.equals(normalizedTarget)) {
      return buildQuantityRow(value, targetUnit, system, normalizedTarget);
    }

    // Determine the target unit's system
    final boolean targetIsCalendar = QuantityValue.isCalendarKeyword(normalizedTarget);

    final Row result =
        switch (system) {
          case QuantityValue.UCUM_SYSTEM ->
              targetIsCalendar
                  ? convertUcumToCalendar(value, code, normalizedTarget)
                  : convertUcumToUcum(value, code, normalizedTarget);
          case QuantityValue.CALENDAR_SYSTEM ->
              targetIsCalendar
                  ? convertCalendarToCalendar(value, code, normalizedTarget)
                  : convertCalendarToUcum(value, code, normalizedTarget);
          default -> null;
        };

    // Replace the canonical display unit with the original target unit string
    if (result != null && !targetUnit.equals(normalizedTarget)) {
      return buildQuantityRow(
          result.getDecimal(SparkTypeMapper.Q_VALUE),
          targetUnit,
          result.getString(SparkTypeMapper.Q_SYSTEM),
          result.getString(SparkTypeMapper.Q_CODE));
    }
    return result;
  }

  /** UCUM → UCUM conversion via UcumService. */
  @Nullable
  private static Row convertUcumToUcum(
      @Nonnull final BigDecimal value,
      @Nonnull final String fromCode,
      @Nonnull final String toCode) {
    final BigDecimal converted = UcumService.convertValue(value, fromCode, toCode);
    return converted != null
        ? buildQuantityRow(converted, toCode, QuantityValue.UCUM_SYSTEM)
        : null;
  }

  /** Calendar → Calendar conversion using spec conversion factors. */
  @Nullable
  private static Row convertCalendarToCalendar(
      @Nonnull final BigDecimal value,
      @Nonnull final String fromCode,
      @Nonnull final String toCode) {
    // Check for incompatible conversions (week ↔ year/month)
    if (INCOMPATIBLE_CALENDAR.contains(fromCode + ":" + toCode)) {
      return null;
    }

    // Special case: year ↔ month uses exact factor of 12
    if ("year".equals(fromCode) && "month".equals(toCode)) {
      return buildQuantityRow(
          value.multiply(MONTHS_IN_YEAR), toCode, QuantityValue.CALENDAR_SYSTEM);
    }
    if ("month".equals(fromCode) && "year".equals(toCode)) {
      return buildQuantityRow(
          value.divide(MONTHS_IN_YEAR, MathContext.DECIMAL128),
          toCode,
          QuantityValue.CALENDAR_SYSTEM);
    }

    // Default: millisecond-based conversion for compatible units
    final BigDecimal fromFactor = CALENDAR_FACTORS_MS.get(fromCode);
    final BigDecimal toFactor = CALENDAR_FACTORS_MS.get(toCode);
    if (fromFactor == null || toFactor == null) {
      return null;
    }
    final BigDecimal converted =
        value.multiply(fromFactor).divide(toFactor, MathContext.DECIMAL128);
    return buildQuantityRow(converted, toCode, QuantityValue.CALENDAR_SYSTEM);
  }

  /**
   * Calendar → UCUM conversion. Bridges via {@link UcumService#toUcumCode}, which only maps {@code
   * second} → {@code 's'} and {@code millisecond} → {@code 'ms'}. All other calendar durations
   * (year, month, week, day, hour, minute) return {@code null} because they have no exact UCUM
   * equivalent. Once bridged, delegates to {@link #convertUcumToUcum}.
   */
  @Nullable
  private static Row convertCalendarToUcum(
      @Nonnull final BigDecimal value,
      @Nonnull final String calendarCode,
      @Nonnull final String ucumTarget) {
    final String ucumCode = UcumService.toUcumCode(QuantityValue.CALENDAR_SYSTEM, calendarCode);
    if (ucumCode == null) {
      return null;
    }
    return convertUcumToUcum(value, ucumCode, ucumTarget);
  }

  /**
   * UCUM → Calendar conversion. Only possible if the UCUM unit can be converted to a definite
   * calendar duration bridge ('s' or 'ms').
   */
  @Nullable
  private static Row convertUcumToCalendar(
      @Nonnull final BigDecimal value,
      @Nonnull final String ucumCode,
      @Nonnull final String calendarTarget) {
    // Find the UCUM bridge for the target calendar unit
    // Only 'second' and 'millisecond' have direct UCUM equivalents
    final BigDecimal targetFactorMs = CALENDAR_FACTORS_MS.get(calendarTarget);
    if (targetFactorMs == null) {
      return null;
    }

    // Try converting UCUM to 's' (seconds) as the bridge unit
    final BigDecimal valueInSeconds = UcumService.convertValue(value, ucumCode, "s");
    if (valueInSeconds == null) {
      return null;
    }

    // Convert seconds to target calendar unit via milliseconds
    final BigDecimal valueInMs = valueInSeconds.multiply(MILLIS_PER_SECOND);
    final BigDecimal converted = valueInMs.divide(targetFactorMs, MathContext.DECIMAL128);
    return buildQuantityRow(converted, calendarTarget, QuantityValue.CALENDAR_SYSTEM);
  }

  @Nonnull
  private static Row buildQuantityRow(
      @Nonnull final BigDecimal value,
      @Nonnull final String unitCode,
      @Nonnull final String system) {
    return RowFactory.create(value, unitCode, system, unitCode);
  }

  @Nonnull
  private static Row buildQuantityRow(
      @Nonnull final BigDecimal value,
      @Nonnull final String displayUnit,
      @Nonnull final String system,
      @Nonnull final String code) {
    return RowFactory.create(value, displayUnit, system, code);
  }
}
