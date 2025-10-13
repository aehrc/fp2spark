package com.example.fhirpath.analyzer;

import com.example.fhirpath.typing.Type;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * A resolved signature with concrete, statically-known result type.
 *
 * This is stored in Operation nodes after type resolution is complete.
 * All ResultSpecs have been evaluated, and the result type is concrete.
 */
public record ResolvedSignature(
    @Nonnull List<Type> parameterTypes,
    @Nonnull Type resultType,
    int minArity
) {
    public ResolvedSignature(
        @Nonnull List<Type> parameterTypes,
        @Nonnull Type resultType
    ) {
        this(parameterTypes, resultType, parameterTypes.size());
    }

    public int arity() {
        return parameterTypes.size();
    }

    /**
     * Create a resolved signature from a definition and resolved arguments.
     */
    @Nonnull
    public static ResolvedSignature resolve(
        @Nonnull SignatureDefinition definition,
        @Nonnull List<com.example.fhirpath.ir.IRNode> resolvedArgs
    ) {
        Type concreteResultType = definition.resultSpec().resolve(resolvedArgs);
        return new ResolvedSignature(
            definition.parameterTypes(),
            concreteResultType,
            definition.minArity()
        );
    }
}
