package com.example.fhirpath.codegen.spark;

import static com.example.fhirpath.codegen.spark.SparkTypeMapper.toSparkDataType;
import static org.apache.spark.sql.functions.coalesce;
import static org.apache.spark.sql.functions.col;
import static org.apache.spark.sql.functions.concat;
import static org.apache.spark.sql.functions.lit;
import static org.apache.spark.sql.functions.when;

import com.example.fhirpath.ir.Cast;
import com.example.fhirpath.ir.Combine;
import com.example.fhirpath.ir.Equality;
import com.example.fhirpath.ir.EqualityOperator;
import com.example.fhirpath.ir.IRNodeVisitor;
import com.example.fhirpath.ir.Lambda;
import com.example.fhirpath.ir.Literal;
import com.example.fhirpath.ir.Operation;
import com.example.fhirpath.ir.Resource;
import com.example.fhirpath.ir.ThisReference;
import com.example.fhirpath.ir.Traversal;
import com.example.fhirpath.typing.Type;
import com.example.fhirpath.typing.Types;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.List;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.functions;
import org.apache.spark.sql.types.DataType;

/**
 * Generates Spark Column expressions from FHIRPath IR trees.
 *
 * <p>This visitor implements target-specific code generation for Apache Spark SQL. Each visit
 * method transforms an IR node into a Spark Column that can be executed by the Spark SQL engine.
 *
 * <p>All operations are dispatched through a {@link SparkOperationRegistry} — a simple map from
 * operation name to pure function.
 *
 * <p>Immutable: each instance may have a bound $this column for lambda evaluation.
 */
public class SparkCodeGenerator implements IRNodeVisitor<Column> {

  @Nullable private final Column thisColumn;

  @Nonnull private final SparkOperationRegistry registry;

  /**
   * Creates a code generator with the given operation registry.
   *
   * @param registry the operation registry for dispatch
   */
  public SparkCodeGenerator(@Nonnull final SparkOperationRegistry registry) {
    this(null, registry);
  }

  /** Private constructor for creating instances with a bound $this column. */
  private SparkCodeGenerator(
      @Nullable final Column thisColumn, @Nonnull final SparkOperationRegistry registry) {
    this.thisColumn = thisColumn;
    this.registry = registry;
  }

  /**
   * Creates a new SparkCodeGenerator with $this bound to the specified column. Used for lambda body
   * evaluation.
   */
  @Nonnull
  SparkCodeGenerator withThisColumn(@Nonnull final Column thisColumn) {
    return new SparkCodeGenerator(thisColumn, this.registry);
  }

  @Override
  @Nonnull
  public Column visitOperation(@Nonnull final Operation op) {
    // Recursively visit child arguments to generate their columns
    // Special case: don't evaluate Lambda nodes - they need to be handled specially
    final List<Column> argColumns =
        op.args().stream()
            .map(
                arg -> {
                  if (arg == null) return lit(null);
                  if (arg instanceof Lambda)
                    return null; // Lambda is passed as IR node, not evaluated
                  return arg.accept(this);
                })
            .toList();

    // Dispatch through registry
    final SparkOperationDef def = registry.get(op.name());
    if (def == null) {
      throw new UnsupportedOperationException(
          "Unknown operation: " + op.name() + " with result type: " + op.getType());
    }
    return def.generate(argColumns, op.args(), op.getType(), this);
  }

  // ========== Filtering and Conditional (package-private for ops classes) ==========

  /** Evaluates a where() filtering operation. */
  @Nonnull
  public Column evaluateWhere(
      final Column collection, final boolean isSingular, @Nonnull final Lambda lambda) {
    if (isSingular) {
      // Singular value: evaluate lambda directly with the value as $this
      // Return the value if criteria matches, NULL otherwise
      final SparkCodeGenerator singularGen = withThisColumn(collection);
      final Column criteriaResult = lambda.body().accept(singularGen);
      return when(criteriaResult, collection);
    } else {
      // Collection: use Spark's filter function
      final Column filtered =
          functions.filter(
              collection,
              elem -> {
                final SparkCodeGenerator lambdaGen = withThisColumn(elem);
                return lambda.body().accept(lambdaGen);
              });
      // Return null if the filtered array is empty (consistent with FHIRPath empty collection
      // semantics)
      return when(functions.size(filtered).gt(lit(0)), filtered);
    }
  }

  /**
   * Evaluates iif() collection-level conditional.
   *
   * <p>FHIRPath semantics: - Both lambdas are evaluated with $this bound to the entire collection -
   * If criterion returns true, return true-result - Otherwise, return empty (null in Spark
   * representation)
   */
  @Nonnull
  public Column evaluateIif(
      final Column collection, @Nonnull final Lambda criterion, @Nonnull final Lambda trueResult) {
    // Evaluate both lambdas with $this bound to entire collection
    final SparkCodeGenerator collectionGen = withThisColumn(collection);
    final Column criterionResult = criterion.body().accept(collectionGen);
    final Column trueValue = trueResult.body().accept(collectionGen);

    // Runtime short-circuit via Spark's when()
    return when(criterionResult, trueValue);
  }

