package com.example.fhirpath.codegen.spark.ops;

import static org.apache.spark.sql.functions.concat;

import com.example.fhirpath.codegen.spark.SparkOpContext;
import com.example.fhirpath.codegen.spark.SparkOperationRegistry;
import com.example.fhirpath.typing.Type;
import jakarta.annotation.Nonnull;
import org.apache.spark.sql.Column;

/**
 * Combine operator registration ({@code ;}).
 *
 * <p>Handles three cases:
 *
 * <ul>
 *   <li>NULL type (empty collection): combine with empty-array coalescing
 *   <li>Incompatible types (ANY,ANY fallback, types differ): throws error (no common type)
 *   <li>Compatible types: ordered concatenation via {@code concat}
 * </ul>
 */
public final class CombineOps {

  private CombineOps() {}

  /**
   * Registers the combine operator into the given registry.
   *
   * @param registry the registry to register operations into
   */
  public static void register(@Nonnull final SparkOperationRegistry registry) {
    registry.register("combine", CombineOps::generateCombine);
  }

  @Nonnull
  private static Column generateCombine(@Nonnull final SparkOpContext ctx) {

    final Type leftType = ctx.argType(0);
    final Type rightType = ctx.argType(1);

    // Incompatible types (ANY,ANY fallback): throw error — no common element type
    // After analyzer coercion, compatible types always have equal types.
    // NULL is compatible with anything (handled below).
    if (EqualityOps.typesAreIncompatible(leftType, rightType)) {
      throw new IllegalArgumentException(
          "Combine operator (;) requires compatible types, but got "
              + leftType.getName()
              + " and "
              + rightType.getName());
    }

    // asArray() handles: singular→wrap in array, null→empty array, array null→empty array
    return concat(ctx.collectionArg(0).asArray(), ctx.collectionArg(1).asArray());
  }
}
