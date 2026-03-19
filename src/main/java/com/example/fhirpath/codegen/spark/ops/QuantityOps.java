package com.example.fhirpath.codegen.spark.ops;

import static org.apache.spark.sql.functions.when;

import jakarta.annotation.Nonnull;
import java.util.function.BinaryOperator;
import org.apache.spark.sql.Column;

/**
 * Quantity comparison helpers for same-unit equality and ordering.
 *
 * <p>Semantics: system and code are compared as-is (case-sensitive). If both match, values are
 * compared. If either differs, the result is {@code null} (empty collection in FHIRPath).
 */
final class QuantityOps {

  private QuantityOps() {}

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
      final Column sameUnit = sameUnit(left, right);
      return when(sameUnit, comparator.apply(left.getField("value"), right.getField("value")));
    };
  }
}
