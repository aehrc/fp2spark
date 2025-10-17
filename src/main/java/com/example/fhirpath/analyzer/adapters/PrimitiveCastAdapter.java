package com.example.fhirpath.analyzer.adapters;

import com.example.fhirpath.analyzer.AdaptationResult;
import com.example.fhirpath.analyzer.TypeAdapter;
import com.example.fhirpath.ir.Cast;
import com.example.fhirpath.typing.PrimitiveType;
import com.example.fhirpath.typing.Type;

import jakarta.annotation.Nonnull;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Adapter for implicit primitive type casts.
 *
 * <p>Implements the widening conversions defined in the type system:
 * <ul>
 *   <li>INTEGER → DECIMAL (cost: 1)
 *   <li>DECIMAL → QUANTITY (cost: 1)
 *   <li>DATE → DATE_TIME (cost: 1)
 * </ul>
 *
 * <p>These are one-step adaptations. Multi-step chains (e.g., INTEGER → QUANTITY)
 * are handled by applying this adapter multiple times in the adaptation graph.
 */
public final class PrimitiveCastAdapter implements TypeAdapter {

    // Direct cast edges in the type hierarchy
    private static final Map<Type, Set<Type>> CAST_EDGES = Map.of(
        PrimitiveType.INTEGER, Set.of(PrimitiveType.DECIMAL),
        PrimitiveType.DECIMAL, Set.of(PrimitiveType.QUANTITY),
        PrimitiveType.DATE, Set.of(PrimitiveType.DATE_TIME)
    );

    @Nonnull
    @Override
    public Set<AdaptationResult> reachableAdaptations(@Nonnull AdaptationResult start) {
        Set<Type> targets = CAST_EDGES.get(start.type());

        if (targets == null || targets.isEmpty()) {
            return Set.of();
        }

        Set<AdaptationResult> results = new HashSet<>();
        for (Type target : targets) {
            // Create Cast IR node and adaptation with cost 1
            Cast castNode = new Cast(start.node(), target);
            results.add(start.withAdaptation(castNode, target, 1));
        }

        return results;
    }
}

