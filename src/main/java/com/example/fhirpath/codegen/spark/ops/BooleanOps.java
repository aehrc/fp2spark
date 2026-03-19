package com.example.fhirpath.codegen.spark.ops;

import static org.apache.spark.sql.functions.coalesce;
import static org.apache.spark.sql.functions.lit;
import static org.apache.spark.sql.functions.when;

import com.example.fhirpath.codegen.spark.SparkOpContext;
import com.example.fhirpath.codegen.spark.SparkOperationRegistry;
import com.example.fhirpath.ir.Lambda;
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
    registry.binary("and", Column::and);
    registry.binary("or", Column::or);

    // implies: NOT left OR right
    registry.binary("implies", (l, r) -> functions.not(l).or(r));

    // xor: null if either operand is null, otherwise not-equal
    registry.binary(
        "xor", (l, r) -> when(l.isNull().or(r.isNull()), lit(null)).otherwise(l.notEqual(r)));

    // not: Spark's NOT handles three-valued logic correctly
    registry.unary("not", functions::not);

    // Boolean collection functions (FHIRPath Spec 5.6.1)
    // Uses array_min/array_max pattern from Pathling: min(booleans) is false iff any is false,
    // max(booleans) is true iff any is true. coalesce handles empty → default value.

    // allTrue(): empty → true, all true → true, any false → false
    registry.register(
        "allTrue",
        ctx -> coalesce(ctx.collectionArg(0).apply(functions::array_min, c -> c), lit(true)));

    // anyTrue(): empty → false, any true → true
    registry.register(
        "anyTrue",
        ctx -> coalesce(ctx.collectionArg(0).apply(functions::array_max, c -> c), lit(false)));

    // allFalse(): empty → true, all false → true, any true → false
    registry.register(
        "allFalse",
        ctx ->
            coalesce(
                functions.not(ctx.collectionArg(0).apply(functions::array_max, c -> c)),
                lit(true)));

    // anyFalse(): empty → false, any false → true
    registry.register(
        "anyFalse",
        ctx ->
            coalesce(
                functions.not(ctx.collectionArg(0).apply(functions::array_min, c -> c)),
                lit(false)));

    // all(criteria): empty → true, all match → true, any mismatch → false
    registry.register(
        "all", ctx -> evaluateAll(ctx.arg(0), ctx.argNode(0).isSingular(), ctx.lambdaArg(1), ctx));
  }

  private static Column evaluateAll(
      final Column collection,
      final boolean isSingular,
      final Lambda lambda,
      final SparkOpContext ctx) {
    if (isSingular) {
      // Singular: evaluate lambda with value as $this, empty → true
      final Column criteriaResult = ctx.evaluateLambda(collection, lambda);
      return when(collection.isNull(), lit(true)).otherwise(criteriaResult);
    } else {
      // Collection: use Spark's forall with lambda evaluation.
      // forall returns true on empty arrays, matching FHIRPath spec.
      return when(collection.isNull(), lit(true))
          .otherwise(functions.forall(collection, elem -> ctx.evaluateLambda(elem, lambda)));
    }
  }
}
