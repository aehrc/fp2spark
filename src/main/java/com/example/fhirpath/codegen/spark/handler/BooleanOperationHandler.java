package com.example.fhirpath.codegen.spark.handler;

import com.example.fhirpath.typing.Type;
import jakarta.annotation.Nonnull;
import org.apache.spark.sql.Column;

import static org.apache.spark.sql.functions.*;

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
     * Truth table:
     * - true and true = true
     * - true and false = false
     * - false and X = false
     * - true and empty = empty
     * - empty and true = empty
     * - empty and false = false
     * - empty and empty = empty
     */
    @Operation("and")
    @Nonnull
    public Column and(@Nonnull final Column left, @Nonnull final Column right) {
        return left.and(right);
    }

    /**
     * Logical OR with three-valued logic.
     * <p>
     * Truth table:
     * - true or X = true
     * - false or false = false
     * - false or empty = empty
     * - empty or true = true
     * - empty or false = empty
     * - empty or empty = empty
     */
    @Operation("or")
    @Nonnull
    public Column or(@Nonnull final Column left, @Nonnull final Column right) {
        return left.or(right);
    }

    /**
     * Exclusive OR (XOR) with three-valued logic.
     * <p>
     * Truth table:
     * - true xor false = true
     * - false xor true = true
     * - true xor true = false
     * - false xor false = false
     * - Any xor empty = empty
     * <p>
     * Implementation: When both non-null, return left !== right.
     * When either is null, return null.
     */
    @Operation("xor")
    @Nonnull
    public Column xor(@Nonnull final Column left, @Nonnull final Column right) {
        // XOR with three-valued logic:
        // When both operands are non-null, return left !== right (not equal)
        // When either operand is null, return null
        return when(left.isNull().or(right.isNull()), lit(null))
                .otherwise(left.notEqual(right));
    }

    /**
     * Material implication with three-valued logic.
     * <p>
     * Truth table:
     * - true implies true = true
     * - true implies false = false
     * - true implies empty = empty
     * - false implies X = true (vacuously true)
     * - empty implies true = true
     * - empty implies false = empty
     * - empty implies empty = empty
     * <p>
     * Implementation: !left OR right
     */
    @Operation("implies")
    @Nonnull
    public Column implies(@Nonnull final Column left, @Nonnull final Column right) {
        return not(left).or(right);
    }

    /**
     * Logical negation with three-valued logic.
     * <p>
     * Truth table:
     * - not true = false
     * - not false = true
     * - not empty = empty
     */
    @Operation("not")
    @Nonnull
    public Column not(@Nonnull final Column operand) {
        return not(operand);
    }
}
