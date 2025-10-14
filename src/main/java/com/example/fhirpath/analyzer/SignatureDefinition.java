package com.example.fhirpath.analyzer;

import com.example.fhirpath.typing.LambdaType;
import com.example.fhirpath.typing.Type;

import jakarta.annotation.Nonnull;
import java.util.List;
import java.util.stream.Stream;

/**
 * Signature definition for registration purposes.
 * Contains parameter types and a ResultSpec for determining result type.
 *
 * This is used in the registry to define available function overloads.
 * During resolution, this is converted to a ResolvedSignature with concrete type.
 *
 * Implements TypeGroup to enable zero-overhead usage in registry:
 * a SignatureDefinition IS a TypeGroup that expands to itself.
 */
public record SignatureDefinition(
    @Nonnull List<Type> parameterTypes,
    @Nonnull ResultSpec resultSpec,
    int minArity
) implements TypeGroup {
    /**
     * Constructor for fixed arity signatures.
     */
    public SignatureDefinition(
        @Nonnull final List<Type> parameterTypes,
        @Nonnull final ResultSpec resultSpec
    ) {
        this(parameterTypes, resultSpec, parameterTypes.size());
    }

    /**
     * Convenience constructor for static result types (most common case).
     */
    public SignatureDefinition(
        @Nonnull final List<Type> parameterTypes,
        @Nonnull final Type resultType,
        final int minArity
    ) {
        this(parameterTypes, new ResultSpec.Static(resultType), minArity);
    }

    public SignatureDefinition(
        @Nonnull final List<Type> parameterTypes,
        @Nonnull final Type resultType
    ) {
        this(parameterTypes, new ResultSpec.Static(resultType));
    }

    public int arity() {
        return parameterTypes.size();
    }

    /**
     * TypeGroup implementation: a signature expands to itself.
     * This enables zero-overhead usage in registry - no wrapper needed.
     */
    @Nonnull
    @Override
    public Stream<SignatureDefinition> expand() {
        return Stream.of(this);
    }

    /**
     * Returns true if any parameter type is a LambdaType.
     */
    public boolean hasLambdaParameters() {
        return parameterTypes.stream().anyMatch(t -> t instanceof LambdaType);
    }

    /**
     * Returns true if this signature can be applied to the given number of arguments.
     * A signature matches if argCount is within [minArity, arity].
     */
    public boolean canApplyToArgumentCount(final int argCount) {
        return argCount >= minArity && argCount <= arity();
    }
}
