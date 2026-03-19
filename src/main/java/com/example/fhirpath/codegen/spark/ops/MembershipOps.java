package com.example.fhirpath.codegen.spark.ops;

import static org.apache.spark.sql.functions.exists;
import static org.apache.spark.sql.functions.when;

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
 * Membership operator registrations ({@code in} and {@code contains}).
 *
 * <p>Uses equality semantics (not equivalence) per the FHIRPath spec. Type-aware equality is used
 * for Quantity (struct-aware) and temporal types (precision-aware), matching the behavior of the
 * {@code =} operator.
 *
 * <p>Handles three cases:
 *
 * <ul>
 *   <li>NULL type (empty element): returns {@code null} (empty collection)
 *   <li>NULL type (empty collection): returns {@code false}
 *   <li>Incompatible types: returns {@code false}
 *   <li>Compatible types: uses {@code exists()} with type-aware equality comparator
 * </ul>
 */
public final class MembershipOps {

  private MembershipOps() {}

  /**
   * Registers membership operators into the given registry.
   *
   * @param registry the registry to register operations into
   */
  public static void register(@Nonnull final SparkOperationRegistry registry) {
    // in: element(arg0) in collection(arg1)
    registry.register("in", ctx -> generateMembership(ctx, 0, 1));
    // memberContains: collection(arg0) contains element(arg1)
    registry.register("memberContains", ctx -> generateMembership(ctx, 1, 0));
  }

  private static Column generateMembership(
      final SparkOpContext ctx, final int elementIdx, final int collectionIdx) {

    final Type elementType = ctx.argType(elementIdx);
    final Type collectionType = ctx.argType(collectionIdx);

    // Empty element → empty result per spec
    if (elementType == Types.NULL) {
      return functions.lit(null);
    }

    // Empty collection → false per spec
    if (collectionType == Types.NULL) {
      return functions.lit(false);
    }

    // Incompatible types (ANY,ANY fallback) → false
    if (!elementType.equals(collectionType)) {
      return functions.lit(false);
    }

    final Column element = ctx.arg(elementIdx);
    final CollectionValue collection = ctx.collectionArg(collectionIdx);

    // Convert collection to array (wraps singular in array if needed, null → empty array)
    final Column collectionArray = collection.asArray();

    // Build type-aware equality comparator
    final BiFunction<Column, Column, Column> equalsFn = equalityForType(elementType);

    // exists(collection, e -> equals(e, element))
    // When element is null at runtime, return null (empty collection semantics)
    final Column result = exists(collectionArray, e -> equalsFn.apply(e, element));
    return when(element.isNotNull(), result);
  }

  @Nonnull
  private static BiFunction<Column, Column, Column> equalityForType(@Nonnull final Type type) {
    if (type == Types.QUANTITY) {
      return QuantityOps::quantityEquals;
    }
    if (TemporalOps.isTemporalType(type)) {
      return TemporalOps::temporalEquals;
    }
    return Column::equalTo;
  }
}
