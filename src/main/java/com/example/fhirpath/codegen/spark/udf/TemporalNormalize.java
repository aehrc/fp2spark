package com.example.fhirpath.codegen.spark.udf;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.spark.sql.api.java.UDF1;
import org.apache.spark.sql.expressions.UserDefinedFunction;
import org.apache.spark.sql.functions;
import org.apache.spark.sql.types.DataTypes;

/**
 * Normalizes temporal ISO 8601 strings for precision-aware comparison.
 *
 * <p>Normalization steps:
 *
 * <ol>
 *   <li>Timezone conversion: DateTime values with hour+ precision are converted to UTC. Values with
 *       explicit offsets are converted directly; values without offsets are treated as UTC (see
 *       {@link #normalizeWithoutOffset} for the rationale).
 *   <li>Seconds normalization: per FHIRPath spec, seconds and fractional seconds are a single
 *       precision level. All seconds-precision values are padded to 9 fractional digits so that
 *       length-based precision comparison works correctly (e.g., {@code :31} and {@code :31.1} are
 *       both seconds precision).
 *   <li>Trailing {@code T} stripping: date-only DateTime partials (e.g. {@code 2014T}) drop the
 *       {@code T} so that prefix comparison aligns positionally with full DateTime values.
 * </ol>
 *
 * <p>After normalization, the output is structured so that lexicographic prefix comparison
 * implements the FHIRPath spec component-walk semantics: same-precision values compare
 * lexicographically; different-precision values compare on their common prefix. Date-only DateTime
 * partials (e.g. {@code 2014T}) have their trailing {@code T} stripped, so they share the same
 * output lengths as the corresponding Date values — the type system at the analyzer layer prevents
 * cross-type Date vs DateTime comparison from reaching this UDF.
 *
 * <p><b>Output length → precision mapping (DateTime / Date):</b>
 *
 * <ul>
 *   <li>year=4, year-month=7, full date=10
 *   <li>DateTime hour=13, minute=16, second=29
 *   <li>Time: hour=2, minute=5, second=18
 * </ul>
 */
public final class TemporalNormalize {

  private TemporalNormalize() {}

  /** Spark UDF that normalizes a temporal string for comparison. */
  @Nonnull
  public static final UserDefinedFunction UDF =
      functions.udf((UDF1<String, String>) TemporalNormalize::normalize, DataTypes.StringType);

  /**
   * Pattern matching a DateTime value with time components and an explicit timezone offset. Group
   * 1: the date-time portion before the offset (hours required, minutes/seconds/fraction optional).
   * Group 2: the offset ({@code Z} or {@code +hh:mm} / {@code -hh:mm}).
   */
  private static final Pattern DATETIME_WITH_OFFSET =
      Pattern.compile(
          "^(\\d{4}-\\d{2}-\\d{2}T\\d{2}(?::\\d{2}(?::\\d{2}(?:\\.\\d+)?)?)?)([Z+-].*)$");

  /**
   * Pattern matching a DateTime value with time components but no timezone offset (has 'T' followed
   * by at least hours, with minutes/seconds/fraction optional).
   */
  private static final Pattern DATETIME_WITH_TIME_NO_OFFSET =
      Pattern.compile("^\\d{4}-\\d{2}-\\d{2}T\\d{2}(?::\\d{2}(?::\\d{2}(?:\\.\\d+)?)?)?$");

  /** Number of fractional digits to pad seconds to (nanosecond precision). */
  private static final int NANO_DIGITS = 9;

  /** Pre-built padding string of {@value #NANO_DIGITS} zeros, used when no fractional seconds. */
  private static final String NANO_ZEROS = "0".repeat(NANO_DIGITS);

