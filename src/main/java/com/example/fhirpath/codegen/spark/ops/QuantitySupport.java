package com.example.fhirpath.codegen.spark.ops;

import static org.apache.spark.sql.functions.coalesce;
import static org.apache.spark.sql.functions.when;

import com.example.fhirpath.codegen.spark.udf.QuantityCanonicalize;
import jakarta.annotation.Nonnull;
import java.util.function.BinaryOperator;
import org.apache.spark.sql.Column;

/**
 * Quantity comparison helpers with UCUM-aware unit conversion.
 *
 * <p>Uses a two-tier comparison strategy (modeled after Pathling's {@code QuantityComparator}):
 *
 * <ol>
 *   <li><b>Tier 1 — Canonical comparison:</b> Both operands are canonicalized to UCUM base units
 *       via {@link QuantityCanonicalize}. If both canonical codes match, canonical values are
 *       compared. This handles cross-unit comparison (e.g., {@code cm} vs {@code m}).
 *   <li><b>Tier 2 — Direct comparison:</b> Falls back to same system+code exact match. This
 *       preserves behavior for non-UCUM quantities or when canonicalization fails.
 * </ol>
 *
 * <p>Returns {@code null} (empty collection) when units are incompatible (different dimensions) or
 * when canonicalization is not possible (e.g., non-definite calendar durations vs UCUM).
 */
final class QuantitySupport {

  private QuantitySupport() {}

  @Nonnull
  private static Column sameUnit(@Nonnull final Column left, @Nonnull final Column right) {
    return left.getField("system")
        .equalTo(right.getField("system"))
        .and(left.getField("code").equalTo(right.getField("code")));
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

      // Tier 2: Fall back to same system+code direct comparison
      final Column directCompare =
          when(
              sameUnit(left, right),
              comparator.apply(left.getField("value"), right.getField("value")));

      return coalesce(canonCompare, directCompare);
    };
  }
}
