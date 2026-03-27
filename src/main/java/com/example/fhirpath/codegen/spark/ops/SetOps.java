package com.example.fhirpath.codegen.spark.ops;

import static org.apache.spark.sql.functions.aggregate;
import static org.apache.spark.sql.functions.array;
import static org.apache.spark.sql.functions.array_distinct;
import static org.apache.spark.sql.functions.array_except;
import static org.apache.spark.sql.functions.array_intersect;
import static org.apache.spark.sql.functions.array_union;
import static org.apache.spark.sql.functions.concat;
import static org.apache.spark.sql.functions.exists;
import static org.apache.spark.sql.functions.filter;
import static org.apache.spark.sql.functions.forall;
import static org.apache.spark.sql.functions.lit;
import static org.apache.spark.sql.functions.not;

import com.example.fhirpath.codegen.spark.CollectionValue;
import com.example.fhirpath.codegen.spark.SparkOpContext;
import com.example.fhirpath.codegen.spark.SparkOperationRegistry;
import com.example.fhirpath.typing.Type;
import com.example.fhirpath.typing.Types;
import jakarta.annotation.Nonnull;
import java.util.function.BiFunction;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.functions;

/**
 * Set operation registrations: {@code union}, {@code distinct}, {@code isDistinct}, {@code
 * intersect}, {@code exclude}, {@code subsetOf}, {@code supersetOf}.
 *
 * <p>The {@code union} operation is also used by the {@code |} operator (via {@link
 * com.example.fhirpath.operation.OperatorNormalizer}).
 *
 * <p>For types with default SQL equality (primitives), Spark's built-in array functions are used
 * ({@code array_union}, {@code array_distinct}, etc.). For types with custom equality semantics
 * (Quantity, temporal), custom implementations using {@code filter}/{@code exists}/{@code
 * aggregate} with type-aware comparators are used instead.
 */
public final class SetOps {

  private SetOps() {}

  /**
   * Registers all set operations into the given registry.
   *
   * @param registry the registry to register operations into
   */
  public static void register(@Nonnull final SparkOperationRegistry registry) {
    registry.register("union", SetOps::generateUnion);
    registry.register("distinct", SetOps::generateDistinct);
    registry.register("isDistinct", SetOps::generateIsDistinct);
    registry.register("intersect", SetOps::generateIntersect);
    registry.register("exclude", SetOps::generateExclude);
    registry.register("subsetOf", ctx -> generateSubsetCheck(ctx, 0, 1));
    registry.register("supersetOf", ctx -> generateSubsetCheck(ctx, 1, 0));
  }

  // ========== Resolve effective element type ==========

  /**
   * Returns the effective element type for a binary set operation. Both args should have the same
   * type after analyzer coercion; if one is NULL (empty collection), returns the other.
   */
  @Nonnull
  private static Type effectiveType(@Nonnull final Type leftType, @Nonnull final Type rightType) {
    return leftType == Types.NULL ? rightType : leftType;
  }

  // ========== distinct / isDistinct ==========

  @Nonnull
  private static Column generateDistinct(@Nonnull final SparkOpContext ctx) {
    final CollectionValue input = ctx.collectionArg(0);

    // Singular value is already distinct — return as-is
    if (input.isSingular()) {
      return input.column();
    }

    final Type type = ctx.argType(0);
    final Column arr = input.column();

    if (type == Types.NULL || EqualityOps.usesDefaultEquality(type)) {
      return CollectionValue.nullIfEmpty(array_distinct(arr));
    }

    return CollectionValue.nullIfEmpty(
        arrayDistinctWithEquality(arr, EqualityOps.equalityForType(type)));
  }

  @Nonnull
  private static Column generateIsDistinct(@Nonnull final SparkOpContext ctx) {
    final Type type = ctx.argType(0);

    if (type == Types.NULL || EqualityOps.usesDefaultEquality(type)) {
      return ctx.collectionArg(0)
          .applyNonNull(
              c -> functions.size(array_distinct(c)).equalTo(functions.size(c)),
              c -> lit(true),
              lit(true));
    }

    // Custom equality: distinct().size == original.size
    final BiFunction<Column, Column, Column> eq = EqualityOps.equalityForType(type);
    return ctx.collectionArg(0)
        .applyNonNull(
            c -> functions.size(arrayDistinctWithEquality(c, eq)).equalTo(functions.size(c)),
            c -> lit(true),
            lit(true));
  }

  // ========== union ==========

  @Nonnull
  private static Column generateUnion(@Nonnull final SparkOpContext ctx) {
    final Type leftType = ctx.argType(0);
    final Type rightType = ctx.argType(1);

    if (EqualityOps.typesAreIncompatible(leftType, rightType)) {
      throw new IllegalArgumentException(
          "Union operator (|) requires compatible types, but got "
              + leftType.getName()
              + " and "
              + rightType.getName());
    }

    final Column leftArr = ctx.collectionArg(0).asArray();
    final Column rightArr = ctx.collectionArg(1).asArray();
    final Type type = effectiveType(leftType, rightType);

    if (type == Types.NULL || EqualityOps.usesDefaultEquality(type)) {
      return CollectionValue.nullIfEmpty(array_union(leftArr, rightArr));
    }

    // concat + distinct with custom equality (Pathling pattern)
    final BiFunction<Column, Column, Column> eq = EqualityOps.equalityForType(type);
    return CollectionValue.nullIfEmpty(arrayDistinctWithEquality(concat(leftArr, rightArr), eq));
  }

