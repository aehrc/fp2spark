package com.example.fhirpath.codegen.spark.udf;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
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
 *       explicit offsets are converted directly; values without offsets use the system default
 *       timezone.
 *   <li>Seconds normalization: per FHIRPath spec, seconds and fractional seconds are a single
 *       precision level. All seconds-precision values are padded to 9 fractional digits so that
 *       length-based precision comparison works correctly (e.g., {@code :31} and {@code :31.1} are
 *       both seconds precision).
 * </ol>
 *
 * <p>After normalization, precision maps to string length, so same-precision values can be compared
 * lexicographically (ISO 8601 is lexicographically ordered for same-precision UTC strings).
 *
 * <p><b>Output length → precision mapping:</b>
 *
 * <ul>
 *   <li>Date: year=4, year-month=7, full=10
 *   <li>DateTime partial: yearT=5, year-monthT=8, fullT=11
 *   <li>DateTime with time: HH:mm=16, HH:mm:ss.nnnnnnnnn=29
 *   <li>Time: HH:mm=5, HH:mm:ss.nnnnnnnnn=14
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
   * 1: the date-time portion before the offset. Group 2: the offset ({@code Z} or {@code +hh:mm} /
   * {@code -hh:mm}).
   */
  private static final Pattern DATETIME_WITH_OFFSET =
      Pattern.compile("^(\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}(?::\\d{2}(?:\\.\\d+)?)?)([Z+-].*)$");

  /**
   * Pattern matching a DateTime value with time components but no timezone offset (has 'T' followed
   * by at least hours:minutes).
   */
  private static final Pattern DATETIME_WITH_TIME_NO_OFFSET =
      Pattern.compile("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}(?::\\d{2}(?:\\.\\d+)?)?$");

  /** Number of fractional digits to pad seconds to (nanosecond precision). */
  private static final int NANO_DIGITS = 9;

  /**
   * Build a base {@link DateTimeFormatterBuilder} for ISO date-times with optional seconds and
   * optional fractional seconds up to 9 digits. Shared by {@link #FLEXIBLE_DATETIME} and {@link
   * #OFFSET_DATETIME}.
   */
  private static DateTimeFormatterBuilder flexibleDateTimeBuilder() {
    return new DateTimeFormatterBuilder()
        .appendPattern("yyyy-MM-dd'T'HH:mm")
        .optionalStart()
        .appendLiteral(':')
        .appendValue(ChronoField.SECOND_OF_MINUTE, 2)
        .optionalStart()
        .appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true)
        .optionalEnd()
        .optionalEnd();
  }

  /**
   * Flexible formatter that can parse ISO date-times with optional seconds and optional fractional
   * seconds up to 9 digits.
   */
  private static final DateTimeFormatter FLEXIBLE_DATETIME =
      flexibleDateTimeBuilder().toFormatter();

  /**
   * Flexible OffsetDateTime formatter that handles Z, +hh:mm, and -hh:mm offsets, with optional
   * seconds and fractional seconds.
   */
  private static final DateTimeFormatter OFFSET_DATETIME =
      flexibleDateTimeBuilder().appendOffset("+HH:MM", "Z").toFormatter();

  /** Formatter for DateTime output at minutes precision. */
  private static final DateTimeFormatter DATETIME_MINUTES =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

  /** Formatter for DateTime output at seconds precision. */
  private static final DateTimeFormatter DATETIME_SECONDS =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

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

    // Check for DateTime with time but no offset — apply system default timezone
    if (DATETIME_WITH_TIME_NO_OFFSET.matcher(value).matches()) {
      return normalizeWithSystemTimezone(value);
    }

    // Date-only values (2014, 2014-01, 2014-01-25),
    // Date-only DateTime partials (2014T, 2014-01T, 2014-01-25T),
    // and Time values (12:00, 12:00:00, 12:00:00.123) — no timezone conversion needed
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
    return formatUtcDateTime(utc.toLocalDateTime(), hasSeconds(withoutOffset));
  }

  /**
   * Normalize a DateTime without an explicit timezone offset by applying the system default
   * timezone.
   *
   * @param value DateTime string without offset (e.g., {@code 2014-01-25T14:30})
   * @return UTC-normalized string without offset
   */
  private static String normalizeWithSystemTimezone(final String value) {
    final LocalDateTime ldt = LocalDateTime.parse(value, FLEXIBLE_DATETIME);
    final OffsetDateTime zoned = ldt.atZone(ZoneId.systemDefault()).toOffsetDateTime();
    final OffsetDateTime utc = zoned.withOffsetSameInstant(ZoneOffset.UTC);
    return formatUtcDateTime(utc.toLocalDateTime(), hasSeconds(value));
  }

  /**
   * Format a UTC LocalDateTime with appropriate precision.
   *
   * @param ldt the UTC date-time
   * @param includeSeconds whether the original value had seconds precision
   * @return formatted string with seconds padded to fixed width if applicable
   */
  private static String formatUtcDateTime(final LocalDateTime ldt, final boolean includeSeconds) {
    if (!includeSeconds) {
      return DATETIME_MINUTES.format(ldt);
    }
    // Seconds precision: always pad to 9 fractional digits
    final String base = DATETIME_SECONDS.format(ldt);
    return base + "." + String.format("%09d", ldt.getNano());
  }

  /**
   * Check if a time string has seconds component (two or more colons for DateTime, or the pattern
   * HH:mm:ss for Time values).
   */
  private static boolean hasSeconds(final String value) {
    final int first = value.indexOf(':');
    return first >= 0 && value.indexOf(':', first + 1) >= 0;
  }

  /**
   * Pad seconds component to fixed fractional width for Time and Date-only values. If the value has
   * seconds (HH:mm:ss or HH:mm:ss.fff), pad fractional part to {@value NANO_DIGITS} digits.
   * Otherwise return unchanged.
   */
  private static String padSeconds(final String value) {
    if (!hasSeconds(value)) {
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
    return value + "." + "0".repeat(NANO_DIGITS);
  }

  /** Pad a string with trailing zeros to the desired length. */
  private static String padRight(final String s, final int length) {
    if (s.length() >= length) {
      return s.substring(0, length);
    }
    return s + "0".repeat(length - s.length());
  }
}
