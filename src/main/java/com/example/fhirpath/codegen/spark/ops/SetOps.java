package com.example.fhirpath.codegen.spark.ops;

import static org.apache.spark.sql.functions.array_distinct;
import static org.apache.spark.sql.functions.array_except;
import static org.apache.spark.sql.functions.array_intersect;
import static org.apache.spark.sql.functions.array_union;
import static org.apache.spark.sql.functions.lit;

import com.example.fhirpath.codegen.spark.CollectionValue;
import com.example.fhirpath.codegen.spark.SparkOpContext;
import com.example.fhirpath.codegen.spark.SparkOperationRegistry;
import com.example.fhirpath.typing.Type;
import com.example.fhirpath.typing.Types;
import jakarta.annotation.Nonnull;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.functions;

/**
 * Set operation registrations: {@code union}, {@code distinct}, {@code isDistinct}, {@code
 * intersect}, {@code exclude}, {@code subsetOf}, {@code supersetOf}.
 *
 * <p>The {@code union} operation is also used by the {@code |} operator (via {@link
 * com.example.fhirpath.operation.OperatorNormalizer}).
 */
public final class SetOps {

  private SetOps() {}

  /**
   * Registers all set operations into the given registry.
   *
   * @param registry the registry to register operations into
   */
  public static void register(@Nonnull final SparkOperationRegistry registry) {

    // union / | : merge two collections, eliminating duplicates
    registry.register("union", SetOps::generateUnion);

    // distinct(): remove duplicate elements
    // Uses asArray() for uniform handling: singular→[c] or [], then array_distinct, then
    // nullIfEmpty
    registry.register(
        "distinct",
        ctx -> CollectionValue.nullIfEmpty(array_distinct(ctx.collectionArg(0).asArray())));

    // isDistinct(): true if all elements are unique; empty → true
    registry.register(
        "isDistinct",
        ctx ->
            ctx.collectionArg(0)
                .applyNonNull(
                    c -> functions.size(array_distinct(c)).equalTo(functions.size(c)),
                    c -> lit(true),
                    lit(true)));

    // intersect(other): elements in both collections, duplicates eliminated
    registry.register("intersect", SetOps::generateIntersect);

    // exclude(other): elements NOT in other
    registry.register("exclude", SetOps::generateExclude);

    // subsetOf(other): all input items are members of other
    registry.register("subsetOf", ctx -> generateSubsetCheck(ctx, 0, 1));

    // supersetOf(other): all other items are members of input (reversed subsetOf)
    registry.register("supersetOf", ctx -> generateSubsetCheck(ctx, 1, 0));
  }

  @Nonnull
  private static Column generateUnion(@Nonnull final SparkOpContext ctx) {
    final Type leftType = ctx.argType(0);
    final Type rightType = ctx.argType(1);

    if (leftType != Types.NULL && rightType != Types.NULL && !leftType.equals(rightType)) {
      throw new IllegalArgumentException(
          "Union operator (|) requires compatible types, but got "
              + leftType.getName()
              + " and "
              + rightType.getName());
    }

    return CollectionValue.nullIfEmpty(
        array_union(ctx.collectionArg(0).asArray(), ctx.collectionArg(1).asArray()));
  }

  @Nonnull
  private static Column generateIntersect(@Nonnull final SparkOpContext ctx) {
    final Type leftType = ctx.argType(0);
    final Type rightType = ctx.argType(1);

    // Incompatible types: no common elements possible → empty
    if (leftType != Types.NULL && rightType != Types.NULL && !leftType.equals(rightType)) {
      return lit(null);
    }

    return CollectionValue.nullIfEmpty(
        array_intersect(ctx.collectionArg(0).asArray(), ctx.collectionArg(1).asArray()));
  }

  @Nonnull
  private static Column generateExclude(@Nonnull final SparkOpContext ctx) {
    final Type leftType = ctx.argType(0);
    final Type rightType = ctx.argType(1);

    // Incompatible types: nothing to exclude → return input as-is
    if (leftType != Types.NULL && rightType != Types.NULL && !leftType.equals(rightType)) {
      return ctx.collectionArg(0).asArray();
    }

    return CollectionValue.nullIfEmpty(
        array_except(ctx.collectionArg(0).asArray(), ctx.collectionArg(1).asArray()));
  }

  /**
   * Generates a subset check: all elements at {@code subIdx} are members of the collection at
   * {@code superIdx}.
   *
   * <p>Uses {@code array_except}: if {@code array_except(sub, super)} is empty, then sub is a
   * subset of super.
   */
  @Nonnull
  private static Column generateSubsetCheck(
      @Nonnull final SparkOpContext ctx, final int subIdx, final int superIdx) {
    final Type subType = ctx.argType(subIdx);
    final Type superType = ctx.argType(superIdx);

    // Incompatible types: elements can never be members → false
    if (subType != Types.NULL && superType != Types.NULL && !subType.equals(superType)) {
      return lit(false);
    }

    // array_except(sub, super) → elements in sub not in super
    // If empty (size==0), sub is a subset of super
    // Handles: empty sub → [], size==0 → true (correct per spec)
    //          empty super → sub (deduplicated), size>0 → false (correct per spec)
    //          both empty → [], size==0 → true (correct per spec)
    final Column subArr = ctx.collectionArg(subIdx).asArray();
    final Column superArr = ctx.collectionArg(superIdx).asArray();
    return functions.size(array_except(subArr, superArr)).equalTo(lit(0));
  }
}
