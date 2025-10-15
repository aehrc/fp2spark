package com.example.fhirpath.codegen.spark;

import com.example.fhirpath.ir.*;
import com.example.fhirpath.typing.PrimitiveType;
import com.example.fhirpath.typing.Type;
import com.example.fhirpath.typing.Types;
import jakarta.annotation.Nullable;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.functions;
import org.apache.spark.sql.types.DataType;

import javax.annotation.Nonnull;
import java.util.List;

import static com.example.fhirpath.codegen.spark.Date.date;
import static com.example.fhirpath.codegen.spark.DateTime.dateTime;
import static com.example.fhirpath.codegen.spark.Quantity.quantity;
import static com.example.fhirpath.codegen.spark.Time.time;
import static org.apache.spark.sql.functions.*;

/**
 * Generates Spark Column expressions from FHIRPath IR trees.
 *
 * This visitor implements target-specific code generation for Apache Spark SQL.
 * Each visit method transforms an IR node into a Spark Column that can be
 * executed by the Spark SQL engine.
 *
 * Immutable: each instance may have a bound $this column for lambda evaluation.
 */
public class SparkCodeGenerator implements IRNodeVisitor<Column> {

    @Nullable
    private final Column thisColumn;

    /**
     * Default constructor for top-level code generation (no $this binding).
     */
    public SparkCodeGenerator() {
        this(null);
    }

    /**
     * Private constructor for creating instances with a bound $this column.
     */
    private SparkCodeGenerator(@Nullable Column thisColumn) {
        this.thisColumn = thisColumn;
    }

    /**
     * Creates a new SparkCodeGenerator with $this bound to the specified column.
     * Used for lambda body evaluation.
     */
    @Nonnull
    private SparkCodeGenerator withThisColumn(@Nonnull Column thisColumn) {
        return new SparkCodeGenerator(thisColumn);
    }

    @Override
    @Nonnull
    public Column visitOperation(@Nonnull Operation op) {
        // Recursively visit child arguments to generate their columns
        // Handle null arguments (e.g., optional parameters that weren't provided)
        // Special case: don't evaluate Lambda nodes - they need to be handled specially
        List<Column> argColumns = op.args().stream()
            .map(arg -> {
                if (arg == null) return lit(null);
                if (arg instanceof Lambda) return null; // Lambda is passed as IR node, not evaluated
                return arg.accept(this);
            })
            .toList();

        // Dispatch to appropriate evaluation method based on operation name
        return evaluateOperation(op.name(), argColumns, op.getType(), op.args());
    }

    /**
     * Central dispatch for all operations.
     */
    @Nonnull
    private Column evaluateOperation(String name, List<Column> args, Type resultType, List<IRNode> argNodes) {
        return switch(name) {
            // Arithmetic
            case "add" -> evaluateAdd(args, resultType);
            case "sub" -> evaluateSub(args, resultType);
            case "multiply" -> evaluateMultiply(args, resultType);
            case "divide" -> evaluateDivide(args, resultType);
            case "mod" -> evaluateMod(args, resultType);

            // Comparison - use input type from first argument, not result type
            case "gt" -> evaluateGreaterThan(args, argNodes.get(0).getType());
            case "lt" -> evaluateLessThan(args, argNodes.get(0).getType());
            case "geq" -> evaluateGreaterEqual(args, argNodes.get(0).getType());
            case "leq" -> evaluateLessEqual(args, argNodes.get(0).getType());

            // Math functions
            case "abs" -> evaluateAbs(args, resultType);
            case "ceiling" -> evaluateCeiling(args, resultType);
            case "floor" -> evaluateFloor(args, resultType);
            case "truncate" -> evaluateTruncate(args, resultType);
            case "exp" -> evaluateExp(args, resultType);
            case "ln" -> evaluateLn(args, resultType);
            case "log" -> evaluateLog(args, resultType);
            case "sqrt" -> evaluateSqrt(args, resultType);

            // String functions
            case "substring" -> evaluateSubstring(args);
            case "startsWith" -> evaluateStartsWith(args);
            case "endsWith" -> evaluateEndsWith(args);
            case "contains" -> evaluateContains(args);
            case "upper" -> upper(args.get(0));
            case "lower" -> lower(args.get(0));
            case "replace" -> regexp_replace(args.get(0), args.get(1), args.get(2));
            case "matches" -> evaluateMatches(args);
            case "length" -> length(args.get(0));

            // Boolean operators
            case "and" -> args.get(0).and(args.get(1));
            case "or" -> args.get(0).or(args.get(1));
            case "xor" -> args.get(0).bitwiseXOR(args.get(1));
            case "implies" -> not(args.get(0)).or(args.get(1));
            case "not" -> not(args.get(0));

            // Collection functions
            case "count" -> evaluateCount(args.get(0), argNodes.get(0).isSingular());
            case "exists" -> evaluateExists(args.get(0));
            case "empty" -> evaluateEmpty(args.get(0));
            case "first" -> evaluateFirst(args.get(0), argNodes.get(0).isSingular());

            // Filtering and projection
            case "where" -> evaluateWhere(args.get(0), argNodes.get(0).isSingular(), argNodes.get(1));

            // Conditional operations
            case "iif" -> evaluateIif(args.get(0), argNodes.get(1), argNodes.get(2));

            default -> throw new UnsupportedOperationException(
                "Unknown operation: " + name + " with result type: " + resultType);
        };
    }

