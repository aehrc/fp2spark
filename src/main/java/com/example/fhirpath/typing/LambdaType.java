package com.example.fhirpath.typing;

import jakarta.annotation.Nonnull;

/**
 * Represents a lambda type in the FHIRPath type system.
 * Lambdas are functions that take parameters and return a result.
 *
 * Used by collection operations like where(), select(), repeat() that accept criteria or projection expressions.
 *
 * Example: where() takes Lambda(T, Boolean) - a function from element type T to Boolean
 * Example: select() takes Lambda(T, R) - a function from element type T to result type R
 *
 * @param parameterType The type of the lambda parameter ($this)
 * @param returnType The type of the lambda body evaluation result
 */
public record LambdaType(
    @Nonnull Type parameterType,
    @Nonnull Type returnType
) implements Type {

    @Override
    @Nonnull
    public String getName() {
        return "Lambda(" + parameterType.getName() + " -> " + returnType.getName() + ")";
    }

    @Override
    public boolean isPrimitive() {
        return false;  // Lambdas are not primitives
    }

    @Override
    public boolean isComplex() {
        return false;  // Lambdas are not complex types
    }

    @Override
    public boolean isCollection() {
        return false;  // Lambdas are not collections
    }

    @Override
    @Nonnull
    public Type effectiveType() {
        // Lambda's effective type is itself - it's not a collection
        return this;
    }

    @Override
    public String toString() {
        return "Lambda(" + parameterType + " -> " + returnType + ")";
    }
}
