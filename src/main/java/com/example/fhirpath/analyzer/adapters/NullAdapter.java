package com.example.fhirpath.analyzer.adapters;

import com.example.fhirpath.analyzer.AdaptationResult;
import com.example.fhirpath.analyzer.TypeAdapter;
import com.example.fhirpath.ir.Literal;
import com.example.fhirpath.typing.PrimitiveType;
import com.example.fhirpath.typing.Type;

import jakarta.annotation.Nonnull;
import java.util.Set;

/**
 * Adapter for NULL polymorphism (NULL → T for any T).
 *
 * <p>Per the type system specification, NULL (empty collection) can adapt to any
 * OrdinaryType with cost 0. This is a free adaptation.
 *
 * <p><b>Examples:</b>
 * <ul>
 *   <li>NULL → INTEGER (cost: 0)
 *   <li>NULL → [STRING] (cost: 0)
 *   <li>NULL → ComplexType (cost: 0)
 * </ul>
 *
 * <p><b>Note:</b> This adapter is special because it can adapt to <i>any</i> type.
 * In practice, it's used during overload resolution when the target type is known.
 * For closure computation, we don't enumerate all possible types (infinite set).
 */
public final class NullAdapter implements TypeAdapter {

    @Nonnull
    @Override
    public Set<AdaptationResult> reachableAdaptations(@Nonnull AdaptationResult start) {
        Type sourceType = start.type();

        // NULL can adapt to any type, but we can't enumerate all types here
        // This adapter is primarily used in direct adaptation (adaptTo method)
        // where the target type is known

        // For closure computation, NULL just stays as NULL
        // The actual adaptation happens in OverloadResolver when matching signatures

        if (sourceType == PrimitiveType.NULL) {
            // We could add common targets here if needed, but typically
            // NULL adaptation is handled specially in OverloadResolver
            return Set.of();
        }

        return Set.of();
    }

    /**
     * Special method for adapting NULL to a specific target type.
     * This should be called directly by OverloadResolver.
     *
     * @param start the NULL adaptation
     * @param targetType the desired target type
     * @return adaptation with NULL retyped to targetType
     */
    @Nonnull
    public static AdaptationResult adaptNullTo(@Nonnull AdaptationResult start, @Nonnull Type targetType) {
        if (start.type() != PrimitiveType.NULL) {
            throw new IllegalArgumentException("Can only adapt NULL type");
        }

        // Create a new Literal with the target type
        Literal nullLiteral = new Literal(null, targetType);
        return start.withAdaptation(nullLiteral, targetType, 0);
    }
}