    // ========== Arithmetic Operations ==========

    @Nonnull
    private Column evaluateAdd(List<Column> args, Type resultType) {
        Column left = args.get(0);
        Column right = args.get(1);

        return switch((PrimitiveType) resultType) {
            case INTEGER, DECIMAL -> left.plus(right);
            case STRING -> concat(left, right);
            case DATE_TIME -> dateTime(left).plus(quantity(right));
            case QUANTITY -> quantity(left).plus(quantity(right));
            default -> throw new IllegalArgumentException(
                "Unsupported result type for add: " + resultType);
        };
    }

    @Nonnull
    private Column evaluateSub(List<Column> args, Type resultType) {
        Column left = args.get(0);
        Column right = args.get(1);

        return switch((PrimitiveType) resultType) {
            case INTEGER, DECIMAL -> left.minus(right);
            case DATE_TIME, QUANTITY -> left.minus(right); // Simplified - use direct minus
            default -> throw new IllegalArgumentException(
                "Unsupported result type for sub: " + resultType);
        };
    }

    @Nonnull
    private Column evaluateMultiply(List<Column> args, Type resultType) {
        Column left = args.get(0);
        Column right = args.get(1);

        return switch((PrimitiveType) resultType) {
            case INTEGER, DECIMAL -> left.multiply(right);
            case QUANTITY -> left.multiply(right); // Use simple multiply for now
            default -> throw new IllegalArgumentException(
                "Unsupported result type for multiply: " + resultType);
        };
    }

    @Nonnull
    private Column evaluateDivide(List<Column> args, Type resultType) {
        Column left = args.get(0);
        Column right = args.get(1);

        return switch((PrimitiveType) resultType) {
            case INTEGER, DECIMAL -> left.divide(right);
            case QUANTITY -> quantity(left).divide(quantity(right));
            default -> throw new IllegalArgumentException(
                "Unsupported result type for divide: " + resultType);
        };
    }

    @Nonnull
    private Column evaluateMod(List<Column> args, Type resultType) {
        return args.get(0).mod(args.get(1));
    }

    // ========== Comparison Operations ==========

    @Nonnull
    private Column evaluateGreaterThan(List<Column> args, Type inputType) {
        Column left = args.get(0);
        Column right = args.get(1);

        // Get input type from first argument's type (before comparison)
        // Note: resultType is always BOOLEAN for comparisons
        return switch((PrimitiveType) inputType) {
            case INTEGER, DECIMAL, STRING -> left.gt(right);
            case QUANTITY -> quantity(left).gt(quantity(right));
            case DATE_TIME -> dateTime(left).gt(dateTime(right));
            case DATE -> date(left).gt(date(right));
            case TIME -> time(left).gt(time(right));
            default -> throw new IllegalArgumentException(
                "Unsupported input type for gt: " + inputType);
        };
    }

