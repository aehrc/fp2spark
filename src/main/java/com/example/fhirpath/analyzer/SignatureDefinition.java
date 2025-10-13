package com.example.fhirpath.analyzer;

import com.example.fhirpath.typing.Type;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * Signature definition for registration purposes.
 * Contains parameter types and a ResultSpec for determining result type.
 *
 * This is used in the registry to define available function overloads.
 * During resolution, this is converted to a ResolvedSignature with concrete type.
 */
public record SignatureDefinition(
    @Nonnull List<Type> parameterTypes,
    @Nonnull ResultSpec resultSpec,
    int minArity
) {
    /**
     * Constructor for fixed arity signatures.
     */
    public SignatureDefinition(
        @Nonnull List<Type> parameterTypes,
        @Nonnull ResultSpec resultSpec
    ) {
        this(parameterTypes, resultSpec, parameterTypes.size());
    }

    /**
     * Convenience constructor for static result types (most common case).
     */
    public SignatureDefinition(
        @Nonnull List<Type> parameterTypes,
        @Nonnull Type resultType,
        int minArity
    ) {
        this(parameterTypes, new ResultSpec.Static(resultType), minArity);
    }

    public SignatureDefinition(
        @Nonnull List<Type> parameterTypes,
        @Nonnull Type resultType
    ) {
        this(parameterTypes, new ResultSpec.Static(resultType));
    }

    public int arity() {
        return parameterTypes.size();
    }
}
