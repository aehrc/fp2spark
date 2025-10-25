package com.example.fhirpath.codegen.spark.handler;

import jakarta.annotation.Nonnull;

/**
 * Represents a function that accepts three arguments and produces a result.
 * <p>
 * This is the three-arity specialization of Function.
 *
 * @param <T> the type of the first argument to the function
 * @param <U> the type of the second argument to the function
 * @param <V> the type of the third argument to the function
 * @param <R> the type of the result of the function
 */
@FunctionalInterface
public interface TriFunction<T, U, V, R> {

    /**
     * Applies this function to the given arguments.
     *
     * @param t the first function argument
     * @param u the second function argument
     * @param v the third function argument
     * @return the function result
     */
    @Nonnull
    R apply(@Nonnull T t, @Nonnull U u, @Nonnull V v);
}
