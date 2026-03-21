package com.example.fhirpath.codegen.spark.ops;

import static org.apache.spark.sql.functions.coalesce;
import static org.apache.spark.sql.functions.concat;
import static org.apache.spark.sql.functions.lit;

import jakarta.annotation.Nonnull;
import org.apache.spark.sql.Column;

/** Spark column expression helpers for string operations. */
final class StringSupport {

  private StringSupport() {}

  /**
   * String concatenation using the {@code &} operator. Treats null/empty operands as empty strings,
   * per the FHIRPath spec.
   */
  @Nonnull
  static Column stringConcat(@Nonnull final Column left, @Nonnull final Column right) {
    return concat(coalesce(left, lit("")), coalesce(right, lit("")));
  }
}
