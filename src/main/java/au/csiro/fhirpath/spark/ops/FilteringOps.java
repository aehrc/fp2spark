package au.csiro.fhirpath.spark.ops;

import static org.apache.spark.sql.functions.when;

import au.csiro.fhirpath.spark.CollectionValue;
import au.csiro.fhirpath.spark.SparkOperationRegistry;
import java.util.function.UnaryOperator;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.functions;

/**
 * Filtering, projection, and conditional operation registrations (where, select, iif).
 *
 * <p>Lambda evaluation uses {@code ctx.lambdaEvaluator()} to bind {@code $this} and evaluate the
 * lambda body. Singular/plural dispatch is handled via {@link CollectionValue}.
 */
public final class FilteringOps {

  private FilteringOps() {}

  /**
   * Registers all filtering and conditional operators into the given registry.
   *
   * @param registry the registry to register operations into
   */
  public static void register(final SparkOperationRegistry registry) {
    registry.register("where", ctx -> evaluateWhere(ctx.collectionArg(0), ctx.lambdaEvaluator(1)));

    registry.register(
        "select",
        ctx ->
            evaluateSelect(
                ctx.collectionArg(0),
                ctx.lambdaEvaluator(1),
                ctx.lambdaArg(1).body().isSingular()));

    registry.register(
        "iif", ctx -> evaluateIif(ctx.arg(0), ctx.lambdaEvaluator(1), ctx.lambdaEvaluator(2)));
  }

  private static Column evaluateWhere(
      final CollectionValue collection, final UnaryOperator<Column> criteria) {
    return collection.apply(
        // Collection: use Spark's filter function
        c -> CollectionValue.nullIfEmpty(functions.filter(c, criteria::apply)),
        // Singular value: return the value if criteria matches, NULL otherwise
        c -> when(criteria.apply(c), c));
  }

  private static Column evaluateSelect(
      final CollectionValue collection,
      final UnaryOperator<Column> projection,
      final boolean lambdaReturnsSingular) {
    return collection.apply(
        c -> {
          // Collection: use Spark's transform to evaluate lambda for each element
          final Column transformed = functions.transform(c, projection::apply);
          final Column result;
          if (lambdaReturnsSingular) {
            // Lambda returns singular: transform gives array of values, filter out nulls
            result = functions.filter(transformed, Column::isNotNull);
          } else {
            // Lambda returns MANY: transform gives array of arrays. Drop NULL sub-arrays
            // (empty FHIRPath collections are encoded as NULL by nullIfEmpty) before
            // flattening — Spark's flatten() returns NULL if any sub-array is NULL.
            final Column nonNullArrays = functions.filter(transformed, Column::isNotNull);
            result = functions.filter(functions.flatten(nonNullArrays), Column::isNotNull);
          }
          return CollectionValue.nullIfEmpty(result);
        },
        // Singular value: evaluate lambda with the value as $this
        // If input is null, result is null (empty collection propagation)
        c -> when(c.isNotNull(), projection.apply(c)));
  }

  private static Column evaluateIif(
      final Column collection,
      final UnaryOperator<Column> criterion,
      final UnaryOperator<Column> trueResult) {
    // Runtime short-circuit via Spark's when()
    return when(criterion.apply(collection), trueResult.apply(collection));
  }
}
