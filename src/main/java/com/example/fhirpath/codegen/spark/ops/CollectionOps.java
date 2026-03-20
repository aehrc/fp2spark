package com.example.fhirpath.codegen.spark.ops;

import static com.example.fhirpath.codegen.spark.SparkDefs.collectionUnary;
import static org.apache.spark.sql.functions.lit;
import static org.apache.spark.sql.functions.when;

import com.example.fhirpath.codegen.spark.SparkOpContext;
import com.example.fhirpath.codegen.spark.SparkOperationRegistry;
import jakarta.annotation.Nonnull;
import java.util.function.UnaryOperator;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.functions;

/**
 * Collection function registrations (count, exists, empty, first, last, tail, skip, take, single,
 * indexer).
 *
 * <p>Uses {@link com.example.fhirpath.codegen.spark.SparkDefs#collectionUnary collectionUnary} for
 * simple cardinality-dispatched operations, and direct {@code ctx.collectionArg(0).apply()} for
 * operations that need additional arguments.
 */
public final class CollectionOps {

  private CollectionOps() {}

  /**
   * Registers all collection functions into the given registry.
   *
   * @param registry the registry to register operations into
   */
  public static void register(final SparkOperationRegistry registry) {
    registry.register("count", collectionUnary(functions::size, c -> lit(1), lit(0)));

    // exists/empty operate on the null-as-empty encoding directly, without cardinality dispatch.
    registry.register(
        "exists", ctx -> when(ctx.arg(0).isNotNull(), lit(true)).otherwise(lit(false)));
    registry.register("empty", ctx -> when(ctx.arg(0).isNull(), lit(true)).otherwise(lit(false)));

    registry.register(
        "first", collectionUnary(c -> functions.get(c, lit(0)), UnaryOperator.identity()));
    registry.register(
        "last",
        collectionUnary(
            c -> functions.get(c, functions.size(c).minus(lit(1))), UnaryOperator.identity()));
    registry.register(
        "tail",
        collectionUnary(c -> functions.slice(c, lit(2), functions.size(c)), c -> lit(null)));
    registry.register(
        "single",
        collectionUnary(
            c -> {
              final var sz = functions.size(c);
              return when(sz.equalTo(lit(1)), functions.get(c, lit(0)))
                  .when(
                      sz.gt(lit(1)),
                      functions.raise_error(
                          lit("single() expected one element but found multiple")))
                  .otherwise(lit(null));
            },
            UnaryOperator.identity()));

    registry.register("skip", CollectionOps::generateSkip);
    registry.register("take", CollectionOps::generateTake);
    registry.register("indexer", CollectionOps::generateIndexer);
  }

  /** skip(n): array → skip first n elements, singular → n<=0 returns as array, else empty. */
  @Nonnull
  private static Column generateSkip(@Nonnull final SparkOpContext ctx) {
    final var n = ctx.arg(1);
    return ctx.collectionArg(0)
        .apply(
            // Guard: Spark slice() requires start != 0; when n<=0 return array unchanged.
            c ->
                when(n.leq(lit(0)), c)
                    .otherwise(functions.slice(c, n.plus(lit(1)), functions.size(c))),
            c -> when(c.isNull().or(n.gt(lit(0))), lit(null)).otherwise(functions.array(c)));
  }

  /** take(n): array → first n elements, singular → n>=1 returns as array, else empty. */
  @Nonnull
  private static Column generateTake(@Nonnull final SparkOpContext ctx) {
    final var n = ctx.arg(1);
    return ctx.collectionArg(0)
        .apply(
            // Guard: Spark slice() requires length >= 0; when n<=0 return empty.
            c -> when(n.leq(lit(0)), lit(null)).otherwise(functions.slice(c, lit(1), n)),
            c -> when(c.isNull().or(n.leq(lit(0))), lit(null)).otherwise(functions.array(c)));
  }

  /** indexer ([]): collection → element at index, singular → only index 0 returns value. */
  @Nonnull
  private static Column generateIndexer(@Nonnull final SparkOpContext ctx) {
    final var index = ctx.arg(1);
    return ctx.collectionArg(0)
        .apply(
            // Guard against negative indices: Spark's get() indexes from end for negatives.
            col -> when(index.lt(lit(0)), lit(null)).otherwise(functions.get(col, index)),
            col -> when(index.equalTo(lit(0)), col).otherwise(lit(null)));
  }
}
