package com.example.fhirpath.analyzer.adapters;

import com.example.fhirpath.analyzer.AdaptationEngine;
import com.example.fhirpath.analyzer.AdaptationResult;
import com.example.fhirpath.analyzer.TypeAdapter;
import com.example.fhirpath.ir.Cast;
import com.example.fhirpath.typing.CollectionType;
import com.example.fhirpath.typing.Type;

import jakarta.annotation.Nonnull;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Adapter for collection element type covariance ([T] → [U] if T → U).
 *
 * <p>If element type T can be cast to U, then Collection[T] can adapt to Collection[U]
 * with the same cost as the element adaptation.
 *
 * <p><b>Examples:</b>
 * <ul>
 *   <li>[INTEGER] → [DECIMAL] (cost: 1, via INTEGER → DECIMAL)
 *   <li>[FhirType(STRING)] → [STRING] (cost: 1, via unwrap)
 * </ul>
 *
 * <p><b>Implementation Note:</b> This adapter needs access to other adapters to
 * determine what element type adaptations are possible. It uses an {@link AdaptationEngine}
 * to compute element type adaptations.
 */
public final class CollectionCovarianceAdapter implements TypeAdapter {

    private final AdaptationEngine elementEngine;

    /**
     * Create a collection covariance adapter.
     *
     * @param elementAdapters adapters to use for element type adaptation
     */
    public CollectionCovarianceAdapter(@Nonnull List<TypeAdapter> elementAdapters) {
        this.elementEngine = new AdaptationEngine(elementAdapters);
    }

    @Nonnull
    @Override
    public Set<AdaptationResult> reachableAdaptations(@Nonnull AdaptationResult start) {
        Type sourceType = start.type();

        if (!(sourceType instanceof CollectionType sourceCollection)) {
            return Set.of();
        }

        Type elementType = sourceCollection.elementType();

        // We need a "dummy" IR node to compute element type adaptations
        // In practice, the IR transformation for collection element adaptation
        // is more complex and happens in code generation

        // For now, we identify reachable collection types but note that
        // the actual IR transformation is simplified

        Set<AdaptationResult> results = new HashSet<>();

        // Compute what element types are reachable (using a synthetic node)
        // Note: This is a simplification - in practice we'd need better handling
        Set<AdaptationResult> elementAdaptations = elementEngine.computeClosure(start.node());

        for (AdaptationResult elementResult : elementAdaptations) {
            Type targetElementType = elementResult.type();

            // Skip if it's the same element type (already handled by identity)
            if (targetElementType.equals(elementType)) {
                continue;
            }

            // Create Collection[U] from element adaptation cost
            CollectionType targetCollection = new CollectionType(targetElementType);

            // IR transformation: wrap in Cast (simplified)
            Cast castNode = new Cast(start.node(), targetCollection);

            results.add(start.withAdaptation(castNode, targetCollection, elementResult.cost()));
        }

        return results;
    }
}

