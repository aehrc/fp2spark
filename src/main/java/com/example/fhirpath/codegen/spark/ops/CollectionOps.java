package com.example.fhirpath.codegen.spark.ops;

import static org.apache.spark.sql.functions.lit;
import static org.apache.spark.sql.functions.when;

import com.example.fhirpath.codegen.spark.SparkOperationRegistry;
import java.util.function.Function;
import org.apache.spark.sql.functions;

/**
 * Collection function registrations (count, exists, empty, first, last, tail, skip, take, single,
 * indexer).
 *
 * <p>Uses {@code collectionArg()} from the operation context to handle singular vs array
 * cardinality.
 */
public final class CollectionOps {

  private CollectionOps() {}

  /**
   * Registers all collection functions into the given registry.
   *
   * @param registry the registry to register operations into
   */
  public static void register(final SparkOperationRegistry registry) {
    registry.register(
        "count", ctx -> ctx.collectionArg(0).applyNonNull(functions::size, c -> lit(1), lit(0)));

    registry.register(
        "exists", ctx -> when(ctx.arg(0).isNotNull(), lit(true)).otherwise(lit(false)));

    registry.register("empty", ctx -> when(ctx.arg(0).isNull(), lit(true)).otherwise(lit(false)));

    registry.register(
        "first",
        ctx -> ctx.collectionArg(0).apply(c -> functions.get(c, lit(0)), Function.identity()));

    // last(): array → last element, singular → identity
    registry.register(
        "last",
        ctx ->
            ctx.collectionArg(0)
                .apply(
                    c -> functions.get(c, functions.size(c).minus(lit(1))), Function.identity()));

    // tail(): array → all but first (slice from index 2, 1-based), singular → empty
    registry.register(
        "tail",
        ctx ->
            ctx.collectionArg(0)
                .apply(c -> functions.slice(c, lit(2), functions.size(c)), c -> lit(null)));

    // skip(n): array → skip first n elements, singular → n<=0 returns as array, else empty
    // Guard: Spark slice() requires start != 0; when n<=0 return array unchanged
    registry.register(
        "skip",
        ctx -> {
          final var n = ctx.arg(1);
          return ctx.collectionArg(0)
              .apply(
                  c ->
                      when(n.leq(lit(0)), c)
                          .otherwise(functions.slice(c, n.plus(lit(1)), functions.size(c))),
                  c -> when(c.isNull().or(n.gt(lit(0))), lit(null)).otherwise(functions.array(c)));
        });

    // take(n): array → first n elements, singular → n>=1 returns as array, else empty
    // Guard: Spark slice() requires length >= 0; when n<=0 return empty
    registry.register(
        "take",
        ctx -> {
          final var n = ctx.arg(1);
          return ctx.collectionArg(0)
              .apply(
                  c -> when(n.leq(lit(0)), lit(null)).otherwise(functions.slice(c, lit(1), n)),
                  c -> when(c.isNull().or(n.leq(lit(0))), lit(null)).otherwise(functions.array(c)));
        });

    // single(): array → element if size=1, error if >1, empty if size=0; singular → identity
    registry.register(
        "single",
        ctx ->
            ctx.collectionArg(0)
                .apply(
                    c -> {
                      final var sz = functions.size(c);
                      return when(sz.equalTo(lit(1)), functions.get(c, lit(0)))
                          .when(
                              sz.gt(lit(1)),
                              functions.raise_error(
                                  lit("single() expected one element but found multiple")))
                          .otherwise(lit(null));
                    },
                    Function.identity()));

    registry.register(
        "indexer",
        ctx -> {
          final var index = ctx.arg(1);
          return ctx.collectionArg(0)
              .apply(
                  // Collection: use Spark's get() (0-based, returns null for out-of-bounds)
                  // Guard against negative indices: Spark's get() indexes from end for negatives
                  col -> when(index.lt(lit(0)), lit(null)).otherwise(functions.get(col, index)),
                  // Singular value: only index 0 returns the value
                  col -> when(index.equalTo(lit(0)), col).otherwise(lit(null)));
        });
  }
}
