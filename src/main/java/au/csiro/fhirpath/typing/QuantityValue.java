package au.csiro.fhirpath.typing;

import jakarta.annotation.Nonnull;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

/**
 * Wrapper for FHIRPath Quantity literal values (e.g. {@code 10 'mg'}, {@code 4 days}).
 *
 * <p>Fields follow the FHIR Quantity encoding:
 *
 * <ul>
 *   <li>{@code value} — the numeric value (always Decimal per spec)
 *   <li>{@code unit} — display unit string (e.g. {@code mg}, {@code year})
 *   <li>{@code system} — {@code http://unitsofmeasure.org} for UCUM, {@code
 *       http://hl7.org/fhirpath/calendar} for calendar durations
 *   <li>{@code code} — canonical unit code (e.g. {@code mg} for UCUM, {@code year} for calendar)
 * </ul>
 *
 * @param value the numeric value
 * @param unit the display unit string
 * @param system the unit system URI
 * @param code the canonical unit code
 */
public record QuantityValue(
    @Nonnull BigDecimal value, @Nonnull String unit, @Nonnull String system, @Nonnull String code) {

  /** Quantity subfield specifications for field traversal (e.g. {@code quantity.value}). */
  private static final Map<String, FieldSpec> FIELDS =
      Map.of(
          "value", new FieldSpec("value", Shape.single(SystemType.DECIMAL)),
          "unit", new FieldSpec("unit", Shape.single(SystemType.STRING)),
          "system", new FieldSpec("system", Shape.single(SystemType.STRING)),
          "code", new FieldSpec("code", Shape.single(SystemType.STRING)));

  public static final String UCUM_SYSTEM = "http://unitsofmeasure.org";
  public static final String CALENDAR_SYSTEM = "http://hl7.org/fhirpath/calendar";
  public static final String DEFAULT_UNIT = "1";

  /**
   * Calendar duration keyword to canonical code mapping. Plural forms normalize to singular (e.g.
   * {@code days} → {@code day}).
   */
  private static final Map<String, String> CALENDAR_KEYWORDS =
      Map.ofEntries(
          Map.entry("year", "year"),
          Map.entry("years", "year"),
          Map.entry("month", "month"),
          Map.entry("months", "month"),
          Map.entry("week", "week"),
          Map.entry("weeks", "week"),
          Map.entry("day", "day"),
          Map.entry("days", "day"),
          Map.entry("hour", "hour"),
          Map.entry("hours", "hour"),
          Map.entry("minute", "minute"),
          Map.entry("minutes", "minute"),
          Map.entry("second", "second"),
          Map.entry("seconds", "second"),
          Map.entry("millisecond", "millisecond"),
          Map.entry("milliseconds", "millisecond"));

  /**
   * Creates a Quantity with a UCUM unit.
   *
   * @param value the numeric value
   * @param ucumCode the UCUM unit code (without quotes)
   */
  @Nonnull
  public static QuantityValue ofUcum(
      @Nonnull final BigDecimal value, @Nonnull final String ucumCode) {
    return new QuantityValue(value, ucumCode, UCUM_SYSTEM, ucumCode);
  }

  /**
   * Creates a Quantity with a calendar duration keyword.
   *
   * @param value the numeric value
   * @param keyword the calendar duration keyword (e.g. {@code day}, {@code years})
   * @throws IllegalArgumentException if the keyword is not a valid calendar duration
   */
  @Nonnull
  public static QuantityValue ofCalendar(
      @Nonnull final BigDecimal value, @Nonnull final String keyword) {
    final String code = CALENDAR_KEYWORDS.get(keyword);
    if (code == null) {
      throw new IllegalArgumentException("Unknown calendar duration keyword: " + keyword);
    }
    return new QuantityValue(value, keyword, CALENDAR_SYSTEM, code);
  }

  /**
   * Creates a Quantity with the default UCUM unit ({@code 1}).
   *
   * @param value the numeric value
   */
  @Nonnull
  public static QuantityValue ofDefault(@Nonnull final BigDecimal value) {
    return new QuantityValue(value, DEFAULT_UNIT, UCUM_SYSTEM, DEFAULT_UNIT);
  }

  /**
   * Returns whether the given keyword is a calendar duration keyword.
   *
   * @param keyword the keyword to check
   * @return true if it is a calendar duration keyword
   */
  public static boolean isCalendarKeyword(@Nonnull final String keyword) {
    return CALENDAR_KEYWORDS.containsKey(keyword);
  }

  /**
   * Returns the canonical (singular) calendar code for a keyword, or the keyword unchanged if it is
   * not a calendar duration keyword.
   *
   * @param keyword the keyword to normalize (e.g. {@code "days"}, {@code "day"}, {@code "s"})
   * @return the canonical calendar code (e.g. {@code "day"}) or the original keyword for
   *     non-calendar
   */
  @Nonnull
  public static String canonicalCalendarCode(@Nonnull final String keyword) {
    final String code = CALENDAR_KEYWORDS.get(keyword);
    return code != null ? code : keyword;
  }

  /**
   * Resolves a Quantity subfield specification by name.
   *
   * @param fieldName the field name (e.g. "value", "unit", "system", "code")
   * @return the field specification, or empty if the field name is not recognized
   */
  @Nonnull
  public static Optional<FieldSpec> resolveField(@Nonnull final String fieldName) {
    return Optional.ofNullable(FIELDS.get(fieldName));
  }
}
