package com.example.fhirpath.codegen.spark.ops;

import static org.apache.spark.sql.functions.coalesce;
import static org.apache.spark.sql.functions.concat;
import static org.apache.spark.sql.functions.when;

import com.example.fhirpath.codegen.spark.SparkOperationRegistry;
import com.example.fhirpath.ir.IRNode;
import com.example.fhirpath.typing.Type;
import com.example.fhirpath.typing.Types;
import jakarta.annotation.Nonnull;
import java.util.List;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.functions;

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
    registry.register("combine", (args, nodes, type, gen) -> generateCombine(args, nodes));
  }

  @Nonnull
  private static Column generateCombine(
      @Nonnull final List<Column> args, @Nonnull final List<IRNode> nodes) {

    final Type leftType = nodes.get(0).getType();
    final Type rightType = nodes.get(1).getType();

    // Incompatible types (ANY,ANY fallback): throw error — no common element type
    // After analyzer coercion, compatible types always have equal types.
    // NULL is compatible with anything (handled below).
    if (leftType != Types.NULL && rightType != Types.NULL && !leftType.equals(rightType)) {
      throw new IllegalArgumentException(
          "Combine operator (;) requires compatible types, but got "
              + leftType.getName()
              + " and "
              + rightType.getName());
    }

    final Column left = args.get(0);
    final Column right = args.get(1);

    // Convert singular values to arrays; NULL stays NULL (coalesced to empty array below)
    final Column leftArray =
        nodes.get(0).isSingular() ? when(left.isNotNull(), functions.array(left)) : left;
    final Column rightArray =
        nodes.get(1).isSingular() ? when(right.isNotNull(), functions.array(right)) : right;

    // concat with coalesce: NULL arrays become empty arrays for proper concatenation
    return concat(coalesce(leftArray, functions.array()), coalesce(rightArray, functions.array()));
  }
}
