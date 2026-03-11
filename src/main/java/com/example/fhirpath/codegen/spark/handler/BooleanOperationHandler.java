package com.example.fhirpath.codegen.spark.handler;

import com.example.fhirpath.typing.Type;
import jakarta.annotation.Nonnull;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.functions;

/**
 * Handler for boolean logic operations.
 * <p>
 * Implements all FHIRPath boolean operators with three-valued logic:
 * <ul>
 *   <li>and - Logical AND</li>
 *   <li>or - Logical OR</li>
 *   <li>xor - Exclusive OR</li>
 *   <li>implies - Material implication</li>
 *   <li>not - Logical negation</li>
 * </ul>
 * <p>
 * All operators follow FHIRPath specification section 6.5 for three-valued logic
 * where operands can be true, false, or empty (NULL).
 */
public final class BooleanOperationHandler extends AnnotatedOperationHandler {

    public BooleanOperationHandler(
            @Nonnull final String operation,
            @Nonnull final Type dispatchType,
            @Nonnull final CodeGenContext context) {
        super(operation, dispatchType, context);
    }

    /**
     * Logical AND with three-valued logic.
     * <p>
     * Truth table (FHIRPath spec section 6.5):
     * <pre>
     *          | true  | false | empty
     * ---------+-------+-------+------
     * true     | true  | false | empty
     * false    | false | false | false
     * empty    | empty | false | empty
     * </pre>
     * <p>
     * Spark's SQL-92 three-valued AND matches FHIRPath semantics exactly.
     */
    @Operation("and")
    @Nonnull
    public Column and(@Nonnull final Column left, @Nonnull final Column right) {
        return left.and(right);
    }

    /**
     * Logical OR with three-valued logic.
     * <p>
     * Truth table (FHIRPath spec section 6.5):
     * <pre>
     *          | true  | false | empty
     * ---------+-------+-------+------
     * true     | true  | true  | true
     * false    | true  | false | empty
     * empty    | true  | empty | empty
     * </pre>
     * <p>
     * Spark's SQL-92 three-valued OR matches FHIRPath semantics exactly.
     */
    @Operation("or")
    @Nonnull
    public Column or(@Nonnull final Column left, @Nonnull final Column right) {
        return left.or(right);
    }

    /**
     * Exclusive OR (XOR) with three-valued logic.
     * <p>
     * Truth table (FHIRPath spec section 6.5):
     * <pre>
     *          | true  | false | empty
     * ---------+-------+-------+------
     * true     | false | true  | empty
     * false    | true  | false | empty
     * empty    | empty | empty | empty
     * </pre>
     * <p>
     * When both operands are non-null, XOR is equivalent to not-equal.
     * When either operand is null, result is null (empty).
     */
    @Operation("xor")
    @Nonnull
    public Column xor(@Nonnull final Column left, @Nonnull final Column right) {
        return functions.when(left.isNull().or(right.isNull()), functions.lit(null))
                .otherwise(left.notEqual(right));
    }

    /**
     * Material implication with three-valued logic.
     * <p>
     * Truth table (FHIRPath spec section 6.5):
     * <pre>
     *          | true  | false | empty
     * ---------+-------+-------+------
     * true     | true  | false | empty
     * false    | true  | true  | true
     * empty    | true  | empty | empty
     * </pre>
     * <p>
     * Equivalent to: NOT left OR right.
     */
    @Operation("implies")
    @Nonnull
    public Column implies(@Nonnull final Column left, @Nonnull final Column right) {
        return functions.not(left).or(right);
    }

    /**
     * Logical negation with three-valued logic.
     * <p>
     * Truth table (FHIRPath spec section 6.5):
     * <pre>
     * not true  = false
     * not false = true
     * not empty = empty
     * </pre>
     */
    @Operation("not")
    @Nonnull
    public Column not(@Nonnull final Column operand) {
        return functions.not(operand);
    }
}