    @Nonnull
    private Column evaluateLessThan(List<Column> args, Type inputType) {
        Column left = args.get(0);
        Column right = args.get(1);

        return switch((PrimitiveType) inputType) {
            case INTEGER, DECIMAL, STRING -> left.lt(right);
            case QUANTITY -> quantity(left).lt(quantity(right));
            case DATE_TIME -> dateTime(left).lt(dateTime(right));
            case DATE -> date(left).lt(date(right));
            case TIME -> time(left).lt(time(right));
            default -> throw new IllegalArgumentException(
                "Unsupported input type for lt: " + inputType);
        };
    }

    @Nonnull
    private Column evaluateGreaterEqual(List<Column> args, Type inputType) {
        Column left = args.get(0);
        Column right = args.get(1);

        return switch((PrimitiveType) inputType) {
            case INTEGER, DECIMAL, STRING -> left.geq(right);
            case QUANTITY -> quantity(left).geq(quantity(right));
            case DATE_TIME -> dateTime(left).geq(dateTime(right));
            case DATE -> date(left).geq(date(right));
            case TIME -> time(left).geq(time(right));
            default -> throw new IllegalArgumentException(
                "Unsupported input type for geq: " + inputType);
        };
    }

    @Nonnull
    private Column evaluateLessEqual(List<Column> args, Type inputType) {
        Column left = args.get(0);
        Column right = args.get(1);

        return switch((PrimitiveType) inputType) {
            case INTEGER, DECIMAL, STRING -> left.leq(right);
            case QUANTITY -> quantity(left).lt(quantity(right)).or(left.equalTo(right));
            case DATE_TIME -> dateTime(left).lt(dateTime(right)).or(left.equalTo(right));
            case DATE -> date(left).lt(date(right)).or(left.equalTo(right));
            case TIME -> time(left).lt(time(right)).or(left.equalTo(right));
            default -> throw new IllegalArgumentException(
                "Unsupported input type for leq: " + inputType);
        };
    }

    // ========== Math Functions ==========

    @Nonnull
    private Column evaluateAbs(List<Column> args, Type resultType) {
        Column target = args.get(0);

        return switch((PrimitiveType) resultType) {
            case INTEGER, DECIMAL -> abs(target);
            case QUANTITY -> quantity(target).abs();
            default -> throw new IllegalArgumentException(
                "Unsupported result type for abs: " + resultType);
        };
    }

    @Nonnull
    private Column evaluateCeiling(List<Column> args, Type resultType) {
        return ceil(args.get(0));
    }

    @Nonnull
    private Column evaluateFloor(List<Column> args, Type resultType) {
        return floor(args.get(0));
    }

    @Nonnull
    private Column evaluateTruncate(List<Column> args, Type resultType) {
        // Truncate towards zero
        Column target = args.get(0);
        return when(target.geq(lit(0)), floor(target))
            .otherwise(ceil(target));
    }

    @Nonnull
    private Column evaluateExp(List<Column> args, Type resultType) {
        return exp(args.get(0));
    }

    @Nonnull
    private Column evaluateLn(List<Column> args, Type resultType) {
        return log(args.get(0));
    }

    @Nonnull
    private Column evaluateLog(List<Column> args, Type resultType) {
        return log(10.0, args.get(0));
    }

    @Nonnull
    private Column evaluateSqrt(List<Column> args, Type resultType) {
        return sqrt(args.get(0));
    }

    // ========== String Functions ==========

    @Nonnull
    private Column evaluateStartsWith(List<Column> args) {
        return args.get(0).startsWith(args.get(1));
    }

    @Nonnull
    private Column evaluateEndsWith(List<Column> args) {
        return args.get(0).endsWith(args.get(1));
    }

    @Nonnull
    private Column evaluateContains(List<Column> args) {
        return args.get(0).contains(args.get(1));
    }

    @Nonnull
    private Column evaluateMatches(List<Column> args) {
        // For matches, Spark SQL has a regexp_like function that works with column patterns
        Column target = args.get(0);
        Column pattern = args.get(1);
        // Use call_function to dynamically call regexp_like with both columns
        return functions.call_function("regexp_like", target, pattern);
    }

