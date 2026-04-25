package com.example.fhirpath.codegen.spark.udf;

import com.example.fhirpath.codegen.spark.SparkTypeMapper;
import com.example.fhirpath.typing.QuantityValue;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Year;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.api.java.UDF3;
import org.apache.spark.sql.expressions.UserDefinedFunction;
import org.apache.spark.sql.functions;
import org.apache.spark.sql.types.DataTypes;

/**
 * Spark UDF for temporal arithmetic: Date/DateTime/Time +/- Quantity.
 *
 * <p>Implements FHIRPath date/time arithmetic per the specification:
 *
 * <ul>
 *   <li>Calendar-based arithmetic for units above seconds (year, month, week, day, hour, minute)
 *   <li>Definite-duration arithmetic for seconds and milliseconds
 *   <li>Partial date/time precision handling: when the quantity is more precise than the temporal
 *       value, the quantity is converted to the temporal's precision using standard conversion
 *       factors
 *   <li>Weeks are multiplied by 7 and treated as days
 *   <li>Decimal portions are truncated for units above seconds
 * </ul>
 *
 * <p>Returns {@code null} (empty collection) when either argument is null, the quantity unit is not
 * a valid time-valued duration, or the unit is not applicable to the temporal type. The FHIRPath
 * spec says invalid units should "signal an error", but this implementation returns null per the
 * project's Spark-NULL-as-empty convention for UDFs.
 */
public final class TemporalArithmetic {

  private TemporalArithmetic() {}

  /** Operation codes passed as the third argument to the UDF. */
  public static final String OP_ADD = "add";

  public static final String OP_SUB = "sub";

  /** The UDF instance: (String, Row, String) → String. */
  @Nonnull
  public static final UserDefinedFunction UDF =
      functions.udf(
          (UDF3<String, Row, String, String>) TemporalArithmetic::compute, DataTypes.StringType);

  /**
   * Precision levels for temporal values, ordered from coarsest to finest. Used to determine when a
   * quantity unit is more precise than a temporal value's precision and needs conversion.
   */
  enum Precision {
    YEAR,
    MONTH,
    DAY,
    HOUR,
    MINUTE,
    SECOND,
    MILLISECOND
  }

  /**
   * Maps calendar duration codes to their precision level. Includes both calendar keywords and UCUM
   * time units.
   */
  private static final Map<String, Precision> UNIT_PRECISION =
      Map.ofEntries(
          // Calendar duration codes
          Map.entry("year", Precision.YEAR),
          Map.entry("month", Precision.MONTH),
          Map.entry("week", Precision.DAY), // weeks convert to days
          Map.entry("day", Precision.DAY),
          Map.entry("hour", Precision.HOUR),
          Map.entry("minute", Precision.MINUTE),
          Map.entry("second", Precision.SECOND),
          Map.entry("millisecond", Precision.MILLISECOND),
          // UCUM time codes
          Map.entry("a", Precision.YEAR),
          Map.entry("mo", Precision.MONTH),
          Map.entry("wk", Precision.DAY),
          Map.entry("d", Precision.DAY),
          Map.entry("h", Precision.HOUR),
          Map.entry("min", Precision.MINUTE),
          Map.entry("s", Precision.SECOND),
          Map.entry("ms", Precision.MILLISECOND));

  /**
   * UCUM duration codes forbidden as right-hand operands of Date/DateTime/Time {@code +} and {@code
   * -}. Per FHIRPath spec §9, definite-duration quantities above the second boundary cannot be used
   * in date/time arithmetic because their calendar meaning is ambiguous (e.g. UCUM {@code 'a'} is
   * 365.25 days, which does not match the calendar year's 365 or 366 days). Only calendar-duration
   * keywords ({@code year}, {@code month}, {@code week}, {@code day}, {@code hour}, {@code minute})
   * and definite UCUM codes at or below the second boundary ({@code 's'}, {@code 'ms'}) are valid.
   *
   * <p>Units below the second boundary ({@code 's'}, {@code 'ms'}) are intentionally omitted — per
   * spec §5.3 they are definite-equal to calendar {@code second}/{@code millisecond} and ARE valid
   * in date arithmetic.
   */
  private static final Set<String> FORBIDDEN_UCUM_DURATION_CODES =
      Set.of("a", "mo", "wk", "d", "h", "min");

