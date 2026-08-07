package au.csiro.fhirpath.spark.ops;

import static org.apache.spark.sql.functions.aggregate;
import static org.apache.spark.sql.functions.array;
import static org.apache.spark.sql.functions.array_distinct;
import static org.apache.spark.sql.functions.array_except;
import static org.apache.spark.sql.functions.array_union;
import static org.apache.spark.sql.functions.concat;
import static org.apache.spark.sql.functions.exists;
import static org.apache.spark.sql.functions.filter;
import static org.apache.spark.sql.functions.forall;
import static org.apache.spark.sql.functions.lit;
import static org.apache.spark.sql.functions.not;

import au.csiro.fhirpath.spark.CollectionValue;
import au.csiro.fhirpath.spark.SparkOpContext;
import au.csiro.fhirpath.spark.SparkOperationRegistry;
import au.csiro.fhirpath.spark.SparkTypeMapper;
import au.csiro.fhirpath.typing.SystemType;
import au.csiro.fhirpath.typing.Type;
import au.csiro.fhirpath.typing.Types;
import jakarta.annotation.Nonnull;
import java.util.function.BiFunction;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.functions;
import org.apache.spark.sql.types.ArrayType;
import org.apache.spark.sql.types.DataTypes;

/**
 * Set operation registrations: {@code union}, {@code distinct}, {@code isDistinct}, {@code
 * intersect}, {@code exclude}, {@code subsetOf}, {@code supersetOf}.
 *
 * <p>The {@code union} operation is also used by the {@code |} operator (via {@link
 * au.csiro.fhirpath.operation.OperatorNormalizer}).
 *
 * <p>For types with default SQL equality (primitives), Spark's built-in array functions are used
 * ({@code array_union}, {@code array_distinct}, etc.). For types with custom equality semantics
 * (Quantity, temporal), custom implementations using {@code filter}/{@code exists}/{@code
 * aggregate} with type-aware comparators are used instead.
 *
 * <p>Two operations intentionally deviate from the obvious built-in choice:
 *
 * <ul>
 *   <li>{@code exclude} uses {@code filter(left, !exists-in-right)} rather than {@code
 *       array_except} because the spec (§5.3.9) requires duplicates in the input to be preserved,
 *       while {@code array_except} performs set difference with deduplication.
 *   <li>{@code intersect} uses {@code filter(distinct(left), exists-in-right)} rather than {@code
 *       array_intersect} because the latter returns {@code NULL} when evaluated inside a
 *       higher-order function (e.g. {@code transform} produced by {@code select()}), even for
 *       non-empty intersections.
 * </ul>
 */
public final class SetOps {

  private static final ArrayType DECIMAL_ARRAY_TYPE =
      DataTypes.createArrayType(SparkTypeMapper.DECIMAL_TYPE);

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

  /**
   * Normalizes a DECIMAL array to the canonical {@link SparkTypeMapper#DECIMAL_TYPE} element type.
   *
   * <p>FHIR data columns may use {@code DOUBLE} while codegen literals use {@code DECIMAL(38,6)}.
   * Spark's built-in array functions ({@code array_union}, {@code array_intersect}, etc.) require
   * matching element types, so we cast to the canonical type before calling them.
   */
  @Nonnull
  private static Column normalizeArray(@Nonnull final Column arr, @Nonnull final Type type) {
    if (type == SystemType.DECIMAL) {
      return arr.cast(DECIMAL_ARRAY_TYPE);
    }
    return arr;
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

    final Type type = effectiveType(leftType, rightType);
    final Column leftArr = normalizeArray(ctx.collectionArg(0).asArray(), type);
    final Column rightArr = normalizeArray(ctx.collectionArg(1).asArray(), type);

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

    final Type type = effectiveType(leftType, rightType);
    final Column leftArr = normalizeArray(ctx.collectionArg(0).asArray(), type);
    final Column rightArr = normalizeArray(ctx.collectionArg(1).asArray(), type);

    // Spec §5.3.8: duplicates are eliminated. Distinct the left side first so filter
    // walks each left element at most once. See class javadoc for why array_intersect is
    // not used.
    if (type == Types.NULL || EqualityOps.usesDefaultEquality(type)) {
      return CollectionValue.nullIfEmpty(
          filter(array_distinct(leftArr), elem -> exists(rightArr, x -> x.equalTo(elem))));
    }

    final BiFunction<Column, Column, Column> eq = EqualityOps.equalityForType(type);
    final Column distinctLeft = arrayDistinctWithEquality(leftArr, eq);
    return CollectionValue.nullIfEmpty(
        filter(distinctLeft, elem -> existsWithEquality(rightArr, elem, eq)));
  }

  // ========== exclude ==========

  @Nonnull
  private static Column generateExclude(@Nonnull final SparkOpContext ctx) {
    final Type leftType = ctx.argType(0);
    final Type rightType = ctx.argType(1);

    if (EqualityOps.typesAreIncompatible(leftType, rightType)) {
      return CollectionValue.nullIfEmpty(ctx.collectionArg(0).asArray());
    }

    final Type type = effectiveType(leftType, rightType);
    final Column leftArr = normalizeArray(ctx.collectionArg(0).asArray(), type);
    final Column rightArr = normalizeArray(ctx.collectionArg(1).asArray(), type);

    // Spec §5.3.9: duplicates preserved and order retained. See class javadoc for why
    // array_except is not used.
    final BiFunction<Column, Column, Column> eq =
        (type == Types.NULL || EqualityOps.usesDefaultEquality(type))
            ? Column::equalTo
            : EqualityOps.equalityForType(type);
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

    final Type type = effectiveType(subType, superType);
    final Column subArr = normalizeArray(ctx.collectionArg(subIdx).asArray(), type);
    final Column superArr = normalizeArray(ctx.collectionArg(superIdx).asArray(), type);

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