    @Nonnull
    private Column evaluateSubstring(List<Column> args) {
        final Column targetColumn = args.get(0);
        // FHIRPath uses 0-based indexing, Spark uses 1-based
        final Column posColumn = args.get(1).plus(lit(1));

        // Handle optional length parameter
        final Column nonNullLengthColumn = coalesce(args.get(2), lit(Integer.MAX_VALUE));

        // FHIRPath null propagation rules
        final Column nullPropagationCondition = targetColumn.isNull()
            .or(posColumn.isNull());

        final Column posOutOfBoundsCondition = posColumn.leq(0)
            .or(posColumn.gt(length(targetColumn)));

        final Column nullCondition = nullPropagationCondition
            .or(posOutOfBoundsCondition);

        return when(not(nullCondition),
            substr(targetColumn, posColumn, nonNullLengthColumn));
    }

    // ========== Collection Functions ==========

    @Nonnull
    private Column evaluateCount(Column childColumn, boolean isSingular) {
        // Handle based on singularity
        if (isSingular) {
            return when(childColumn.isNotNull(), lit(1)).otherwise(lit(0));
        } else {
            return when(childColumn.isNotNull(), functions.size(childColumn)).otherwise(lit(0));
        }
    }

    @Nonnull
    private Column evaluateExists(Column childColumn) {
        return when(childColumn.isNotNull(), lit(true)).otherwise(lit(false));
    }

    @Nonnull
    private Column evaluateEmpty(Column childColumn) {
        return when(childColumn.isNull(), lit(true)).otherwise(lit(false));
    }

    @Nonnull
    private Column evaluateFirst(final Column childColumn, final boolean isSingular) {
        // FHIRPath semantics:
        // - Empty collection (NULL) returns empty (NULL)
        // - Singular values return themselves (they ARE the first element)
        // - Multi-element collections return element at index 0

        if (isSingular) {
            // Singular value: return the value itself
            return childColumn;
        } else {
            // Collection: extract first element using get() with 0-based index
            // Returns null if array is null or empty
            return functions.get(childColumn, lit(0));
        }
    }

    // ========== Filtering and Projection ==========

    @Nonnull
    private Column evaluateWhere(final Column collection, final boolean isSingular, @Nonnull final IRNode lambdaNode) {
        if (!(lambdaNode instanceof Lambda lambda)) {
            throw new IllegalArgumentException(
                "where() requires a Lambda argument, got: " + lambdaNode.getClass()
            );
        }

        // FHIRPath semantics:
        // - Empty collection (NULL) returns empty (NULL)
        // - Singular values are treated as single-element collections
        // - Multi-element collections are filtered

        if (isSingular) {
            // Singular value: evaluate lambda directly with the value as $this
            // Return the value if criteria matches, NULL otherwise
            final SparkCodeGenerator singularGen = withThisColumn(collection);
            final Column criteriaResult = lambda.body().accept(singularGen);
            return when(criteriaResult, collection);
        } else {
            // Collection: use Spark's filter function
            final Column filtered = functions.filter(collection, elem -> {
                final SparkCodeGenerator lambdaGen = withThisColumn(elem);
                return lambda.body().accept(lambdaGen);
            });
            // Return null if the filtered array is empty (consistent with FHIRPath empty collection semantics)
            return when(functions.size(filtered).gt(lit(0)), filtered);
        }
    }

    // ========== Conditional Operations ==========