  /**
   * Standard conversion factors for converting between precision levels. Used when a quantity is
   * more precise than the temporal value's precision. Per the FHIRPath spec: 1 year = 12 months =
   * 365 days, 1 month = 30 days, 1 week = 7 days, 1 day = 24 hours, 1 hour = 60 minutes, 1 minute =
   * 60 seconds, 1 second = 1000 milliseconds.
   */
  private static final long MS_PER_SECOND = 1000;

  private static final long MS_PER_MINUTE = 60 * MS_PER_SECOND;
  private static final long MS_PER_HOUR = 60 * MS_PER_MINUTE;
  private static final long MS_PER_DAY = 24 * MS_PER_HOUR;
  private static final long DAYS_PER_MONTH = 30;
  private static final long DAYS_PER_YEAR = 365;

  // ===== Patterns for parsing temporal strings =====

  /** Matches year-only date (e.g. {@code 2014}). */
  private static final Pattern YEAR_ONLY = Pattern.compile("^(\\d{4})$");

  /** Matches year-month date (e.g. {@code 2014-01}). */
  private static final Pattern YEAR_MONTH = Pattern.compile("^(\\d{4})-(\\d{2})$");

  /** Matches full date (e.g. {@code 2014-01-25}). */
  private static final Pattern FULL_DATE = Pattern.compile("^(\\d{4}-\\d{2}-\\d{2})$");

  /** Matches year-only DateTime (e.g. {@code 2014T}). */
  private static final Pattern YEAR_DATETIME = Pattern.compile("^(\\d{4})T$");

  /** Matches year-month DateTime (e.g. {@code 2014-01T}). */
  private static final Pattern YEAR_MONTH_DATETIME = Pattern.compile("^(\\d{4}-\\d{2})T$");

  /** Matches date-only DateTime (e.g. {@code 2014-01-25T}). */
  private static final Pattern DATE_DATETIME = Pattern.compile("^(\\d{4}-\\d{2}-\\d{2})T$");

  /**
   * Matches DateTime with time but no offset (e.g. {@code 2014-01-25T14:30} or {@code
   * 2014-01-25T14:30:00.123}).
   */
  private static final Pattern DATETIME_WITH_TIME =
      Pattern.compile("^(\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}(?::\\d{2}(?:\\.\\d+)?)?)$");

  /**
   * Matches DateTime with timezone offset (e.g. {@code 2014-01-25T14:30:00Z}). Group 1: date-time
   * portion. Group 2: offset.
   */
  private static final Pattern DATETIME_WITH_OFFSET =
      Pattern.compile("^(\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}(?::\\d{2}(?:\\.\\d+)?)?)([Z+-].*)$");

  /** Matches time-only values (e.g. {@code 14:30} or {@code 14:30:00.123}). */
  private static final Pattern TIME_ONLY =
      Pattern.compile("^(\\d{2}:\\d{2}(?::\\d{2}(?:\\.\\d+)?)?)$");

  /** Flexible DateTime formatter for parsing (shared definition in {@link TemporalNormalize}). */
  private static final DateTimeFormatter FLEXIBLE_DATETIME =
      TemporalNormalize.flexibleDateTimeBuilder().toFormatter();

