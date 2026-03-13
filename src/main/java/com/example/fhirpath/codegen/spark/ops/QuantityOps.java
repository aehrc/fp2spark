package com.example.fhirpath.codegen.spark.ops;

import static org.apache.spark.sql.functions.when;

import jakarta.annotation.Nonnull;
import java.util.function.BinaryOperator;
import org.apache.spark.sql.Column;

/**
 * Quantity comparison helpers for same-unit equality and ordering.
 *
 * <p>Semantics: codes are compared as-is (case-sensitive). If codes match, values are compared. If
 * codes differ, the result is {@code null} (empty collection in FHIRPath).
 */
final class QuantityOps {

  private QuantityOps() {}

  @Nonnull
  static Column quantityEquals(@Nonnull final Column left, @Nonnull final Column right) {
    final Column sameUnit = left.getField("code").equalTo(right.getField("code"));
    return when(sameUnit, left.getField("value").equalTo(right.getField("value")));
  }

  @Nonnull
  static Column quantityCompare(
      @Nonnull final Column left,
      @Nonnull final Column right,
      @Nonnull final BinaryOperator<Column> comparator) {
    final Column sameUnit = left.getField("code").equalTo(right.getField("code"));
    return when(sameUnit, comparator.apply(left.getField("value"), right.getField("value")));
  }
}
