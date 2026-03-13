package com.example.fhirpath.codegen.spark.ops;

import static org.apache.spark.sql.functions.when;

import com.example.fhirpath.codegen.spark.CollectionValue;
import com.example.fhirpath.codegen.spark.SparkOpContext;
import com.example.fhirpath.codegen.spark.SparkOperationRegistry;
import com.example.fhirpath.typing.PrimitiveType;
import com.example.fhirpath.typing.Type;
import com.example.fhirpath.typing.Types;
import jakarta.annotation.Nonnull;
import java.util.function.Function;
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

    // Temporal types: precision-aware equality via TemporalOps
    if (leftType instanceof PrimitiveType pt && TemporalOps.isTemporalType(pt)) {
      final Column eq = TemporalOps.temporalEquals(ctx.arg(0), ctx.arg(1));
      return negate ? functions.not(eq) : eq;
    }

    final CollectionValue left = ctx.collectionArg(0);
    final CollectionValue right = ctx.collectionArg(1);

    // Normalize cardinality:
    // - Both singular: compare directly (scalar = scalar), null propagates naturally
    // - Mixed or both plural: normalize to arrays (array = array)
    //   When wrapping singular to array, preserve null semantics:
    //   null → null (not array(null)) so that empty collection equality returns empty
    final Column result;
    if (left.isSingular() && right.isSingular()) {
      result = left.column().equalTo(right.column());
    } else {
      final Column leftArray =
          left.apply(Function.identity(), c -> when(c.isNotNull(), functions.array(c)));
      final Column rightArray =
          right.apply(Function.identity(), c -> when(c.isNotNull(), functions.array(c)));
      result = leftArray.equalTo(rightArray);
    }

    return negate ? functions.not(result) : result;
  }
}