  /** Flexible Time formatter for parsing. */
  private static final DateTimeFormatter FLEXIBLE_TIME =
      new DateTimeFormatterBuilder()
          .appendPattern("HH:mm")
          .optionalStart()
          .appendLiteral(':')
          .appendValue(ChronoField.SECOND_OF_MINUTE, 2)
          .optionalStart()
          .appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true)
          .optionalEnd()
          .optionalEnd()
          .toFormatter();

  // ===== Output formatters =====

  private static final DateTimeFormatter DATETIME_MINUTE_FMT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

  private static final DateTimeFormatter DATETIME_SECOND_FMT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

  private static final DateTimeFormatter TIME_MINUTE_FMT = DateTimeFormatter.ofPattern("HH:mm");

  private static final DateTimeFormatter TIME_SECOND_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

  // ===== Pre-computed conversion factors from sub-day units to days =====

  private static final BigDecimal HOURS_PER_DAY = BigDecimal.valueOf(24);
  private static final BigDecimal MINUTES_PER_DAY = BigDecimal.valueOf(24L * 60);
  private static final BigDecimal SECONDS_PER_DAY = BigDecimal.valueOf(24L * 60 * 60);
  private static final BigDecimal MILLIS_PER_DAY = BigDecimal.valueOf(24L * 60 * 60 * 1000);

  @Nullable
  static String compute(
      @Nullable final String temporal, @Nullable final Row quantity, @Nullable final String op) {
    if (temporal == null || quantity == null || op == null) {
      return null;
    }

    try {
      final BigDecimal qValue = quantity.getDecimal(SparkTypeMapper.Q_VALUE);
      final String qSystem = quantity.getString(SparkTypeMapper.Q_SYSTEM);
      final String qCode = quantity.getString(SparkTypeMapper.Q_CODE);

      if (qValue == null || qSystem == null || qCode == null) {
        return null;
      }

      // Guard: reject UCUM definite-duration codes above the second boundary. Per FHIRPath spec
      // §9, only calendar-duration keywords and UCUM codes 's'/'ms' are valid right-hand operands
      // of Date/DateTime/Time +/-. Empty Quantities are caught by the null check above, so this
      // guard only fires when all three fields are present and match the forbidden set.
      if (QuantityValue.UCUM_SYSTEM.equals(qSystem)
          && FORBIDDEN_UCUM_DURATION_CODES.contains(qCode)) {
        throw new IllegalArgumentException(
            "Date/time arithmetic with UCUM duration unit '"
                + qCode
                + "' is not allowed (FHIRPath spec §9): definite-duration quantities above the"
                + " second boundary cannot be used in date/time arithmetic. Use the calendar"
                + " duration keyword (year, month, week, day, hour, minute) instead.");
      }

      // Resolve the effective calendar duration code
      final String durationCode = resolveDurationCode(qSystem, qCode);
      if (durationCode == null) {
        return null;
      }

      final boolean isAdd = OP_ADD.equals(op);
      final BigDecimal effectiveValue = isAdd ? qValue : qValue.negate();

      return applyArithmetic(temporal, effectiveValue, durationCode);
    } catch (final ArithmeticException | java.time.DateTimeException e) {
      // Overflow or invalid date arithmetic — return empty per project convention
      return null;
    }
  }

  /**
   * Resolves a quantity's system+code to a duration code recognized by {@link #UNIT_PRECISION}.
   * Returns null for non-time units or unrecognized systems.
   */
  @Nullable
  private static String resolveDurationCode(
      @Nonnull final String system, @Nonnull final String code) {
    if (!QuantityValue.CALENDAR_SYSTEM.equals(system)
        && !QuantityValue.UCUM_SYSTEM.equals(system)) {
      return null;
    }
    return UNIT_PRECISION.containsKey(code) ? code : null;
  }

  /**
   * Applies the temporal arithmetic to the given temporal string. Detects the temporal type and
   * precision from the string format, validates unit compatibility, and performs the arithmetic.
   */
  @Nullable
  private static String applyArithmetic(
      @Nonnull final String temporal,
      @Nonnull final BigDecimal value,
      @Nonnull final String durationCode) {
    // Normalize weeks to days
    final String effectiveCode;
    final BigDecimal effectiveValue;
    if ("week".equals(durationCode) || "wk".equals(durationCode)) {
      effectiveCode = "day";
      effectiveValue = value.multiply(BigDecimal.valueOf(7));
    } else {
      effectiveCode = durationCode;
      effectiveValue = value;
    }

    final Precision unitPrecision = UNIT_PRECISION.get(effectiveCode);
    if (unitPrecision == null) {
      return null;
    }

    // Try each temporal format in order
    return tryDateFormats(temporal, effectiveValue, unitPrecision);
  }

  /**
   * Tries to parse the temporal string as various date/datetime/time formats and applies the
   * arithmetic.
   */
  @Nullable
  private static String tryDateFormats(
      @Nonnull final String temporal,
      @Nonnull final BigDecimal value,
      @Nonnull final Precision unitPrecision) {

    Matcher m;

    // Year-only date: "2014"
    m = YEAR_ONLY.matcher(temporal);
    if (m.matches()) {
      return addToYearOnly(temporal, value, unitPrecision);
    }

    // Year-month date: "2014-01"
    m = YEAR_MONTH.matcher(temporal);
    if (m.matches()) {
      return addToYearMonth(temporal, value, unitPrecision);
    }

    // Full date: "2014-01-25"
    m = FULL_DATE.matcher(temporal);
    if (m.matches()) {
      return addToFullDate(temporal, value, unitPrecision);
    }

    // Year-only DateTime: "2014T"
    m = YEAR_DATETIME.matcher(temporal);
    if (m.matches()) {
      return addToYearOnlyDateTime(m.group(1), value, unitPrecision);
    }

    // Year-month DateTime: "2014-01T"
    m = YEAR_MONTH_DATETIME.matcher(temporal);
    if (m.matches()) {
      return addToYearMonthDateTime(m.group(1), value, unitPrecision);
    }

    // Date-only DateTime: "2014-01-25T"
    m = DATE_DATETIME.matcher(temporal);
    if (m.matches()) {
      return addToDateDateTime(m.group(1), value, unitPrecision);
    }

    // DateTime with offset
    m = DATETIME_WITH_OFFSET.matcher(temporal);
    if (m.matches()) {
      final String result = addToDateTime(m.group(1), value, unitPrecision);
      return result != null ? result + m.group(2) : null;
    }

    // DateTime with time (no offset)
    m = DATETIME_WITH_TIME.matcher(temporal);
    if (m.matches()) {
      return addToDateTime(temporal, value, unitPrecision);
    }

    // Time-only
    m = TIME_ONLY.matcher(temporal);
    if (m.matches()) {
      return addToTime(temporal, value, unitPrecision);
    }

    return null;
  }

  // ===== Year-only date =====

  @Nullable
  private static String addToYearOnly(
      @Nonnull final String temporal,
      @Nonnull final BigDecimal value,
      @Nonnull final Precision unitPrecision) {
    // Date values only accept year, month, week, day
    if (unitPrecision.ordinal() > Precision.DAY.ordinal()) {
      return null;
    }
    final long years = convertToTargetPrecision(value, unitPrecision, Precision.YEAR);
    final Year year = Year.parse(temporal);
    return year.plusYears(years).toString();
  }

  // ===== Year-month date =====

  @Nullable
  private static String addToYearMonth(
      @Nonnull final String temporal,
      @Nonnull final BigDecimal value,
      @Nonnull final Precision unitPrecision) {
    if (unitPrecision.ordinal() > Precision.DAY.ordinal()) {
      return null;
    }
    final long months = convertToTargetPrecision(value, unitPrecision, Precision.MONTH);
    return YearMonth.parse(temporal).plusMonths(months).toString();
  }

  // ===== Full date =====

  @Nullable
  private static String addToFullDate(
      @Nonnull final String temporal,
      @Nonnull final BigDecimal value,
      @Nonnull final Precision unitPrecision) {
    if (unitPrecision.ordinal() > Precision.DAY.ordinal()) {
      return null; // Date doesn't accept hour/minute/second/millisecond
    }
    final LocalDate date = LocalDate.parse(temporal);
    return addToLocalDate(date, value, unitPrecision).toString();
  }

  // ===== DateTime partials (year-only, year-month, date-only with T suffix) =====

  @Nonnull
  private static String addToYearOnlyDateTime(
      @Nonnull final String datePart,
      @Nonnull final BigDecimal value,
      @Nonnull final Precision unitPrecision) {
    final long years = convertToTargetPrecision(value, unitPrecision, Precision.YEAR);
    final Year year = Year.parse(datePart);
    return year.plusYears(years) + "T";
  }

  @Nonnull
  private static String addToYearMonthDateTime(
      @Nonnull final String datePart,
      @Nonnull final BigDecimal value,
      @Nonnull final Precision unitPrecision) {
    final long months = convertToTargetPrecision(value, unitPrecision, Precision.MONTH);
    return YearMonth.parse(datePart).plusMonths(months) + "T";
  }

  @Nullable
  private static String addToDateDateTime(
      @Nonnull final String datePart,
      @Nonnull final BigDecimal value,
      @Nonnull final Precision unitPrecision) {
    final LocalDate date = LocalDate.parse(datePart);
    if (unitPrecision.ordinal() <= Precision.DAY.ordinal()) {
      return addToLocalDate(date, value, unitPrecision) + "T";
    }
    // Finer than day: convert to days
    final long days = convertToTargetPrecision(value, unitPrecision, Precision.DAY);
    return date.plusDays(days) + "T";
  }

  /** Adds years, months, or days to a LocalDate based on the given precision. */
  @Nonnull
  private static LocalDate addToLocalDate(
      @Nonnull final LocalDate date,
      @Nonnull final BigDecimal value,
      @Nonnull final Precision precision) {
    return switch (precision) {
      case YEAR -> date.plusYears(truncate(value));
      case MONTH -> date.plusMonths(truncate(value));
      case DAY -> date.plusDays(truncate(value));
      default ->
          throw new IllegalStateException("Unexpected unit precision for date: " + precision);
    };
  }

  // ===== DateTime with time =====

  @Nullable
  private static String addToDateTime(
      @Nonnull final String temporal,
      @Nonnull final BigDecimal value,
      @Nonnull final Precision unitPrecision) {
    final LocalDateTime ldt = LocalDateTime.parse(temporal, FLEXIBLE_DATETIME);
    final Precision temporalPrecision = detectTemporalPrecision(temporal);

    final LocalDateTime result;
    if (unitPrecision.ordinal() <= temporalPrecision.ordinal()) {
      // Unit is same or coarser precision: apply directly
      result = addToLocalDateTime(ldt, value, unitPrecision);
    } else {
      // Unit is finer: convert to temporal's precision
      final long converted = convertToTargetPrecision(value, unitPrecision, temporalPrecision);
      result = addToLocalDateTime(ldt, BigDecimal.valueOf(converted), temporalPrecision);
    }

    return formatDateTime(result, temporalPrecision);
  }

  @Nonnull
  private static LocalDateTime addToLocalDateTime(
      @Nonnull final LocalDateTime ldt,
      @Nonnull final BigDecimal value,
      @Nonnull final Precision precision) {
    return switch (precision) {
      case YEAR -> ldt.plusYears(truncate(value));
      case MONTH -> ldt.plusMonths(truncate(value));
      case DAY -> ldt.plusDays(truncate(value));
      case HOUR -> ldt.plusHours(truncate(value));
      case MINUTE -> ldt.plusMinutes(truncate(value));
      case SECOND -> ldt.plusSeconds(truncate(value));
      case MILLISECOND -> ldt.plusNanos(value.longValue() * 1_000_000L);
    };
  }

  // ===== Time =====

  @Nullable
  private static String addToTime(
      @Nonnull final String temporal,
      @Nonnull final BigDecimal value,
      @Nonnull final Precision unitPrecision) {
    // Time only accepts hour, minute, second, millisecond
    if (unitPrecision.ordinal() < Precision.HOUR.ordinal()) {
      return null;
    }
    final LocalTime time = LocalTime.parse(temporal, FLEXIBLE_TIME);
    final Precision temporalPrecision = detectTemporalPrecision(temporal);

    final LocalTime result;
    if (unitPrecision.ordinal() <= temporalPrecision.ordinal()) {
      result = addToLocalTime(time, value, unitPrecision);
    } else {
      final long converted = convertToTargetPrecision(value, unitPrecision, temporalPrecision);
      result = addToLocalTime(time, BigDecimal.valueOf(converted), temporalPrecision);
    }

    return formatTime(result, temporalPrecision);
  }

  @Nonnull
  private static LocalTime addToLocalTime(
      @Nonnull final LocalTime time,
      @Nonnull final BigDecimal value,
      @Nonnull final Precision precision) {
    return switch (precision) {
      case HOUR -> time.plusHours(truncate(value));
      case MINUTE -> time.plusMinutes(truncate(value));
      case SECOND -> time.plusSeconds(truncate(value));
      case MILLISECOND -> time.plusNanos(value.longValue() * 1_000_000L);
      default ->
          throw new IllegalStateException("Unexpected unit precision for time: " + precision);
    };
  }

  // ===== Precision detection =====

  /**
   * Detects the precision of a temporal string (DateTime or Time) by examining its format. Looks
   * for fractional seconds (millisecond precision), two or more colons (second precision), or
   * defaults to minute precision.
   */
  @Nonnull
  private static Precision detectTemporalPrecision(@Nonnull final String temporal) {
    if (temporal.contains(".")) {
      return Precision.MILLISECOND;
    }
    final long colonCount = temporal.chars().filter(c -> c == ':').count();
    if (colonCount >= 2) {
      return Precision.SECOND;
    }
    return Precision.MINUTE;
  }

  // ===== Precision conversion =====

  /**
   * Converts a quantity value from its unit precision to the target precision. The decimal portion
   * is truncated per the FHIRPath spec.
   *
   * <p>Uses direct conversion factors between precision levels rather than an intermediate unit,
   * because the spec's conversion factors are not transitive (12 months = 1 year, 30 days = 1
   * month, but 365 days = 1 year, not 360). Specifically:
   *
   * <ul>
   *   <li>month → year: divide by 12
   *   <li>day → year: divide by 365
   *   <li>day → month: divide by 30
   *   <li>hour → day: divide by 24
   *   <li>minute → hour: divide by 60
   *   <li>second → minute: divide by 60
   *   <li>millisecond → second: divide by 1000
   * </ul>
   */
  private static long convertToTargetPrecision(
      @Nonnull final BigDecimal value,
      @Nonnull final Precision fromPrecision,
      @Nonnull final Precision toPrecision) {
    if (fromPrecision == toPrecision) {
      return truncate(value);
    }
    final BigDecimal factor = conversionFactor(fromPrecision, toPrecision);
    return truncate(value.divide(factor, RoundingMode.DOWN));
  }

  /**
   * Returns the direct conversion factor from one precision to a coarser precision. The result is
   * the number of {@code from} units per one {@code to} unit.
   */
  @Nonnull
  private static BigDecimal conversionFactor(
      @Nonnull final Precision from, @Nonnull final Precision to) {
    // Month to/from year: use 12 directly (spec: 1 year = 12 months)
    if (from == Precision.MONTH && to == Precision.YEAR) {
      return BigDecimal.valueOf(12);
    }
    // Day/hour/minute/second/millisecond to year: convert to days first, then use 365
    if (to == Precision.YEAR) {
      return toDaysFactor(from).multiply(BigDecimal.valueOf(DAYS_PER_YEAR));
    }
    // Day/hour/minute/second/millisecond to month: convert to days first, then use 30
    if (to == Precision.MONTH) {
      return toDaysFactor(from).multiply(BigDecimal.valueOf(DAYS_PER_MONTH));
    }
    // Below month: convert through time-based chain
    return timeConversionFactor(from, to);
  }

  /**
   * Returns the factor to convert from the given precision to days. For DAY returns 1, for sub-day
   * precisions multiplies through the chain.
   */
  @Nonnull
  private static BigDecimal toDaysFactor(@Nonnull final Precision from) {
    return switch (from) {
      case DAY -> BigDecimal.ONE;
      case HOUR -> BigDecimal.ONE.divide(HOURS_PER_DAY, 20, RoundingMode.HALF_UP);
      case MINUTE -> BigDecimal.ONE.divide(MINUTES_PER_DAY, 20, RoundingMode.HALF_UP);
      case SECOND -> BigDecimal.ONE.divide(SECONDS_PER_DAY, 20, RoundingMode.HALF_UP);
      case MILLISECOND -> BigDecimal.ONE.divide(MILLIS_PER_DAY, 20, RoundingMode.HALF_UP);
      default -> throw new IllegalStateException("Unexpected precision for toDaysFactor: " + from);
    };
  }

  /**
   * Returns the direct time-based conversion factor from a finer precision to a coarser precision
   * (DAY or below).
   */
  @Nonnull
  private static BigDecimal timeConversionFactor(
      @Nonnull final Precision from, @Nonnull final Precision to) {
    // Calculate milliseconds per unit for both, then divide
    final long fromMs = millisPerUnit(from);
    final long toMs = millisPerUnit(to);
    return BigDecimal.valueOf(toMs).divide(BigDecimal.valueOf(fromMs), 20, RoundingMode.HALF_UP);
  }

  /** Returns the number of milliseconds per one unit at the given precision. */
  private static long millisPerUnit(@Nonnull final Precision precision) {
    return switch (precision) {
      case DAY -> MS_PER_DAY;
      case HOUR -> MS_PER_HOUR;
      case MINUTE -> MS_PER_MINUTE;
      case SECOND -> MS_PER_SECOND;
      case MILLISECOND -> 1L;
      default ->
          throw new IllegalStateException("Unexpected precision for millisPerUnit: " + precision);
    };
  }

  // ===== Formatting =====

  @Nonnull
  private static String formatDateTime(
      @Nonnull final LocalDateTime ldt, @Nonnull final Precision precision) {
    return switch (precision) {
      case MINUTE -> DATETIME_MINUTE_FMT.format(ldt);
      case SECOND -> DATETIME_SECOND_FMT.format(ldt);
      case MILLISECOND -> appendFractionalSeconds(DATETIME_SECOND_FMT.format(ldt), ldt.getNano());
      default ->
          throw new IllegalStateException("Unexpected precision for formatDateTime: " + precision);
    };
  }

  @Nonnull
  private static String formatTime(
      @Nonnull final LocalTime time, @Nonnull final Precision precision) {
    return switch (precision) {
      case MINUTE -> TIME_MINUTE_FMT.format(time);
      case SECOND -> TIME_SECOND_FMT.format(time);
      case MILLISECOND -> appendFractionalSeconds(TIME_SECOND_FMT.format(time), time.getNano());
      default ->
          throw new IllegalStateException("Unexpected precision for formatTime: " + precision);
    };
  }

  /**
   * Appends fractional seconds to a base time string. Trailing zeros are trimmed, but at least one
   * fractional digit is always present (e.g., {@code ".0"} for zero nanos).
   */
  @Nonnull
  private static String appendFractionalSeconds(@Nonnull final String base, final int nanos) {
    if (nanos == 0) {
      return base + ".0";
    }
    final String frac = String.format("%09d", nanos);
    int end = frac.length();
    while (end > 0 && frac.charAt(end - 1) == '0') {
      end--;
    }
    return base + "." + frac.substring(0, end);
  }

  /** Truncates a BigDecimal toward zero to a long value. */
  private static long truncate(@Nonnull final BigDecimal value) {
    return value.setScale(0, RoundingMode.DOWN).longValue();
  }
}
