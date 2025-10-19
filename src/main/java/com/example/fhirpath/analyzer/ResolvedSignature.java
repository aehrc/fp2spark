package com.example.fhirpath.analyzer;

import com.example.fhirpath.typing.Shape;
import com.example.fhirpath.typing.Type;

import jakarta.annotation.Nonnull;
import java.util.List;

/**
 * A resolved signature with concrete, statically-known result shape.
 *
 * <p>This is stored in Operation nodes after type resolution is complete.
 * All type variables have been substituted, and the result shape is concrete.
 */
public record ResolvedSignature(
    @Nonnull List<Type> parameterTypes,
    @Nonnull Shape resultShape,
    int minArity
) {
    /**
     * Convenience constructor for minimal signatures.
     */
    public ResolvedSignature(
        @Nonnull List<Type> parameterTypes,
        @Nonnull Shape resultShape
    ) {
        this(parameterTypes, resultShape, parameterTypes.size());
    }

    /**
     * Returns the result type (element type of the result shape).
     * Convenience method for backward compatibility.
     */
    @Nonnull
    public Type resultType() {
        return resultShape.elementType();
    }

    public int arity() {
        return parameterTypes.size();
    }

    /**
     * Create a resolved signature from a Phase 1 signature definition.
     * In Phase 1, the result shape may be static or dynamic (computed from arguments).
     *
     * @param definition the signature definition
     * @param resolvedArgs the resolved arguments (needed for dynamic type resolution)
     * @return resolved signature with concrete result shape
     */
    @Nonnull
    public static ResolvedSignature fromDefinition(
            @Nonnull SignatureDefinition definition,
            @Nonnull List<com.example.fhirpath.ir.IRNode> resolvedArgs
    ) {
        // Extract parameter types (without cardinality)
        List<Type> paramTypes = definition.parameters().stream()
            .map(ParamSpec::type)
            .toList();

        // Resolve result shape (static or dynamic)
        Shape resultShape = definition.resultSpec().resolve(resolvedArgs);

        return new ResolvedSignature(paramTypes, resultShape, definition.minArity());
    }

}
