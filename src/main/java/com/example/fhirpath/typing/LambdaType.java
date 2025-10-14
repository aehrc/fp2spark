package com.example.fhirpath.typing;

import jakarta.annotation.Nonnull;

/**
 * Represents a lambda type in the FHIRPath type system.
 * Lambdas are expressions that are evaluated with an implicit $this binding.
 *
 * In FHIRPath, lambda parameter types are always implicit - determined by the
 * collection element type. There is no syntax to declare parameter types.
 * The lambda is checked for compatibility during type resolution.
 *
 * Used by collection operations like where(), select(), repeat() that accept
 * criteria or projection expressions.
 *
 * Example: Collection<T>.where(criteria) expects Lambda(Boolean)
 * Example: Collection<T>.select(projection) expects Lambda(R)
 *
 * @param returnType The type of the lambda body evaluation result
 */
public record LambdaType(
    @Nonnull Type returnType
) implements Type {

    @Override
    @Nonnull
    public String getName() {
        return "Lambda(" + returnType.getName() + ")";
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
        return "Lambda(" + returnType + ")";
    }
}
