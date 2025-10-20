package com.example.fhirpath.operation.signature;

import com.example.fhirpath.typing.Cardinality;
import com.example.fhirpath.typing.LambdaType;
import com.example.fhirpath.typing.Shape;
import com.example.fhirpath.typing.Type;
import com.example.fhirpath.typing.Types;
import jakarta.annotation.Nonnull;

import java.util.List;

import static com.example.fhirpath.operation.signature.ParamSpec.many;
import static com.example.fhirpath.operation.signature.ParamSpec.single;

/**
 * Phase 1 signature factory methods with explicit cardinality.
 *
 * <p>Phase 1 uses ParamSpec and ResultTypeSpec to explicitly specify
 * type and cardinality for each parameter and result.
 */
public final class Signatures {
    private Signatures() {
        throw new AssertionError("No instances");
    }

    /**
     * Unary function: ?T → ?R
     * Example: length(?STRING) → ?INTEGER
     */
    @Nonnull
    public static SignatureDefinition unaryFunc(
            @Nonnull final Type paramType,
            @Nonnull final Type resultType
    ) {
        return new SignatureDefinition(
                List.of(single(paramType)),
                ResultTypeSpec.single(resultType)
        );
    }

    /**
     * Unary operation where input type = result type: ?T → ?T
     * Example: abs(?INTEGER) → ?INTEGER
     */
    @Nonnull
    public static SignatureDefinition unaryOp(@Nonnull final Type type) {
        return unaryFunc(type, type);
    }

    /**
     * Binary function: (?T1, ?T2) → ?R
     * Example: startsWith(?STRING, ?STRING) → ?BOOLEAN
     */
    @Nonnull
    public static SignatureDefinition binaryFunc(
            @Nonnull final Type leftType,
            @Nonnull final Type rightType,
            @Nonnull final Type resultType
    ) {
        return new SignatureDefinition(
                List.of(single(leftType), single(rightType)),
                ResultTypeSpec.single(resultType)
        );
    }

    /**
     * Binary operation where all types are the same: (?T, ?T) → ?T
     * Example: add(?INTEGER, ?INTEGER) → ?INTEGER
     */
    @Nonnull
    public static SignatureDefinition binaryOp(@Nonnull final Type type) {
        return binaryFunc(type, type, type);
    }

    /**
     * Temporal arithmetic operation: (?Temporal, ?Quantity) → ?Temporal
     * Example: ?DATE + ?QUANTITY → ?DATE
     */
    @Nonnull
    public static SignatureDefinition temporalArithmetic(@Nonnull final Type temporalType) {
        return binaryFunc(temporalType, Types.QUANTITY, temporalType);
    }

    /**
     * Ternary function: (?T1, ?T2, ?T3) → ?R
     * Example: replace(?STRING, ?STRING, ?STRING) → ?STRING
     */
    @Nonnull
    public static SignatureDefinition ternaryFunc(
            @Nonnull final Type firstType,
            @Nonnull final Type secondType,
            @Nonnull final Type thirdType,
            @Nonnull final Type resultType
    ) {
        return new SignatureDefinition(
                List.of(single(firstType), single(secondType), single(thirdType)),
                ResultTypeSpec.single(resultType)
        );
    }

    /**
     * Comparison operation: (?T, ?T) → ?BOOLEAN
     * Example: gt(?INTEGER, ?INTEGER) → ?BOOLEAN
     */
    @Nonnull
    public static SignatureDefinition comparisonOp(@Nonnull final Type type) {
        return binaryFunc(type, type, Types.BOOLEAN);
    }

    /**
     * Element extractor from collection: *T → ?T
     * Example: *T.first() → ?T
     *
     * <p>Phase 1 workaround: Uses dynamic result type resolution (ResultTypeSpec.effectiveInputType)
     * to extract the element type from the input collection, since we don't have type variables yet.
     */
    @Nonnull
    public static SignatureDefinition elementExtractor(@Nonnull final Type elementType) {
        return new SignatureDefinition(
                List.of(many(elementType)),
                ResultTypeSpec.effectiveInputType(Cardinality.SINGLE)  // Dynamic: extract element type with SINGLE cardinality
        );
    }

