package com.example.fhirpath.codegen.spark;

import static org.apache.spark.sql.functions.lit;
import static org.apache.spark.sql.functions.when;

import jakarta.annotation.Nonnull;
import java.util.function.Function;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.functions;

/**
 * Wraps a Spark Column with cardinality metadata for FHIRPath collection handling.
 *
 * <p>In FHIRPath, collections can have different cardinalities (single vs many elements). This
 * record provides utilities to apply different operations based on whether a column represents a
 * singular value or an array, enabling proper handling of both cases.
 *
 * @param column The Spark SQL column containing the value
 * @param isSingular Whether the column represents a singular value (true) or an array (false)
 */
public record CollectionValue(Column column, boolean isSingular) {
  /**
   * Apply different functions based on whether the column is singular or an array.
   *
   * @param arrayFunction Function to apply if the column is an array
   * @param singleFunction Function to apply if the column is singular
   * @return The resulting column after applying the appropriate function
   */
  @Nonnull
  public Column apply(
      final Function<Column, Column> arrayFunction, final Function<Column, Column> singleFunction) {
    return isSingular ? singleFunction.apply(column) : arrayFunction.apply(column);
  }

  /**
   * Apply different functions based on whether the column is singular or an array, but only if the
   * column is non-null. If the column is null, return the provided default value.
   *
   * @param arrayFunction Function to apply if the column is an array
   * @param singleFunction Function to apply if the column is singular
   * @param defaultValue Default value to return if the column is null
   * @return The resulting column after applying the appropriate function or the default value
   */
  @Nonnull
  public Column applyNonNull(
      final Function<Column, Column> arrayFunction,
      final Function<Column, Column> singleFunction,
      final Column defaultValue) {
    return when(column.isNotNull(), apply(arrayFunction, singleFunction)).otherwise(defaultValue);
  }

  /**
   * Converts this column to an array, wrapping singular values in a single-element array.
   *
   * @return the column as an array, or an empty array if null
   */
  @Nonnull
  public Column asArray() {
    return applyNonNull(Function.identity(), functions::array, functions.array());
  }

  /**
   * Applies an element-wise transformation. For arrays, uses Spark's {@code transform()}. For
   * singular values, applies the function directly. The result preserves the original cardinality.
   *
   * @param fn the function to apply to each element
   * @return a new CollectionValue with the transformation applied
   */
  @Nonnull
  public CollectionValue map(@Nonnull final Function<Column, Column> fn) {
    final Column result = isSingular ? fn.apply(column) : functions.transform(column, fn::apply);
    return new CollectionValue(result, isSingular);
  }

  /**
   * Removes null elements. For arrays, uses Spark's {@code filter(isNotNull)}. For singular values,
   * this is a no-op (null propagation is handled elsewhere).
   *
   * @return a new CollectionValue with nulls removed
   */
  @Nonnull
  public CollectionValue filterNulls() {
    if (isSingular) {
      return this;
    }
    return new CollectionValue(functions.filter(column, Column::isNotNull), false);
  }

  /**
   * Flattens nested arrays. For arrays, uses Spark's {@code flatten()}. For singular values, this
   * is a no-op since there is nothing to flatten.
   *
   * @return a new CollectionValue with nested arrays flattened
   */
  @Nonnull
  public CollectionValue flatten() {
    if (isSingular) {
      return this;
    }
    return new CollectionValue(functions.flatten(column), false);
  }

  /**
   * Returns null if the array column is empty; otherwise returns the array as-is. This preserves
   * FHIRPath empty collection semantics where an empty collection is represented as null.
   *
   * @param array the array column to check
   * @return null if the array is empty, otherwise the array
   */
  @Nonnull
  public static Column nullIfEmpty(@Nonnull final Column array) {
    return when(functions.size(array).gt(lit(0)), array);
  }

  /**
   * Create a function that always returns a constant value as a literal column.
   *
   * @param constValue The constant value to return
   * @return A function that takes a Column (ignored) and returns a literal Column with the constant
   *     value
   */
  @Nonnull
  public static Function<Column, Column> cons(final Object constValue) {
    return ign -> lit(constValue);
  }
}
