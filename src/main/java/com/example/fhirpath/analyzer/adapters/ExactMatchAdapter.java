package com.example.fhirpath.analyzer.adapters;

import com.example.fhirpath.analyzer.AdaptationResult;
import com.example.fhirpath.analyzer.TypeAdapter;

import jakarta.annotation.Nonnull;
import java.util.Set;

/**
 * Adapter for exact type matches (identity adaptation).
 *
 * <p>This adapter produces no new adaptations since a type always matches itself
 * with cost 0. The identity case is handled implicitly by {@link AdaptationResult#initial}.
 *
 * <p>This adapter exists primarily for completeness and documentation purposes.
 */
public final class ExactMatchAdapter implements TypeAdapter {

    @Nonnull
    @Override
    public Set<AdaptationResult> reachableAdaptations(@Nonnull AdaptationResult start) {
        // Identity is already represented by the start node
        // No additional adaptations needed
        return Set.of();
    }
}