  // ========== Infrastructure Nodes ==========

  @Override
  @Nonnull
  public Column visitLiteral(@Nonnull final Literal lit) {
    // Empty literal {} should be NULL, not an empty array
    if (lit.type() == Types.NULL || lit.value() == null) {
      return lit(null);
    }
    final DataType sparkType = toSparkDataType(lit.getShape());
    return lit(lit.value()).cast(sparkType);
  }

  @Override
  @Nonnull
  public Column visitTraversal(@Nonnull final Traversal trav) {
    final Column target = trav.target().accept(this);
    Column result = target.getField(trav.fieldSpec().getName());

    // Handle collection traversals - need to filter nulls and flatten if necessary
    if (!trav.target().isSingular()) {
      result = functions.filter(result, Column::isNotNull);
      if (!trav.fieldSpec().isSingular()) {
        result = functions.flatten(result);
      }
    }
    return result;
  }

  @Override
  @Nonnull
  public Column visitCast(@Nonnull final Cast cast) {
    final DataType sparkType = toSparkDataType(cast.getShape());
    // Get the child column
    final Column childColumn = cast.child().accept(this);
    return childColumn.cast(sparkType);
  }

  @Override
  @Nonnull
  public Column visitResource(@Nonnull final Resource res) {
    return res.type() != com.example.fhirpath.typing.ResourceType.EMPTY
        ? col(res.type().getResourceName())
        : lit(null);
  }

  @Override
  @Nonnull
  public Column visitCombine(@Nonnull final Combine combine) {
    final Column leftColumn = combine.left().accept(this);
    final Column rightColumn = combine.right().accept(this);

    // FHIRPath combine semantics: {} ; x = x, x ; {} = x
    // Empty collections (NULL) should be treated as empty arrays

    // Convert to arrays if singular, NULL stays NULL (will be coalesced to empty array)
    final Column leftArray =
        combine.left().isSingular()
            ? when(leftColumn.isNotNull(), functions.array(leftColumn))
            : leftColumn;
    final Column rightArray =
        combine.right().isSingular()
            ? when(rightColumn.isNotNull(), functions.array(rightColumn))
            : rightColumn;

    // concat handles NULL properly: concat(NULL, arr) = arr
    // Use concat for ordered concatenation (';' operator semantics)
    return concat(coalesce(leftArray, functions.array()), coalesce(rightArray, functions.array()));
  }

  @Override
  @Nonnull
  public Column visitEquality(@Nonnull final Equality equality) {
    final Type leftType = equality.left().getType();
    final Type rightType = equality.right().getType();
    final boolean isNotEquals = equality.operator() == EqualityOperator.NOT_EQUALS;

    // Handle null types (empty collection semantics)
    if (leftType == Types.NULL || rightType == Types.NULL) {
      return lit(null);
    }

    // Check if types are compatible (exact match or numeric coercion)
    final boolean typesCompatible =
        leftType == rightType || (isNumericType(leftType) && isNumericType(rightType));

    if (!typesCompatible) {
      // Incompatible types: = returns false, != returns true
      return lit(isNotEquals);
    }

    // Generate columns for both sides
    final Column left = equality.left().accept(this);
    final Column right = equality.right().accept(this);
    final boolean leftSingular = equality.left().isSingular();
    final boolean rightSingular = equality.right().isSingular();

    // Normalize cardinality following Pathling's approach:
    // - Both singular: compare directly (scalar = scalar)
    // - Mixed or both plural: normalize to arrays (array = array)
    final Column result;
    if (leftSingular && rightSingular) {
      result = left.equalTo(right);
    } else {
      final Column leftArray = leftSingular ? functions.array(left) : left;
      final Column rightArray = rightSingular ? functions.array(right) : right;
      result = leftArray.equalTo(rightArray);
    }

    return isNotEquals ? functions.not(result) : result;
  }

  /** Check if a type is numeric (INTEGER or DECIMAL). */
  private boolean isNumericType(final Type type) {
    return type == Types.INTEGER || type == Types.DECIMAL;
  }

  @Override
  @Nonnull
  public Column visitLambda(@Nonnull final Lambda lambda) {
    throw new UnsupportedOperationException(
        "Lambdas cannot be evaluated directly - they must be inlined at their call site");
  }

  @Override
  @Nonnull
  public Column visitThisReference(@Nonnull final ThisReference thisRef) {
    if (thisColumn == null) {
      throw new UnsupportedOperationException(
          "$this cannot be evaluated outside of a lambda context");
    }
    return thisColumn;
  }
}
