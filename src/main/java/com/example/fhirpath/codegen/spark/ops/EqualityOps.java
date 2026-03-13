package com.example.fhirpath.codegen.spark.ops;

import static org.apache.spark.sql.functions.when;

import com.example.fhirpath.codegen.spark.SparkOperationRegistry;
import com.example.fhirpath.typing.PrimitiveType;
import com.example.fhirpath.typing.Type;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.functions;

/**
 * Equality operator registrations ({@code =} and {@code !=}).
 *
 * <p>Handles three cases:
 *
 * <ul>
 *   <li>Incompatible types (ANY,ANY fallback): returns {@code lit(false)} / {@code lit(true)}
 *   <li>Both singular: direct scalar comparison via {@code equalTo}
 *   <li>Mixed or both plural: normalize to arrays then compare via {@code equalTo}
 * </ul>
 */
public final class EqualityOps {

  private EqualityOps() {}

  /**
   * Registers equality operators into the given registry.
   *
   * @param registry the registry to register operations into
   */
  public static void register(final SparkOperationRegistry registry) {
    registry.register("equals", (args, nodes, type, gen) -> generateEquality(args, nodes, false));

    registry.register("notEquals", (args, nodes, type, gen) -> generateEquality(args, nodes, true));
  }

  private static Column generateEquality(
      final java.util.List<Column> args,
      final java.util.List<com.example.fhirpath.ir.IRNode> nodes,
      final boolean negate) {

    final Type leftType = nodes.get(0).getType();
    final Type rightType = nodes.get(1).getType();

    // Incompatible types: = returns false, != returns true
    if (!typesCompatible(leftType, rightType)) {
      return functions.lit(negate);
    }

    final Column left = args.get(0);
    final Column right = args.get(1);
    final boolean leftSingular = nodes.get(0).isSingular();
    final boolean rightSingular = nodes.get(1).isSingular();

    // Normalize cardinality:
    // - Both singular: compare directly (scalar = scalar), null propagates naturally
    // - Mixed or both plural: normalize to arrays (array = array)
    //   When wrapping singular to array, preserve null semantics:
    //   null → null (not array(null)) so that empty collection equality returns empty
    final Column result;
    if (leftSingular && rightSingular) {
      result = left.equalTo(right);
    } else {
      final Column leftArray = leftSingular ? when(left.isNotNull(), functions.array(left)) : left;
      final Column rightArray =
          rightSingular ? when(right.isNotNull(), functions.array(right)) : right;
      result = leftArray.equalTo(rightArray);
    }

    return negate ? functions.not(result) : result;
  }

  /**
   * Checks if two types are compatible for equality comparison. Types are compatible if they are
   * the same, or both are numeric (INTEGER/DECIMAL).
   */
  private static boolean typesCompatible(final Type leftType, final Type rightType) {
    if (leftType == rightType) {
      return true;
    }
    if (leftType instanceof PrimitiveType left && rightType instanceof PrimitiveType right) {
      return isNumeric(left) && isNumeric(right);
    }
    return false;
  }

  private static boolean isNumeric(final PrimitiveType type) {
    return type == PrimitiveType.INTEGER || type == PrimitiveType.DECIMAL;
  }
}
