package com.example.fhirpath.codegen.spark.ops;

import static org.apache.spark.sql.functions.coalesce;
import static org.apache.spark.sql.functions.forall;
import static org.apache.spark.sql.functions.lit;
import static org.apache.spark.sql.functions.when;
import static org.apache.spark.sql.functions.zip_with;

import com.example.fhirpath.codegen.spark.CollectionValue;
import com.example.fhirpath.codegen.spark.SparkOpContext;
import com.example.fhirpath.codegen.spark.SparkOperationRegistry;
import com.example.fhirpath.typing.Type;
import com.example.fhirpath.typing.Types;
import jakarta.annotation.Nonnull;
import java.util.function.BiFunction;
import java.util.function.UnaryOperator;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.functions;

/**
 * Equality operator registrations ({@code =} and {@code !=}).
 *
 * <p>Handles three cases:
 *
 * <ul>
 *   <li>NULL type (empty collection): short-circuits to {@code lit(null)}
 *   <li>Incompatible types (ANY,ANY fallback): returns {@code lit(false)} / {@code lit(true)}
 *   <li>Compatible types: scalar or array comparison via {@code equalTo}
 * </ul>
 *
 * <p>For types with custom equality semantics (Quantity, temporal), both singular and collection
 * comparisons use type-aware comparators. Collection comparison uses {@code zip_with} for pairwise
 * element comparison.
 */
public final class EqualityOps {

  private EqualityOps() {}

  /**
   * Registers equality operators into the given registry.
   *
   * @param registry the registry to register operations into
   */
  public static void register(@Nonnull final SparkOperationRegistry registry) {
    registry.register("equals", ctx -> generateEquality(ctx, false));

    registry.register("notEquals", ctx -> generateEquality(ctx, true));
  }

  private static Column generateEquality(final SparkOpContext ctx, final boolean negate) {

    final Type leftType = ctx.argType(0);
    final Type rightType = ctx.argType(1);

    // Empty collection: equality with {} always returns {} (null)
    if (leftType == Types.NULL || rightType == Types.NULL) {
      return functions.lit(null);
    }

    // Incompatible types (ANY,ANY fallback): = returns false, != returns true
    // After analyzer coercion, compatible types always have equal types
    if (!leftType.equals(rightType)) {
      return functions.lit(negate);
    }

    final CollectionValue left = ctx.collectionArg(0);
    final CollectionValue right = ctx.collectionArg(1);

    final Column result;
    if (usesDefaultEquality(leftType)) {
      // Default SQL equality: use Spark's equalTo directly
      if (left.isSingular() && right.isSingular()) {
        result = left.column().equalTo(right.column());
      } else {
        final Column leftArray =
            left.apply(UnaryOperator.identity(), c -> when(c.isNotNull(), functions.array(c)));
        final Column rightArray =
            right.apply(UnaryOperator.identity(), c -> when(c.isNotNull(), functions.array(c)));
        result = leftArray.equalTo(rightArray);
      }
    } else {
      // Custom equality (Quantity, temporal): use type-aware comparator
      final BiFunction<Column, Column, Column> eq = equalityForType(leftType);
      if (left.isSingular() && right.isSingular()) {
        result = eq.apply(left.column(), right.column());
      } else {
        result = collectionEqualWithCustomEquality(left, right, eq);
      }
    }

    return negate ? functions.not(result) : result;
  }

  /**
   * Compares two collections element-by-element using a custom equality comparator.
   *
   * <p>Returns {@code false} if sizes differ. For same-size collections, uses {@code zip_with} for
   * pairwise comparison. Per the FHIRPath spec, for multi-element collections "each item must be
   * equal, otherwise equals returns false" — element-level null (incomparable) is treated as not
   * equal, resulting in {@code false} (not null).
   */
  @Nonnull
  private static Column collectionEqualWithCustomEquality(
      @Nonnull final CollectionValue left,
      @Nonnull final CollectionValue right,
      @Nonnull final BiFunction<Column, Column, Column> eq) {

    final Column leftArray =
        left.apply(UnaryOperator.identity(), c -> when(c.isNotNull(), functions.array(c)));
    final Column rightArray =
        right.apply(UnaryOperator.identity(), c -> when(c.isNotNull(), functions.array(c)));

    final Column sameSize = functions.size(leftArray).equalTo(functions.size(rightArray));

    // zip_with produces array<boolean?> of pairwise comparison results
    final Column pairResults = zip_with(leftArray, rightArray, eq::apply);

    // forall returns null when all non-null elements match but some are null;
    // coalesce collapses that to false per the spec (incomparable elements → not equal).
    final Column allMatch = coalesce(forall(pairResults, x -> x), lit(false));

    // Different sizes → false; same size → check elements
    return when(sameSize, allMatch).otherwise(lit(false));
  }

  /**
   * Returns whether two non-NULL types are incompatible for equality-based operations.
   *
   * <p>After analyzer coercion, compatible types always have equal types. If either type is NULL
   * (empty collection), the types are always compatible. This check is used by set operations
   * (union, intersect, exclude, subsetOf/supersetOf) and combine to detect the ANY,ANY fallback
   * case.
   *
   * @param leftType the left operand type
   * @param rightType the right operand type
   * @return true if both types are non-NULL and not equal
   */
  static boolean typesAreIncompatible(@Nonnull final Type leftType, @Nonnull final Type rightType) {
    return leftType != Types.NULL && rightType != Types.NULL && !leftType.equals(rightType);
  }

  /**
   * Returns a type-aware equality comparator for the given FHIRPath type.
   *
   * <p>Quantity uses struct-aware equality (system+code+value). Temporal types use precision-aware
   * equality. All other types use Spark's default {@code equalTo}.
   *
   * @param type the FHIRPath type
   * @return a binary function producing a Boolean column from two input columns
   */
  @Nonnull
  static BiFunction<Column, Column, Column> equalityForType(@Nonnull final Type type) {
    if (type == Types.QUANTITY) {
      return QuantitySupport::quantityEquals;
    }
    if (type == Types.CODING) {
      return CodingSupport::codingEquals;
    }
    if (TemporalSupport.isTemporalType(type)) {
      return TemporalSupport::temporalEquals;
    }
    return Column::equalTo;
  }

  /**
   * Returns whether the given type uses Spark's default SQL equality for array operations.
   *
   * <p>Types with custom equality semantics (Quantity, temporal) require custom array set
   * operations instead of Spark's built-in {@code array_union}, {@code array_distinct}, etc.
   *
   * @param type the FHIRPath type
   * @return true if Spark's built-in array functions use correct equality for this type
   */
  static boolean usesDefaultEquality(@Nonnull final Type type) {
    return type != Types.QUANTITY && type != Types.CODING && !TemporalSupport.isTemporalType(type);
  }
}
