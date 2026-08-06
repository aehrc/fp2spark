package au.csiro.fhirpath.compat;

import au.csiro.fhirpath.test.ResourceDataBuilder;
import au.csiro.fhirpath.typing.CodingValue;
import au.csiro.fhirpath.typing.QuantityValue;
import jakarta.annotation.Nullable;

/**
 * Compat equivalents of Pathling's {@code FhirTypedLiteral} static factory methods.
 *
 * <p>These produce objects that are directly comparable to Spark execution results:
 *
 * <ul>
 *   <li>Temporal types (time, date, dateTime) → raw String (Spark stores these as strings)
 *   <li>Coding → {@link CodingValue} (adapted to Spark Row by {@code TypeAdapter})
 *   <li>Quantity → {@link QuantityValue} (adapted to Spark Row by {@code TypeAdapter})
 * </ul>
 *
 * <p>Usage: replace {@code import static ...FhirTypedLiteral.*} with {@code import static
 * ...CompatLiterals.*} in compat test files.
 */
public final class CompatLiterals {

  private CompatLiterals() {}

  /** Returns the time string as-is (Spark stores times as strings). */
  @Nullable
  public static String toTime(@Nullable final String literal) {
    return literal;
  }

  /** Returns the date string as-is (Spark stores dates as strings). */
  @Nullable
  public static String toDate(@Nullable final String literal) {
    return literal;
  }

  /** Returns the dateTime string as-is (Spark stores dateTimes as strings). */
  @Nullable
  public static String toDateTime(@Nullable final String literal) {
    return literal;
  }

  /** Parses a pipe-delimited coding string into a {@link CodingValue}. */
  @Nullable
  public static CodingValue toCoding(@Nullable final String literal) {
    return literal != null ? ResourceDataBuilder.parseCodingValue(literal) : null;
  }

  /** Parses a FHIRPath quantity literal into a {@link QuantityValue}. */
  @Nullable
  public static QuantityValue toQuantity(@Nullable final String literal) {
    return literal != null ? ResourceDataBuilder.parseQuantity(literal) : null;
  }
}
