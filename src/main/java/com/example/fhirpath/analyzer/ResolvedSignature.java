package com.example.fhirpath.analyzer;

import com.example.fhirpath.typing.Shape;
import com.example.fhirpath.typing.Type;

import jakarta.annotation.Nonnull;
import java.util.List;

/**
 * A resolved signature with concrete, statically-known result shape.
 *
 * <p>This is stored in Operation nodes after type resolution is complete.
 * All ResultSpecs have been evaluated, and the result shape is concrete.
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
     * Create a resolved signature from a definition and resolved arguments.
     */
    @Nonnull
    public static ResolvedSignature resolve(
        @Nonnull SignatureDefinition definition,
        @Nonnull List<com.example.fhirpath.ir.IRNode> resolvedArgs
    ) {
        Type concreteResultType = definition.resultSpec().resolve(resolvedArgs);
        // TODO: Get cardinality from ResultSpec when implementing Phase 2
        // For now, assume single cardinality
        Shape resultShape = Shape.single(concreteResultType);
        return new ResolvedSignature(
            definition.parameterTypes(),
            resultShape,
            definition.minArity()
        );
    }
}
