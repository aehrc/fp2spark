package com.example.fhirpath.codegen.spark.ops;

import static org.apache.spark.sql.functions.when;

import jakarta.annotation.Nonnull;
import org.apache.spark.sql.Column;

/**
 * Null-propagation helper for operations whose result is empty (SQL {@code NULL}) whenever any of
 * their inputs is empty — the common FHIRPath rule that an operation applied to an empty collection
 * yields an empty result.
 */
final class NullSupport {

  private NullSupport() {}

  /**
   * Returns {@code result} if every column in {@code inputs} is non-null, otherwise {@code NULL}.
   */
  @Nonnull
  static Column propagateNull(@Nonnull final Column result, @Nonnull final Column... inputs) {
    Column allNotNull = inputs[0].isNotNull();
    for (int i = 1; i < inputs.length; i++) {
      allNotNull = allNotNull.and(inputs[i].isNotNull());
    }
    return when(allNotNull, result);
  }
}
