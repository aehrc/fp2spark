package com.example.fhirpath.analyzer;

import com.example.fhirpath.typing.CollectionType;
import com.example.fhirpath.typing.LambdaType;
import com.example.fhirpath.typing.Type;

import jakarta.annotation.Nonnull;

import java.util.Arrays;
import java.util.List;

/**
 * Factory methods for creating common signature patterns.
 * <p>
 * This class provides convenience methods for the most common signature patterns
 * in FHIRPath, reducing boilerplate in OperationRegistry.
 */
public final class Signatures {
    private Signatures() {
        throw new AssertionError("No instances");
    }

    /**
     * Unary function with arbitrary types (unconstrained).
     * Example: length(String) → Integer
     */
    @Nonnull
    public static SignatureDefinition unaryFunc(
            @Nonnull final Type paramType,
            @Nonnull final Type resultType
    ) {
        return new SignatureDefinition(
                List.of(paramType),
                new ResultSpec.Static(resultType)
        );
    }

    /**
     * Unary operation where input type = result type (T → T).
     * Example: abs(Integer) → Integer
     */
    @Nonnull
    public static SignatureDefinition unaryOp(@Nonnull final Type type) {
        return unaryFunc(type, type);
    }

    /**
     * Binary function with arbitrary types (unconstrained).
     * Example: startsWith(String, String) → Boolean
     */
    @Nonnull
    public static SignatureDefinition binaryFunc(
            @Nonnull final Type leftType,
            @Nonnull final Type rightType,
            @Nonnull final Type resultType
    ) {
        return new SignatureDefinition(
                List.of(leftType, rightType),
                new ResultSpec.Static(resultType)
        );
    }

    /**
     * Binary operation where all types are the same ((T, T) → T).
     * Example: add(Integer, Integer) → Integer
     */
    @Nonnull
    public static SignatureDefinition binaryOp(@Nonnull final Type type) {
        return binaryFunc(type, type, type);
    }

    /**
     * Temporal arithmetic operation ((Temporal, Quantity) → Temporal).
     * Example: Date + Quantity → Date, DateTime + Quantity → DateTime
     * Used for FHIRPath date/time arithmetic operations.
     */
    @Nonnull
    public static SignatureDefinition temporalArithmetic(@Nonnull final Type temporalType) {
        return binaryFunc(temporalType, Type.QUANTITY, temporalType);
    }

    /**
     * Ternary function with arbitrary types (unconstrained).
     * Example: replace(String, String, String) → String
     */
    @Nonnull
    public static SignatureDefinition ternaryFunc(
            @Nonnull final Type firstType,
            @Nonnull final Type secondType,
            @Nonnull final Type thirdType,
            @Nonnull final Type resultType
    ) {
        return new SignatureDefinition(
                List.of(firstType, secondType, thirdType),
                new ResultSpec.Static(resultType)
        );
    }

    /**
     * Comparison operation - always returns Boolean ((T, T) → Boolean).
     * Example: gt(Integer, Integer) → Boolean
     */
    @Nonnull
    public static SignatureDefinition comparisonOp(@Nonnull final Type type) {
        return binaryFunc(type, type, Type.BOOLEAN);
    }

    /**
     * Element extractor - returns effective type of input collection.
     * Example: Collection<T>.first() → T
     */
    @Nonnull
    public static SignatureDefinition elementExtractor(@Nonnull final Type inputType) {
        return new SignatureDefinition(
                List.of(inputType),
                ResultSpec.EffectiveInputType.INSTANCE
        );
    }

    /**
     * Collection preserver - returns same type as input.
     * Example: Collection<T>.where(...) → Collection<T>
     */
    @Nonnull
    public static SignatureDefinition collectionPreserver(
            @Nonnull final Type inputType,
            @Nonnull final Type... additionalParams
    ) {
        final List<Type> params = new java.util.ArrayList<>();
        params.add(inputType);
        params.addAll(Arrays.asList(additionalParams));
        return new SignatureDefinition(
                params,
                ResultSpec.InputType.INSTANCE
        );
    }

    /**
     * Collection aggregator - reduces collection to a single value.
     * Example: Collection<T>.count() → Integer
     * Works on any input type (uses UNKNOWN as placeholder).
     */
    @Nonnull
    public static SignatureDefinition collectionAggregator(
            @Nonnull final Type inputType,
            @Nonnull final Type resultType
    ) {
        return new SignatureDefinition(
                List.of(inputType),
                new ResultSpec.Static(resultType)
        );
    }