    /**
     * Evaluates iif() collection-level conditional.
     *
     * FHIRPath semantics:
     * - Both lambdas are evaluated with $this bound to the entire collection
     * - If criterion returns true, return true-result
     * - Otherwise, return empty (null in Spark representation)
     *
     * Example: (1 | 2).iif(exists(), $this) → [1, 2]
     * Example: (1 | 2 | 3).iif(count() > 2, first()) → 1
     */
    @Nonnull
    private Column evaluateIif(
        final Column collection,
        @Nonnull final IRNode criterionLambda,
        @Nonnull final IRNode trueResultLambda
    ) {
        if (!(criterionLambda instanceof Lambda criterion)) {
            throw new IllegalArgumentException(
                "iif() criterion must be a Lambda, got: " + criterionLambda.getClass()
            );
        }
        if (!(trueResultLambda instanceof Lambda trueResult)) {
            throw new IllegalArgumentException(
                "iif() true-result must be a Lambda, got: " + trueResultLambda.getClass()
            );
        }

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
    public Column visitLiteral(@Nonnull Literal lit) {
        DataType sparkType = SparkTypeMapper.toSparkDataType(lit.type());
        return lit(lit.value()).cast(sparkType);
    }

    @Override
    @Nonnull
    public Column visitTraversal(@Nonnull Traversal trav) {
        Column target = trav.target().accept(this);
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
    public Column visitCast(@Nonnull Cast cast) {
        DataType sparkType = SparkTypeMapper.toSparkDataType(cast.targetType());
        // Get the child column
        Column childColumn = cast.child().accept(this);
        // Apply cast based on singularity
        if (cast.isSingular()) {
            return childColumn.cast(sparkType);
        } else {
            return childColumn.cast(org.apache.spark.sql.types.DataTypes.createArrayType(sparkType));
        }
    }

    @Override
    @Nonnull
    public Column visitResource(@Nonnull Resource res) {
        return res.type() != com.example.fhirpath.typing.ResourceType.EMPTY
            ? col(res.type().getResourceName())
            : lit(null);
    }

    @Override
    @Nonnull
    public Column visitCastToSystem(@Nonnull CastToSystem castToSystem) {
        // GetValue converts FHIR types to system types by casting
        Column childColumn = castToSystem.child().accept(this);
        DataType sparkType = SparkTypeMapper.toSparkDataType(castToSystem.getType());

        // Handle both singular and collection cases
        if (castToSystem.isSingular()) {
            return childColumn.cast(sparkType);
        } else {
            return childColumn.cast(org.apache.spark.sql.types.DataTypes.createArrayType(sparkType));
        }
    }

    @Override
    @Nonnull
    public Column visitUnion(@Nonnull Union union) {
        Column leftColumn = union.left().accept(this);
        Column rightColumn = union.right().accept(this);

        // Convert to arrays if singular
        Column leftArray = union.left().isSingular()
            ? when(leftColumn.isNotNull(), functions.array(leftColumn)).otherwise(functions.array())
            : leftColumn;
        Column rightArray = union.right().isSingular()
            ? when(rightColumn.isNotNull(), functions.array(rightColumn)).otherwise(functions.array())
            : rightColumn;

        return array_union(leftArray, rightArray);
    }

    @Override
    @Nonnull
    public Column visitEquals(@Nonnull Equals equals) {
        Type leftType = equals.left().getType();
        Type rightType = equals.right().getType();

        // Normalize FHIR types to their system types for comparison
        Type normalizedLeftType = leftType instanceof com.example.fhirpath.typing.fhir.FhirType fhirLeft
            ? fhirLeft.systemType()
            : leftType;
        Type normalizedRightType = rightType instanceof com.example.fhirpath.typing.fhir.FhirType fhirRight
            ? fhirRight.systemType()
            : rightType;

        // Handle null types
        if (normalizedLeftType == Types.NULL || normalizedRightType == Types.NULL) {
            return lit(null);
        }

        // Check if types are compatible (exact match or numeric coercion)
        boolean typesCompatible = normalizedLeftType == normalizedRightType ||
            (isNumericType(normalizedLeftType) && isNumericType(normalizedRightType));

        if (!typesCompatible) {
            return lit(false);
        }

        // If types are compatible, perform actual equality comparison
        Column left = equals.left().accept(this);
        Column right = equals.right().accept(this);
        return left.equalTo(right);
    }

    /**
     * Check if a type is numeric (INTEGER or DECIMAL).
     */
    private boolean isNumericType(Type type) {
        return type == Types.INTEGER || type == Types.DECIMAL;
    }

    @Override
    @Nonnull
    public Column visitLambda(@Nonnull Lambda lambda) {
        throw new UnsupportedOperationException(
            "Lambdas cannot be evaluated directly - they must be inlined at their call site"
        );
    }

    @Override
    @Nonnull
    public Column visitThisReference(@Nonnull ThisReference thisRef) {
        if (thisColumn == null) {
            throw new UnsupportedOperationException(
                "$this cannot be evaluated outside of a lambda context"
            );
        }
        return thisColumn;
    }
}
