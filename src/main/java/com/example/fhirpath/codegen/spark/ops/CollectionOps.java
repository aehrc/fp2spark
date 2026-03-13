package com.example.fhirpath.codegen.spark.ops;

import static org.apache.spark.sql.functions.lit;
import static org.apache.spark.sql.functions.when;

import com.example.fhirpath.codegen.spark.SparkOperationRegistry;
import java.util.function.Function;
import org.apache.spark.sql.functions;

/**
 * Collection function registrations (count, exists, empty, first, indexer).
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
