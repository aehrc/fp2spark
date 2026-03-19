package com.example.fhirpath.codegen.spark.ops;

import static org.apache.spark.sql.functions.when;

import com.example.fhirpath.codegen.spark.CollectionValue;
import com.example.fhirpath.codegen.spark.SparkOpContext;
import com.example.fhirpath.codegen.spark.SparkOperationRegistry;
import com.example.fhirpath.ir.Lambda;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.functions;

/**
 * Filtering, projection, and conditional operation registrations (where, select, iif).
 *
 * <p>Lambda evaluation uses {@code ctx.evaluateLambda()} to bind {@code $this} and evaluate the
 * lambda body. Singular/plural dispatch is handled here using the appropriate Spark array
 * functions.
 */
public final class FilteringOps {

  private FilteringOps() {}

  /**
   * Registers all filtering and conditional operators into the given registry.
   *
   * @param registry the registry to register operations into
   */
  public static void register(final SparkOperationRegistry registry) {
    registry.register(
        "where",
        ctx -> evaluateWhere(ctx.arg(0), ctx.argNode(0).isSingular(), ctx.lambdaArg(1), ctx));

    registry.register(
        "select",
        ctx -> evaluateSelect(ctx.arg(0), ctx.argNode(0).isSingular(), ctx.lambdaArg(1), ctx));

    registry.register(
        "iif", ctx -> evaluateIif(ctx.arg(0), ctx.lambdaArg(1), ctx.lambdaArg(2), ctx));
  }

  private static Column evaluateWhere(
      final Column collection,
      final boolean isSingular,
      final Lambda lambda,
      final SparkOpContext ctx) {
    if (isSingular) {
      // Singular value: return the value if criteria matches, NULL otherwise
      final Column criteriaResult = ctx.evaluateLambda(collection, lambda);
      return when(criteriaResult, collection);
    } else {
      // Collection: use Spark's filter function
      final Column filtered =
          functions.filter(collection, elem -> ctx.evaluateLambda(elem, lambda));
      return CollectionValue.nullIfEmpty(filtered);
    }
  }

  private static Column evaluateSelect(
      final Column collection,
      final boolean isSingular,
      final Lambda lambda,
      final SparkOpContext ctx) {
    if (isSingular) {
      // Singular value: evaluate lambda with the value as $this
      // If input is null, result is null (empty collection propagation)
      final Column result = ctx.evaluateLambda(collection, lambda);
      return when(collection.isNotNull(), result);
    } else {
      // Collection: use Spark's transform to evaluate lambda for each element
      final Column transformed =
          functions.transform(collection, elem -> ctx.evaluateLambda(elem, lambda));

      final Column result;
      if (lambda.body().isSingular()) {
        // Lambda returns singular: transform gives array of values, filter out nulls
        result = functions.filter(transformed, Column::isNotNull);
      } else {
        // Lambda returns MANY: transform gives array of arrays, flatten then filter nulls
        result = functions.filter(functions.flatten(transformed), Column::isNotNull);
      }

      return CollectionValue.nullIfEmpty(result);
    }
  }

  private static Column evaluateIif(
      final Column collection,
      final Lambda criterion,
      final Lambda trueResult,
      final SparkOpContext ctx) {
    // Evaluate both lambdas with $this bound to entire collection
    final Column criterionResult = ctx.evaluateLambda(collection, criterion);
    final Column trueValue = ctx.evaluateLambda(collection, trueResult);

    // Runtime short-circuit via Spark's when()
    return when(criterionResult, trueValue);
  }
}
