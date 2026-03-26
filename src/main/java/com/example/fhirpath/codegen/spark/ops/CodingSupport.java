package com.example.fhirpath.codegen.spark.ops;

import com.example.fhirpath.codegen.spark.SparkTypeMapper;
import jakarta.annotation.Nonnull;
import org.apache.spark.sql.Column;

/**
 * Coding equality helpers.
 *
 * <p>Two Codings are equal when their {@code system} and {@code code} fields match. Version,
 * display, and userSelected are not compared for equality (following Pathling semantics).
 *
 * <p>Inputs are cast to {@link SparkTypeMapper#CODING_TYPE} to handle null columns (Spark's VOID
 * type) which cannot have fields extracted directly.
 */
final class CodingSupport {

  private CodingSupport() {}

  @Nonnull
  static Column codingEquals(@Nonnull final Column left, @Nonnull final Column right) {
    final Column l = left.cast(SparkTypeMapper.CODING_TYPE);
    final Column r = right.cast(SparkTypeMapper.CODING_TYPE);
    return l.getField("system")
        .eqNullSafe(r.getField("system"))
        .and(l.getField("code").eqNullSafe(r.getField("code")));
  }
}
