package com.example.fhirpath.codegen.spark.ops;

import static org.apache.spark.sql.functions.when;

import com.example.fhirpath.typing.Type;
import com.example.fhirpath.typing.Types;
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

  static Column quantityEquals(final Column left, final Column right) {
    final Column sameUnit = left.getField("code").equalTo(right.getField("code"));
    return when(sameUnit, left.getField("value").equalTo(right.getField("value")));
  }

  static Column quantityCompare(
      final Column left, final Column right, final BinaryOperator<Column> comparator) {
    final Column sameUnit = left.getField("code").equalTo(right.getField("code"));
    return when(sameUnit, comparator.apply(left.getField("value"), right.getField("value")));
  }

  static boolean isQuantityType(final Type type) {
    return type == Types.QUANTITY;
  }
}
