package com.example.fhirpath.codegen.spark.ops;

import static org.apache.spark.sql.functions.length;
import static org.apache.spark.sql.functions.when;

import com.example.fhirpath.typing.PrimitiveType;
import java.util.function.BinaryOperator;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.api.java.UDF1;
import org.apache.spark.sql.expressions.UserDefinedFunction;
import org.apache.spark.sql.functions;
import org.apache.spark.sql.types.DataTypes;

/**
 * Spark column expression helpers for precision-aware temporal comparison.
 *
 * <p>Uses a Spark UDF ({@code normalize_temporal}) to normalize temporal strings to UTC with
 * trailing fractional zeros stripped. After normalization, precision maps to string length, so
 * same-precision values can be compared lexicographically.
 *
 * <p>When two temporal values have different precision levels, equality and comparison return
 * {@code null} (empty collection) per the FHIRPath specification.
 */
final class TemporalOps {

  private TemporalOps() {}

  /** UDF that normalizes a temporal string for comparison. */
  private static final UserDefinedFunction NORMALIZE_TEMPORAL =
      functions.udf((UDF1<String, String>) TemporalNormalize::normalize, DataTypes.StringType);

  /**
   * Apply the normalize_temporal UDF to a column.
   *
   * @param col the column containing a temporal string
   * @return normalized column
   */
  private static Column normalize(final Column col) {
    return NORMALIZE_TEMPORAL.apply(col);
  }

  /**
   * Precision-aware temporal equality. Normalizes both values, compares string lengths (precision).
   * Same precision → {@code equalTo}. Different precision → {@code null} (empty).
   *
   * @param left the left temporal column
   * @param right the right temporal column
   * @return a Boolean column: true/false for same precision, null for different precision
   */
  static Column temporalEquals(final Column left, final Column right) {
    final Column normLeft = normalize(left);
    final Column normRight = normalize(right);
    final Column samePrecision = length(normLeft).equalTo(length(normRight));
    return when(samePrecision, normLeft.equalTo(normRight));
  }

  /**
   * Precision-aware temporal comparison. Normalizes both values, compares string lengths
   * (precision). Same precision → applies the given comparator. Different precision → {@code null}
   * (empty).
   *
   * @param left the left temporal column
   * @param right the right temporal column
   * @param comparator the comparison function (e.g., {@code Column::gt})
   * @return a Boolean column: true/false for same precision, null for different precision
   */
  static Column temporalCompare(
      final Column left, final Column right, final BinaryOperator<Column> comparator) {
    final Column normLeft = normalize(left);
    final Column normRight = normalize(right);
    final Column samePrecision = length(normLeft).equalTo(length(normRight));
    return when(samePrecision, comparator.apply(normLeft, normRight));
  }

  /**
   * Check if a type is a temporal type (Date, DateTime, or Time).
   *
   * @param type the type to check
   * @return true if the type is temporal
   */
  static boolean isTemporalType(final PrimitiveType type) {
    return type == PrimitiveType.DATE
        || type == PrimitiveType.DATE_TIME
        || type == PrimitiveType.TIME;
  }
}
