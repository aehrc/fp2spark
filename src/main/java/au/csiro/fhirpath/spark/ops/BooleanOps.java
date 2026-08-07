package au.csiro.fhirpath.spark.ops;

import static au.csiro.fhirpath.spark.SparkDefs.binary;
import static au.csiro.fhirpath.spark.SparkDefs.collectionUnary;
import static au.csiro.fhirpath.spark.SparkDefs.unary;
import static org.apache.spark.sql.functions.coalesce;
import static org.apache.spark.sql.functions.lit;

import au.csiro.fhirpath.spark.CollectionValue;
import au.csiro.fhirpath.spark.SparkOperationDef;
import au.csiro.fhirpath.spark.SparkOperationRegistry;
import java.util.function.UnaryOperator;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.functions;

/**
 * Boolean operator and boolean collection function registrations.
 *
 * <p>Boolean operators follow FHIRPath specification section 6.5 where operands can be true, false,
 * or empty (NULL).
 *
 * <p>Boolean collection functions (section 5.6.1): allTrue(), anyTrue(), allFalse(), anyFalse(),
 * all(criteria).
 */
public final class BooleanOps {

  private BooleanOps() {}

  /**
   * Registers all boolean operators and collection functions into the given registry.
   *
   * @param registry the registry to register operations into
   */
  public static void register(final SparkOperationRegistry registry) {
    // Spark's SQL-92 AND/OR match FHIRPath three-valued logic exactly
    registry.register("and", binary(Column::and));
    registry.register("or", binary(Column::or));

    // implies: NOT left OR right
    registry.register("implies", binary((l, r) -> functions.not(l).or(r)));

    // xor: null if either operand is null, otherwise not-equal
    registry.register("xor", binary((l, r) -> NullSupport.propagateNull(l.notEqual(r), l, r)));

    // not: Spark's NOT handles three-valued logic correctly
    registry.register("not", unary(functions::not));

    // Boolean collection functions (FHIRPath Spec 5.6.1)
    // Uses array_min/array_max pattern from Pathling: min(booleans) is false iff any is false,
    // max(booleans) is true iff any is true. coalesce handles empty → default value.
    registry.register("allTrue", booleanAggregate(functions::array_min, false, true));
    registry.register("anyTrue", booleanAggregate(functions::array_max, false, false));
    registry.register("allFalse", booleanAggregate(functions::array_max, true, true));
    registry.register("anyFalse", booleanAggregate(functions::array_min, true, false));

    // all(criteria): empty → true, all match → true, any mismatch → false
    registry.register("all", ctx -> evaluateAll(ctx.collectionArg(0), ctx.lambdaEvaluator(1)));
  }

  /**
   * Creates a boolean aggregate operation using the array_min/array_max pattern.
   *
   * @param aggregateFn the array aggregate function ({@code array_min} or {@code array_max})
   * @param negate whether to negate the aggregate result
   * @param defaultValue the value to return for empty collections
   */
  private static SparkOperationDef booleanAggregate(
      final UnaryOperator<Column> aggregateFn, final boolean negate, final boolean defaultValue) {
    final SparkOperationDef inner = collectionUnary(aggregateFn, c -> c);
    return ctx -> {
      final Column aggregated = inner.generate(ctx);
      return coalesce(negate ? functions.not(aggregated) : aggregated, lit(defaultValue));
    };
  }

  private static Column evaluateAll(
      final CollectionValue collection, final UnaryOperator<Column> criteria) {
    return collection.applyNonNull(
        // Collection: use Spark's forall with lambda evaluation.
        // forall returns true on empty arrays, matching FHIRPath spec.
        c -> functions.forall(c, criteria::apply),
        // Singular: evaluate lambda with value as $this
        criteria,
        lit(true));
  }
}