  // ========== intersect ==========

  @Nonnull
  private static Column generateIntersect(@Nonnull final SparkOpContext ctx) {
    final Type leftType = ctx.argType(0);
    final Type rightType = ctx.argType(1);

    if (EqualityOps.typesAreIncompatible(leftType, rightType)) {
      return lit(null);
    }

    final Column leftArr = ctx.collectionArg(0).asArray();
    final Column rightArr = ctx.collectionArg(1).asArray();
    final Type type = effectiveType(leftType, rightType);

    if (type == Types.NULL || EqualityOps.usesDefaultEquality(type)) {
      return CollectionValue.nullIfEmpty(array_intersect(leftArr, rightArr));
    }

    // distinct(filter(left, elem -> exists(right, x -> eq(x, elem))))
    final BiFunction<Column, Column, Column> eq = EqualityOps.equalityForType(type);
    final Column filtered = filter(leftArr, elem -> existsWithEquality(rightArr, elem, eq));
    return CollectionValue.nullIfEmpty(arrayDistinctWithEquality(filtered, eq));
  }

  // ========== exclude ==========

  @Nonnull
  private static Column generateExclude(@Nonnull final SparkOpContext ctx) {
    final Type leftType = ctx.argType(0);
    final Type rightType = ctx.argType(1);

    if (EqualityOps.typesAreIncompatible(leftType, rightType)) {
      return CollectionValue.nullIfEmpty(ctx.collectionArg(0).asArray());
    }

    final Column leftArr = ctx.collectionArg(0).asArray();
    final Column rightArr = ctx.collectionArg(1).asArray();
    final Type type = effectiveType(leftType, rightType);

    if (type == Types.NULL || EqualityOps.usesDefaultEquality(type)) {
      return CollectionValue.nullIfEmpty(array_except(leftArr, rightArr));
    }

    // filter(left, elem -> !exists(right, x -> eq(x, elem)))
    final BiFunction<Column, Column, Column> eq = EqualityOps.equalityForType(type);
    return CollectionValue.nullIfEmpty(
        filter(leftArr, elem -> not(existsWithEquality(rightArr, elem, eq))));
  }

  // ========== subsetOf / supersetOf ==========

  @Nonnull
  private static Column generateSubsetCheck(
      @Nonnull final SparkOpContext ctx, final int subIdx, final int superIdx) {
    final Type subType = ctx.argType(subIdx);
    final Type superType = ctx.argType(superIdx);

    if (EqualityOps.typesAreIncompatible(subType, superType)) {
      return lit(false);
    }

    final Column subArr = ctx.collectionArg(subIdx).asArray();
    final Column superArr = ctx.collectionArg(superIdx).asArray();
    final Type type = effectiveType(subType, superType);

    if (type == Types.NULL || EqualityOps.usesDefaultEquality(type)) {
      return functions.size(array_except(subArr, superArr)).equalTo(lit(0));
    }

    // forall(sub, elem -> exists(super, x -> eq(x, elem)))
    final BiFunction<Column, Column, Column> eq = EqualityOps.equalityForType(type);
    return forall(subArr, elem -> existsWithEquality(superArr, elem, eq));
  }

  // ========== Custom equality helpers ==========

  /**
   * Checks if any element in the array matches the target using custom equality. Null equality
   * results are treated as "not equal" (via {@code coalesce(eq, false)}).
   */
  @Nonnull
  private static Column existsWithEquality(
      @Nonnull final Column arr,
      @Nonnull final Column target,
      @Nonnull final BiFunction<Column, Column, Column> eq) {
    return exists(arr, x -> functions.coalesce(eq.apply(x, target), lit(false)));
  }

  /**
   * Removes duplicate elements using custom equality (Pathling pattern).
   *
   * <p>Uses {@code aggregate()} to fold over the array, appending each element to the accumulator
   * only if it doesn't already exist (checked via {@code exists()} with the custom comparator).
   */
  @Nonnull
  private static Column arrayDistinctWithEquality(
      @Nonnull final Column arr, @Nonnull final BiFunction<Column, Column, Column> eq) {
    // Start with an empty array of the same type (filter with false keeps the type but removes all)
    final Column emptyTypedArray = filter(arr, x -> lit(false));
    return aggregate(
        arr,
        emptyTypedArray,
        (acc, elem) ->
            functions
                .when(not(existsWithEquality(acc, elem, eq)), concat(acc, array(elem)))
                .otherwise(acc));
  }
}