    /**
     * Variadic operation with optional parameters.
     * Example: substring(String, Integer, Integer?) - 2 or 3 args
     */
    @Nonnull
    public static SignatureDefinition variadic(
            @Nonnull final List<Type> parameterTypes,
            @Nonnull final Type resultType,
            final int minArity
    ) {
        return new SignatureDefinition(parameterTypes, resultType, minArity);
    }

    /**
     * Variadic operation with optional parameters and ResultSpec.
     * Example: select(Collection<T>, Expression, Expression?) - 1 or 2 additional args
     */
    @Nonnull
    public static SignatureDefinition variadic(
            @Nonnull final List<Type> parameterTypes,
            @Nonnull final ResultSpec resultSpec,
            final int minArity
    ) {
        return new SignatureDefinition(parameterTypes, resultSpec, minArity);
    }


    /**
     * Type test operation - always returns Boolean.
     * Example: is(T, Type) → Boolean
     */
    @Nonnull
    public static SignatureDefinition typeTest(@Nonnull final Type inputType) {
        return new SignatureDefinition(
                List.of(inputType, Type.STRING),
                Type.BOOLEAN
        );
    }

    /**
     * FHIR getValue operation - converts FHIR type to system type.
     * Example: FhirType(STRING).getValue() → String
     */
    @Nonnull
    public static SignatureDefinition fhirValueExtractor(@Nonnull final Type fhirType) {
        return new SignatureDefinition(
                List.of(fhirType),
                ResultSpec.FhirSystemType.INSTANCE
        );
    }

    /**
     * Collection operation with lambda predicate - preserves collection type.
     * Example: Collection<T>.where(Lambda(Boolean)) → Collection<T>
     * <p>
     * The lambda takes an implicit element of type T (from collection) and returns Boolean (filter criteria).
     * The lambda parameter type is implicit and determined by the collection element type.
     * Uses ELEMENT_WISE binding strategy: $this = element type.
     */
    @Nonnull
    public static SignatureDefinition collectionFilter(@Nonnull final Type elementType) {
        //final CollectionType collectionType = new CollectionType(elementType);
        final LambdaType lambdaType = new LambdaType(Type.BOOLEAN);

        return new SignatureDefinition(
                // TODO: use CollectionType as first param when we support it in overload resolution
                List.of(Type.ANY, lambdaType),
                ResultSpec.InputType.INSTANCE,  // Preserve collection type
                2,  // minArity
                LambdaBindingStrategy.ELEMENT_WISE  // $this = element
        );
    }

    /**
     * Collection transformation - maps elements to new type.
     * Example: Collection<T>.select(Expression) → Collection<U>
     * The result type depends on the expression parameter.
     */
    @Nonnull
    public static SignatureDefinition collectionMap(
            @Nonnull final Type inputType,
            @Nonnull final Type expressionType,
            @Nonnull final Type resultElementType
    ) {
        return new SignatureDefinition(
                List.of(inputType, expressionType),
                new ResultSpec.Static(resultElementType)
        );
    }

    /**
     * Union operation - combines two collections.
     * Example: Collection<T> | Collection<T> → Collection<T>
     */
    @Nonnull
    public static SignatureDefinition union(
            @Nonnull final Type leftType,
            @Nonnull final Type rightType
    ) {
        return new SignatureDefinition(
                List.of(leftType, rightType),
                ResultSpec.InputType.INSTANCE
        );
    }

    /**
     * Conditional iif operation - collection-level conditional with lambda parameters.
     * Example: Collection<T>.iif(Lambda<Boolean>, Lambda<R>) → R
     * <p>
     * Both lambda parameters operate on the entire collection (not elements):
     * - criterion: $this = Collection<T>, returns Boolean
     * - true-result: $this = Collection<T>, returns R (any type - collection or scalar)
     * <p>
     * Uses COLLECTION_WISE binding strategy: $this = entire collection type.
     * Result type is extracted from the true-result lambda's body type.
     * Scalar results are implicitly treated as singleton collections when needed.
     */
    @Nonnull
    public static SignatureDefinition conditionalIif() {
        return new SignatureDefinition(
                List.of(
                        new CollectionType(Type.ANY),      // Collection<T>
                        new LambdaType(Type.BOOLEAN),      // criterion lambda returns Boolean
                        new LambdaType(Type.ANY)           // true-result lambda returns R (any type)
                ),
                new ResultSpec.ArgumentType(2),  // Result is type of arg[2] (unwrapped from lambda)
                3,  // minArity = 3 (all required for non-variadic version)
                LambdaBindingStrategy.COLLECTION_WISE  // $this = entire collection
        );
    }
}
