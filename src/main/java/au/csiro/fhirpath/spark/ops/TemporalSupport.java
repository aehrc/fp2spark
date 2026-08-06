package au.csiro.fhirpath.spark.ops;

import static org.apache.spark.sql.functions.least;
import static org.apache.spark.sql.functions.length;
import static org.apache.spark.sql.functions.lit;
import static org.apache.spark.sql.functions.when;

import au.csiro.fhirpath.spark.udf.TemporalNormalize;
import au.csiro.fhirpath.typing.Type;
import au.csiro.fhirpath.typing.TypeSets;
import jakarta.annotation.Nonnull;
import java.util.function.BinaryOperator;
import org.apache.spark.sql.Column;

/**
 * Spark column expression helpers for precision-aware temporal comparison.
 *
 * <p>Uses a Spark UDF ({@code normalize_temporal}) to normalize temporal strings to UTC, with
 * trailing {@code T} stripped from date-only DateTime partials and seconds padded to a fixed
 * fractional width. After normalization, precision maps to string length, and the format is
 * positionally aligned across precisions so that lexicographic prefix comparison implements the
 * FHIRPath spec's component-walk equality and comparison semantics.
 *
 * <p>For the shared prefix length {@code n = min(len(L), len(R))}:
 *
 * <ul>
 *   <li>If the prefixes differ → apply the comparator to the prefixes (values differ at the highest
 *       shared precision).
 *   <li>If the prefixes match and lengths are equal → apply the comparator to the full strings
 *       (values are equal at the same precision).
 *   <li>If the prefixes match and lengths differ → return {@code null} / empty (precision
 *       mismatch).
 * </ul>
 */
final class TemporalSupport {

  private TemporalSupport() {}

  /**
   * Apply the normalize_temporal UDF to a column.
   *
   * @param col the column containing a temporal string
   * @return normalized column
   */
  @Nonnull
  private static Column normalize(@Nonnull final Column col) {
    return TemporalNormalize.UDF.apply(col);
  }

  /**
   * Precision-aware temporal equality. See {@link TemporalSupport} class doc for the comparison
   * algorithm.
   *
   * @param left the left temporal column
   * @param right the right temporal column
   * @return a Boolean column: true/false when comparable, null when precisions differ but values
   *     agree to the shared precision
   */
  @Nonnull
  static Column temporalEquals(@Nonnull final Column left, @Nonnull final Column right) {
    return temporalComparator(Column::equalTo).apply(left, right);
  }

  /**
   * Precision-aware temporal comparator factory. Returns a {@link BinaryOperator} that compares
   * normalized prefixes per the FHIRPath component-walk semantics.
   *
   * @param comparator the comparison function (e.g., {@code Column::gt})
   * @return a binary operator that performs precision-aware temporal comparison
   */
  @Nonnull
  static BinaryOperator<Column> temporalComparator(
      @Nonnull final BinaryOperator<Column> comparator) {
    return (left, right) -> {
      final Column normLeft = normalize(left);
      final Column normRight = normalize(right);
      final Column commonLen = least(length(normLeft), length(normRight));
      final Column prefixLeft = normLeft.substr(lit(1), commonLen);
      final Column prefixRight = normRight.substr(lit(1), commonLen);
      final Column precisionMismatch =
          prefixLeft.equalTo(prefixRight).and(length(normLeft).notEqual(length(normRight)));
      // The otherwise branch covers two sub-cases that both reduce to comparing the prefixes:
      // (a) prefixes differ → values disagree at the highest shared precision; (b) lengths are
      // equal → prefixes equal the full normalized strings, so a same-precision comparison.
      return when(precisionMismatch, lit(null))
          .otherwise(comparator.apply(prefixLeft, prefixRight));
    };
  }

  /**
   * Check if a type is a temporal type (Date, DateTime, or Time).
   *
   * @param type the type to check
   * @return true if the type is temporal
   */
  static boolean isTemporalType(@Nonnull final Type type) {
    return TypeSets.TEMPORAL.contains(type);
  }
}