    /**
     * Collection preserver: (*T, ...) → *T
     * Preserves MANY cardinality.
     * Example: *T.where(Lambda) → *T
     */
    @Nonnull
    public static SignatureDefinition collectionPreserver(
            @Nonnull final Type elementType,
            @Nonnull final ParamSpec... additionalParams
    ) {
        final List<ParamSpec> params = new java.util.ArrayList<>();
        params.add(many(elementType));
        params.addAll(java.util.Arrays.asList(additionalParams));
        return new SignatureDefinition(
                params,
                ResultTypeSpec.many(elementType)
        );
    }

    /**
     * Collection aggregator: *T → ?R
     * Reduces collection to single value.
     * Example: *T.count() → ?INTEGER
     */
    @Nonnull
    public static SignatureDefinition collectionAggregator(
            @Nonnull final Type inputType,
            @Nonnull final Type resultType
    ) {
        return new SignatureDefinition(
                List.of(many(inputType)),
                ResultTypeSpec.single(resultType)
        );
    }

    /**
     * Variadic operation with optional parameters.
     * Example: substring(?STRING, ?INTEGER, ?INTEGER) with minArity=2
     */
    @Nonnull
    public static SignatureDefinition variadic(
            @Nonnull final List<ParamSpec> parameters,
            @Nonnull final ResultTypeSpec resultSpec,
            final int minArity
    ) {
        return new SignatureDefinition(parameters, resultSpec, minArity);
    }

    /**
     * Type test operation: (?T, ?STRING) → ?BOOLEAN
     * Example: is(?T, ?STRING) → ?BOOLEAN
     */
    @Nonnull
    public static SignatureDefinition typeTest(@Nonnull final Type inputType) {
        return new SignatureDefinition(
                List.of(single(inputType), single(Types.STRING)),
                ResultTypeSpec.single(Types.BOOLEAN)
        );
    }

    /**
     * Collection filter with lambda: (*T, ?Lambda(?BOOLEAN)) → *T
     * Example: *T.where(?Lambda(?BOOLEAN)) → *T
     *
     * <p>Uses ELEMENT_WISE binding: $this = T (element type)
     *
     * <p>Phase 1 workaround: Uses dynamic result type resolution (ResultTypeSpec.inputType)
     * to preserve the input element type, since we don't have type variables yet.
     */
    @Nonnull
    public static SignatureDefinition collectionFilter(@Nonnull final Type elementType) {
        // Lambda expects single BOOLEAN result
        final LambdaType lambdaType = new LambdaType(Shape.single(Types.BOOLEAN));

        // Use dynamic result resolution to preserve input type
        return new SignatureDefinition(
                List.of(many(elementType), single(lambdaType)),
                ResultTypeSpec.inputType(Cardinality.MANY),  // Dynamic: preserve input type with MANY cardinality
                2,  // minArity
                LambdaBindingStrategy.ELEMENT_WISE  // $this = element
        );
    }

    /**
     * Union operation: (*T, *T) → *T
     * Example: *INTEGER | *INTEGER → *INTEGER
     *
     * <p>Phase 1 limitation: both sides must have same type.
     * Phase 2 will add type variable support for mixed types.
     */
    @Nonnull
    public static SignatureDefinition union(@Nonnull final Type elementType) {
        return new SignatureDefinition(
                List.of(many(elementType), many(elementType)),
                ResultTypeSpec.many(elementType)
        );
    }

    /**
     * Conditional iif operation: (*T, ?Lambda(?BOOLEAN), ?Lambda(S)) → S
     *
     * <p>Uses COLLECTION_WISE binding: $this = *T (entire collection)
     *
     * <p>Phase 1 workaround: Uses dynamic result shape resolution (ResultTypeSpec.lambdaBodyType)
     * to extract the result shape (type + cardinality) from the "then" lambda's body,
     * since we don't have type variables yet.
     *
     * <p>The result shape S is whatever the lambda body returns:
     * <ul>
     *   <li>If lambda returns ?R (single), result is ?R</li>
     *   <li>If lambda returns *R (many), result is *R</li>
     * </ul>
     *
     * <p>Phase 2 will add type variables for more precise type checking.
     */
    @Nonnull
    public static SignatureDefinition conditionalIif(@Nonnull final Type inputType, @Nonnull final Type resultType) {
        return new SignatureDefinition(
                List.of(
                        many(inputType),
                        single(new LambdaType(Shape.single(Types.BOOLEAN))),
                        single(new LambdaType(Shape.single(resultType)))
                ),
                // Dynamic: extract result shape from lambda at argument index 2 (the "then" lambda)
                ResultTypeSpec.lambdaBodyType(2),
                3,
                LambdaBindingStrategy.COLLECTION_WISE
        );
    }
}
