package com.example.fhirpath.analyzer;

import com.example.fhirpath.typing.Type;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.List;

/**
 * Factory methods for creating common signature patterns.
 *
 * This class provides convenience methods for the most common signature patterns
 * in FHIRPath, reducing boilerplate in OperationRegistry.
 */
public final class Signatures {
    private Signatures() {
        throw new AssertionError("No instances");
    }

    /**
     * Unary operation with static result type.
     * Example: abs(Integer) → Integer
     */
    @Nonnull
    public static SignatureDefinition unaryOp(
        @Nonnull Type paramType,
        @Nonnull Type resultType
    ) {
        return new SignatureDefinition(
            List.of(paramType),
            new ResultSpec.Static(resultType)
        );
    }

    /**
     * Binary operation with static result type.
     * Example: add(Integer, Integer) → Integer
     */
    @Nonnull
    public static SignatureDefinition binaryOp(
        @Nonnull Type leftType,
        @Nonnull Type rightType,
        @Nonnull Type resultType
    ) {
        return new SignatureDefinition(
            List.of(leftType, rightType),
            new ResultSpec.Static(resultType)
        );
    }

    /**
     * Comparison operation - always returns Boolean.
     * Example: gt(Integer, Integer) → Boolean
     */
    @Nonnull
    public static SignatureDefinition comparisonOp(
        @Nonnull Type leftType,
        @Nonnull Type rightType
    ) {
        return binaryOp(leftType, rightType, Type.BOOLEAN);
    }

    /**
     * Element extractor - returns effective type of input collection.
     * Example: Collection<T>.first() → T
     */
    @Nonnull
    public static SignatureDefinition elementExtractor(@Nonnull Type inputType) {
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
        @Nonnull Type inputType,
        @Nonnull Type... additionalParams
    ) {
        List<Type> params = new java.util.ArrayList<>();
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
        @Nonnull Type inputType,
        @Nonnull Type resultType
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
        @Nonnull List<Type> parameterTypes,
        @Nonnull Type resultType,
        int minArity
    ) {
        return new SignatureDefinition(parameterTypes, resultType, minArity);
    }

    /**
     * Variadic operation with optional parameters and ResultSpec.
     * Example: select(Collection<T>, Expression, Expression?) - 1 or 2 additional args
     */
    @Nonnull
    public static SignatureDefinition variadic(
        @Nonnull List<Type> parameterTypes,
        @Nonnull ResultSpec resultSpec,
        int minArity
    ) {
        return new SignatureDefinition(parameterTypes, resultSpec, minArity);
    }

    /**
     * String operation with static String result.
     * Example: substring(String, Integer) → String
     */
    @Nonnull
    public static SignatureDefinition stringOp(Type... paramTypes) {
        return new SignatureDefinition(
            Arrays.asList(paramTypes),
            Type.STRING
        );
    }

    /**
     * Type test operation - always returns Boolean.
     * Example: is(T, Type) → Boolean
     */
    @Nonnull
    public static SignatureDefinition typeTest(@Nonnull Type inputType) {
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
    public static SignatureDefinition fhirValueExtractor(@Nonnull Type fhirType) {
        return new SignatureDefinition(
            List.of(fhirType),
            ResultSpec.FhirSystemType.INSTANCE
        );
    }

    /**
     * Collection operation with predicate - preserves collection type.
     * Example: Collection<T>.where(Boolean) → Collection<T>
     */
    @Nonnull
    public static SignatureDefinition collectionFilter(@Nonnull Type collectionType) {
        return collectionPreserver(collectionType, Type.BOOLEAN);
    }

    /**
     * Collection transformation - maps elements to new type.
     * Example: Collection<T>.select(Expression) → Collection<U>
     * The result type depends on the expression parameter.
     */
    @Nonnull
    public static SignatureDefinition collectionMap(
        @Nonnull Type inputType,
        @Nonnull Type expressionType,
        @Nonnull Type resultElementType
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
        @Nonnull Type leftType,
        @Nonnull Type rightType
    ) {
        return new SignatureDefinition(
            List.of(leftType, rightType),
            ResultSpec.InputType.INSTANCE
        );
    }
}
