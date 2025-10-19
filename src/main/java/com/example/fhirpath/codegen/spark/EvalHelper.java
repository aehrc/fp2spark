package com.example.fhirpath.codegen.spark;


import com.example.fhirpath.ir.IRNode;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.functions;

import jakarta.annotation.Nonnull;
import java.util.function.Function;

import static org.apache.spark.sql.functions.lit;
import static org.apache.spark.sql.functions.when;

/**
 * Helper record for handling FHIRPath expressions that can be either singular values or arrays.
 *
 * <p>In FHIRPath, collections can have different cardinalities (single vs many elements).
 * This record provides utilities to apply different operations based on whether a column
 * represents a singular value or an array, enabling proper handling of both cases.
 *
 * @param column The Spark SQL column containing the value
 * @param isSingular Whether the column represents a singular value (true) or an array (false)
 */
public record EvalHelper(Column column, boolean isSingular) {
    /**
     * Apply different functions based on whether the column is singular or an array.
     *
     * @param arrayFunction  Function to apply if the column is an array
     * @param singleFunction Function to apply if the column is singular
     * @return The resulting column after applying the appropriate function
     */
    @Nonnull
    public Column apply(Function<Column, Column> arrayFunction,
                        Function<Column, Column> singleFunction) {
        return isSingular ? singleFunction.apply(column) : arrayFunction.apply(column);
    }

    /**
     * Apply different functions based on whether the column is singular or an array,
     * but only if the column is non-null. If the column is null, return the provided default value.
     *
     * @param arrayFunction  Function to apply if the column is an array
     * @param singleFunction Function to apply if the column is singular
     * @param defaultValue   Default value to return if the column is null
     * @return The resulting column after applying the appropriate function or the default value
     */
    @Nonnull
    public Column applyNonNull(Function<Column, Column> arrayFunction,
                               Function<Column, Column> singleFunction,
                               Column defaultValue) {
        return when(column.isNotNull(), apply(arrayFunction, singleFunction))
                .otherwise(defaultValue);
    }

    @Nonnull
    public Column asArray() {
        return applyNonNull(
                Function.identity(),
                functions::array,
                functions.array()
        );
    }

    /**
     * Create a function that always returns a constant value as a literal column.
     *
     * @param constValue The constant value to return
     * @return A function that takes a Column (ignored) and returns a literal Column with the constant value
     */
    @Nonnull
    public static Function<Column, Column> cons(Object constValue) {
        return ign -> lit(constValue);
    }
}