  /**
   * Build a base {@link DateTimeFormatterBuilder} for ISO date-times with optional minutes,
   * seconds, and fractional seconds up to 9 digits. Missing components default to zero.
   *
   * <p>Package-private so that {@code TemporalArithmetic} in the same package can reuse the same
   * format definition.
   */
  static DateTimeFormatterBuilder flexibleDateTimeBuilder() {
    return new DateTimeFormatterBuilder()
        .appendPattern("yyyy-MM-dd'T'HH")
        .optionalStart()
        .appendLiteral(':')
        .appendValue(ChronoField.MINUTE_OF_HOUR, 2)
        .optionalStart()
        .appendLiteral(':')
        .appendValue(ChronoField.SECOND_OF_MINUTE, 2)
        .optionalStart()
        .appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true)
        .optionalEnd()
        .optionalEnd()
        .optionalEnd()
        .parseDefaulting(ChronoField.MINUTE_OF_HOUR, 0)
        .parseDefaulting(ChronoField.SECOND_OF_MINUTE, 0)
        .parseDefaulting(ChronoField.NANO_OF_SECOND, 0);
  }

  /**
   * Flexible formatter that can parse ISO date-times with optional minutes, seconds, and fractional
   * seconds.
   */
  private static final DateTimeFormatter FLEXIBLE_DATETIME =
      flexibleDateTimeBuilder().toFormatter();

  /**
   * Flexible OffsetDateTime formatter that handles Z, +hh:mm, and -hh:mm offsets, with optional
   * minutes, seconds, and fractional seconds.
   */
  private static final DateTimeFormatter OFFSET_DATETIME =
      flexibleDateTimeBuilder().appendOffset("+HH:MM", "Z").toFormatter();

  /** Formatter for DateTime output at hour precision. */
  private static final DateTimeFormatter DATETIME_HOURS =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH");

  /** Formatter for DateTime output at minutes precision. */
  private static final DateTimeFormatter DATETIME_MINUTES =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

  /** Formatter for DateTime output at seconds precision. */
  private static final DateTimeFormatter DATETIME_SECONDS =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

  /** Time-component precision of a DateTime literal. */
  private enum TimePrecision {
    HOUR,
    MINUTE,
    SECOND
  }

  /**
   * Normalize a temporal string for comparison.
   *
   * @param value ISO 8601 temporal string (Date, DateTime, or Time) without the {@code @} prefix
   * @return normalized string: UTC (no offset suffix), seconds padded to fixed width
   */
  @Nullable
  static String normalize(@Nullable final String value) {
    if (value == null) {
      return null;
    }

    // Check for DateTime with explicit offset (Z, +hh:mm, -hh:mm)
    final Matcher offsetMatcher = DATETIME_WITH_OFFSET.matcher(value);
    if (offsetMatcher.matches()) {
      return normalizeWithOffset(value, offsetMatcher.group(1));
    }

    if (DATETIME_WITH_TIME_NO_OFFSET.matcher(value).matches()) {
      return normalizeWithoutOffset(value);
    }

    // Date-only DateTime partials (2014T, 2014-01T, 2014-01-25T) — strip the trailing T so the
    // output aligns positionally with full DateTime values for prefix-based comparison.
    if (value.endsWith("T")) {
      return value.substring(0, value.length() - 1);
    }

    // Date-only values (2014, 2014-01, 2014-01-25)
    // and Time values (12, 12:30, 12:00:00, 12:00:00.123) — no timezone conversion needed
    return padSeconds(value);
  }

  /**
   * Normalize a DateTime with an explicit timezone offset to UTC.
   *
   * @param value DateTime string with offset (e.g., {@code 2017-11-05T01:30:00.0-04:00})
   * @param withoutOffset the date-time portion without the offset suffix (regex group 1)
   * @return UTC-normalized string without offset
   */
  private static String normalizeWithOffset(final String value, final String withoutOffset) {
    final OffsetDateTime odt = OffsetDateTime.parse(value, OFFSET_DATETIME);
    final OffsetDateTime utc = odt.withOffsetSameInstant(ZoneOffset.UTC);
    return formatUtcDateTime(utc.toLocalDateTime(), timePrecisionOf(withoutOffset));
  }

  /**
   * Normalize a DateTime without an explicit timezone offset by treating it as UTC.
   *
   * <p>The FHIRPath spec §6.1 explicitly defers the missing-offset case to the implementation;
   * Pathling makes the same choice. Using a fixed UTC default keeps dedup, equality, and comparison
   * deterministic across JVM timezone settings.
   *
   * @param value DateTime string without offset (e.g., {@code 2014-01-25T14:30})
   * @return UTC-normalized string without offset
   */
  private static String normalizeWithoutOffset(final String value) {
    final LocalDateTime ldt = LocalDateTime.parse(value, FLEXIBLE_DATETIME);
    return formatUtcDateTime(ldt, timePrecisionOf(value));
  }

  /**
   * Format a UTC LocalDateTime at the given time-component precision. Seconds-precision values are
   * padded to 9 fractional digits so that the FHIRPath spec's "seconds and milliseconds are a
   * single precision" rule holds under string comparison.
   *
   * @param ldt the UTC date-time
   * @param precision the time-component precision of the original value
   * @return formatted string at the given precision
   */
  private static String formatUtcDateTime(final LocalDateTime ldt, final TimePrecision precision) {
    return switch (precision) {
      case HOUR -> DATETIME_HOURS.format(ldt);
      case MINUTE -> DATETIME_MINUTES.format(ldt);
      case SECOND -> DATETIME_SECONDS.format(ldt) + "." + padNanos(ldt.getNano());
    };
  }

  /**
   * Format a nanosecond value (0..999_999_999) as a {@value #NANO_DIGITS}-digit zero-padded string.
   * Hand-rolled to avoid the reflective {@link String#format} machinery on the per-row UDF path.
   */
  private static String padNanos(final int nanos) {
    final String s = Integer.toString(nanos);
    final int len = s.length();
    if (len >= NANO_DIGITS) {
      return s;
    }
    return NANO_ZEROS.substring(0, NANO_DIGITS - len) + s;
  }

  /**
   * Determine the time-component precision of a temporal value from the count of {@code :}
   * separators in the time portion. Zero colons → hour precision; one → minute; two → second. Works
   * for both DateTime values (where the time portion follows {@code T}) and bare Time values.
   */
  private static TimePrecision timePrecisionOf(final String value) {
    final int first = value.indexOf(':');
    if (first < 0) {
      return TimePrecision.HOUR;
    }
    return value.indexOf(':', first + 1) >= 0 ? TimePrecision.SECOND : TimePrecision.MINUTE;
  }

  /**
   * Pad seconds component to fixed fractional width for Time and Date-only values. If the value has
   * seconds (HH:mm:ss or HH:mm:ss.fff), pad fractional part to {@value NANO_DIGITS} digits.
   * Otherwise return unchanged.
   */
  private static String padSeconds(final String value) {
    if (timePrecisionOf(value) != TimePrecision.SECOND) {
      return value;
    }

    // Find the seconds portion and pad
    final int dotIndex = value.indexOf('.');
    if (dotIndex >= 0) {
      // Has fractional seconds — pad to fixed width
      final String beforeFrac = value.substring(0, dotIndex);
      final String frac = value.substring(dotIndex + 1);
      return beforeFrac + "." + padRight(frac, NANO_DIGITS);
    }
    // No fractional seconds — add .000000000
    return value + "." + NANO_ZEROS;
  }

  /** Pad a string with trailing zeros to the desired length, or truncate if longer. */
  private static String padRight(final String s, final int length) {
    final int len = s.length();
    if (len >= length) {
      return s.substring(0, length);
    }
    return s + NANO_ZEROS.substring(0, length - len);
  }
}
