/*
 * Copyright © 2025-2026 Commonwealth Scientific and Industrial Research
 * Organisation (CSIRO) ABN 41 687 119 230.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package au.csiro.fhirpath.spark.ops;

import static org.apache.spark.sql.functions.exists;
import static org.apache.spark.sql.functions.when;

import au.csiro.fhirpath.spark.CollectionValue;
import au.csiro.fhirpath.spark.SparkOpContext;
import au.csiro.fhirpath.spark.SparkOperationRegistry;
import au.csiro.fhirpath.typing.Type;
import au.csiro.fhirpath.typing.Types;
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
 * <p>Handles four cases:
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

    // Build type-aware equality comparator
    final BiFunction<Column, Column, Column> equalsFn = EqualityOps.equalityForType(elementType);

    // For arrays: exists(array, e -> equals(e, element))
    // For singular: direct equality comparison (avoids unnecessary array wrapping)
    // Null collection at runtime → false (empty collection returns false per spec)
    final Column result =
        collection.applyNonNull(
            arr -> exists(arr, e -> equalsFn.apply(e, element)),
            col -> equalsFn.apply(col, element),
            functions.lit(false));

    // When element is null at runtime, return null (empty collection semantics)
    return when(element.isNotNull(), result).otherwise(functions.lit(null));
  }
}
