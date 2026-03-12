package com.example.fhirpath.codegen.spark.ops;

import static org.apache.spark.sql.functions.lit;
import static org.apache.spark.sql.functions.when;

import com.example.fhirpath.codegen.spark.SparkOperationRegistry;
import org.apache.spark.sql.functions;

/**
 * Collection function registrations (count, exists, empty, first).
 *
 * <p>Uses full registration to access isSingular() from argument IR nodes.
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
        "count",
        (args, nodes, type, gen) -> {
          final var col = args.get(0);
          if (nodes.get(0).isSingular()) {
            return when(col.isNotNull(), lit(1)).otherwise(lit(0));
          } else {
            return when(col.isNotNull(), functions.size(col)).otherwise(lit(0));
          }
        });

    registry.register(
        "exists",
        (args, nodes, type, gen) -> when(args.get(0).isNotNull(), lit(true)).otherwise(lit(false)));

    registry.register(
        "empty",
        (args, nodes, type, gen) -> when(args.get(0).isNull(), lit(true)).otherwise(lit(false)));

    registry.register(
        "first",
        (args, nodes, type, gen) -> {
          final var col = args.get(0);
          if (nodes.get(0).isSingular()) {
            return col;
          } else {
            return functions.get(col, lit(0));
          }
        });
  }
}
