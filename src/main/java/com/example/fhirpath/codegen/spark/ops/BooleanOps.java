package com.example.fhirpath.codegen.spark.ops;

import static org.apache.spark.sql.functions.lit;
import static org.apache.spark.sql.functions.when;

import com.example.fhirpath.codegen.spark.SparkCodeGenerator;
import com.example.fhirpath.codegen.spark.SparkOperationRegistry;
import com.example.fhirpath.ir.Lambda;
import java.util.function.Function;
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

    // allTrue(): empty → true, all true → true, any false → false
    registry.register(
        "allTrue",
        ctx ->
            ctx.collectionArg(0)
                .applyNonNull(
                    col -> functions.forall(col, x -> x), Function.identity(), lit(true)));

    // anyTrue(): empty → false, any true → true
    registry.register(
        "anyTrue",
        ctx ->
            ctx.collectionArg(0)
                .applyNonNull(
                    col -> functions.exists(col, x -> x), Function.identity(), lit(false)));

    // allFalse(): empty → true, all false → true, any true → false
    registry.register(
        "allFalse",
        ctx ->
            ctx.collectionArg(0)
                .applyNonNull(
                    col -> functions.forall(col, x -> functions.not(x)),
                    x -> functions.not(x),
                    lit(true)));

    // anyFalse(): empty → false, any false → true
    registry.register(
        "anyFalse",
        ctx ->
            ctx.collectionArg(0)
                .applyNonNull(
                    col -> functions.exists(col, x -> functions.not(x)),
                    x -> functions.not(x),
                    lit(false)));

    // all(criteria): empty → true, all match → true, any mismatch → false
    registry.register(
        "all",
        ctx -> {
          if (!(ctx.argNode(1) instanceof Lambda lambda)) {
            throw new IllegalArgumentException(
                "all() requires a Lambda argument, got: " + ctx.argNode(1).getClass());
          }
          return evaluateAll(ctx.arg(0), ctx.argNode(0).isSingular(), lambda, ctx.generator());
        });
  }

  /**
   * Evaluates the all(criteria) function.
   *
   * <p>Returns true if for every element in the input collection, criteria evaluates to true. Empty
   * input returns true per the FHIRPath spec.
   */
  private static Column evaluateAll(
      final Column collection,
      final boolean isSingular,
      final Lambda lambda,
      final SparkCodeGenerator generator) {
    if (isSingular) {
      // Singular: evaluate lambda with value as $this, empty → true
      final SparkCodeGenerator singularGen = generator.withThisColumn(collection);
      final Column criteriaResult = lambda.body().accept(singularGen);
      return when(collection.isNull(), lit(true)).otherwise(criteriaResult);
    } else {
      // Collection: use Spark's forall with lambda evaluation
      // forall returns true on empty arrays, matching FHIRPath spec
      return when(collection.isNull(), lit(true))
          .otherwise(
              functions.forall(
                  collection,
                  elem -> {
                    final SparkCodeGenerator lambdaGen = generator.withThisColumn(elem);
                    return lambda.body().accept(lambdaGen);
                  }));
    }
  }
}
