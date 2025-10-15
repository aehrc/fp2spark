package com.example.fhirpath.analyzer;

import com.example.fhirpath.typing.Type;

import java.util.List;

import static com.example.fhirpath.typing.Type.*;

/**
 * Predefined type sets for common signature patterns in FHIRPath.
 * <p>
 * These sets group types that share common operation signatures,
 * reducing duplication in the OperationRegistry.
 */
public final class TypeSets {

    private TypeSets() {
        throw new AssertionError("No instances");
    }

    /**
     * Numeric types: Integer and Decimal.
     * Used for: mod, ceiling, floor, truncate, exp, ln, log, sqrt
     */
    public static final List<Type> NUMERIC = List.of(INTEGER, DECIMAL);

    /**
     * Numeric types including Quantity.
     * Used for: add, sub, multiply, divide, abs
     */
    public static final List<Type> NUMERIC_WITH_QUANTITY = List.of(INTEGER, DECIMAL, QUANTITY);

    /**
     * Comparable types that support ordering operations.
     * Used for: gt, lt, geq, leq
     */
    public static final List<Type> COMPARABLE = List.of(
            INTEGER, DECIMAL, STRING, QUANTITY,
            DATE, DATE_TIME, TIME
    );

    /**
     * Temporal types (date/time related).
     * Used for temporal operations.
     */
    public static final List<Type> TEMPORAL = List.of(DATE, DATE_TIME, TIME);

    /**
     * String-like types.
     * Used for string operations like add.
     */
    public static final List<Type> STRING_LIKE = List.of(STRING);
}
