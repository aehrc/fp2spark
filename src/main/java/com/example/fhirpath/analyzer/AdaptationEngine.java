package com.example.fhirpath.analyzer;

import com.example.fhirpath.ir.IRNode;
import com.example.fhirpath.typing.Type;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.*;

/**
 * Engine for computing type adaptations using a graph traversal algorithm.
 *
 * <p>This class implements the core adaptation logic:
 * <ul>
 *   <li><b>Direct adaptation</b>: Find the cheapest path from source to target type
 *   <li><b>Adaptation closure</b>: Find all reachable types from a source
 *   <li><b>Common type finding</b>: Find intersection of two adaptation closures
 * </ul>
 *
 * <p>The algorithm is a breadth-first search (BFS) through the adaptation graph,
 * where each adapter can produce new adaptations from existing results.
 * Costs are additive, and we always keep the cheapest path to each type.
 *
 * <p><b>Example Usage:</b>
 * <pre>
 * AdaptationEngine engine = new AdaptationEngine(List.of(
 *     new PrimitiveCastAdapter(),
 *     new FhirSystemCastAdapter()
 * ));
 *
 * // Direct adaptation: INTEGER → DECIMAL
 * AdaptationResult result = engine.adaptTo(intNode, DECIMAL);
 * // → Cast(intNode, DECIMAL) with cost=1
 *
 * // Adaptation closure: all types reachable from INTEGER
 * Set&lt;AdaptationResult&gt; closure = engine.computeClosure(intNode);
 * // → {INTEGER (cost=0), DECIMAL (cost=1), QUANTITY (cost=2)}
 *
 * // Common type: INTEGER and DECIMAL
 * CommonTypeResult common = engine.findCommonType(intNode, decimalNode);
 * // → DECIMAL (both can adapt to it)
 * </pre>
 */
public final class AdaptationEngine {

    private final List<TypeAdapter> adapters;

    public AdaptationEngine(@Nonnull List<TypeAdapter> adapters) {
        this.adapters = List.copyOf(adapters);
    }

    /**
     * Adapt the given node to the target type, finding the cheapest adaptation path.
     *
     * <p>This performs a BFS through the adaptation graph, stopping as soon as we
     * find the target type. Returns null if no adaptation path exists.
     *
     * @param node the source IR node
     * @param targetType the desired target type
     * @return the adapted result, or null if adaptation is impossible
     */
    @Nullable
    public AdaptationResult adaptTo(@Nonnull IRNode node, @Nonnull Type targetType) {
        AdaptationResult start = AdaptationResult.initial(node);

        // Check if we're already at the target
        if (start.hasType(targetType)) {
            return start;
        }

        // BFS to find target type
        Queue<AdaptationResult> queue = new LinkedList<>();
        Map<Type, AdaptationResult> visited = new HashMap<>();

        queue.add(start);
        visited.put(start.type(), start);

        while (!queue.isEmpty()) {
            AdaptationResult current = queue.poll();

            // Apply all adapters to current result
            for (TypeAdapter adapter : adapters) {
                for (AdaptationResult next : adapter.reachableAdaptations(current)) {
                    Type nextType = next.type();

                    // Found target - return immediately (BFS guarantees cheapest)
                    if (next.hasType(targetType)) {
                        return next;
                    }

                    // Skip if we've seen this type with a cheaper cost
                    AdaptationResult existing = visited.get(nextType);
                    if (existing != null && existing.cost() <= next.cost()) {
                        continue;
                    }

                    // Record this as the best path to nextType and continue search
                    visited.put(nextType, next);
                    queue.add(next);
                }
            }
        }

        // No path found
        return null;
    }

    /**
     * Compute the complete adaptation closure from the given node.
     *
     * <p>This finds all types reachable through any sequence of adaptations,
     * keeping only the cheapest path to each type.
     *
     * <p>The algorithm iterates until a fixed point is reached (no new adaptations).
     *
     * @param node the source IR node
     * @return set of all reachable adaptations with their costs
     */
    @Nonnull
    public Set<AdaptationResult> computeClosure(@Nonnull IRNode node) {
        Map<Type, AdaptationResult> closure = new HashMap<>();
        Queue<AdaptationResult> queue = new LinkedList<>();

        AdaptationResult start = AdaptationResult.initial(node);
        closure.put(start.type(), start);
        queue.add(start);

        while (!queue.isEmpty()) {
            AdaptationResult current = queue.poll();

            // Apply all adapters
            for (TypeAdapter adapter : adapters) {
                for (AdaptationResult next : adapter.reachableAdaptations(current)) {
                    Type nextType = next.type();

                    // Keep only the cheapest path to each type
                    AdaptationResult existing = closure.get(nextType);
                    if (existing == null || next.cost() < existing.cost()) {
                        closure.put(nextType, next);
                        queue.add(next);
                    }
                }
            }
        }

        return new LinkedHashSet<>(closure.values());
    }

    /**
     * Find the common type between two nodes.
     *
     * <p>This computes the adaptation closures for both nodes, finds their intersection,
     * and returns the cheapest common type.
     *
     * <p><b>Example:</b>
     * <pre>
     * // INTEGER: {INTEGER (0), DECIMAL (1), QUANTITY (2)}
     * // DECIMAL: {DECIMAL (0), QUANTITY (1)}
     * // Common: {DECIMAL, QUANTITY}
     * // Result: DECIMAL (cheaper for both: 1+0=1 vs 2+1=3)
     * </pre>
     *
     * @param node1 first node
     * @param node2 second node
     * @return the common adaptation with both nodes adapted to it, or null if none exists
     */
    @Nullable
    public CommonTypeResult findCommonType(@Nonnull IRNode node1, @Nonnull IRNode node2) {
        // Compute closures
        Set<AdaptationResult> closure1 = computeClosure(node1);
        Set<AdaptationResult> closure2 = computeClosure(node2);

        // Build maps for efficient lookup
        Map<Type, AdaptationResult> map1 = new HashMap<>();
        for (AdaptationResult r : closure1) {
            map1.put(r.type(), r);
        }

        Map<Type, AdaptationResult> map2 = new HashMap<>();
        for (AdaptationResult r : closure2) {
            map2.put(r.type(), r);
        }

        // Find intersection and pick the cheapest
        CommonTypeResult best = null;
        int bestTotalCost = Integer.MAX_VALUE;

        for (Type commonType : map1.keySet()) {
            if (map2.containsKey(commonType)) {
                AdaptationResult r1 = map1.get(commonType);
                AdaptationResult r2 = map2.get(commonType);
                int totalCost = r1.cost() + r2.cost();

                if (totalCost < bestTotalCost) {
                    bestTotalCost = totalCost;
                    best = new CommonTypeResult(commonType, r1, r2);
                }
            }
        }

        return best;
    }

    /**
     * Result of finding a common type between two nodes.
     * Contains the common type and the adapted versions of both nodes.
     */
    public record CommonTypeResult(
        @Nonnull Type commonType,
        @Nonnull AdaptationResult adapted1,
        @Nonnull AdaptationResult adapted2
    ) {
        /**
         * Get the total cost (sum of both adaptation costs).
         */
        public int totalCost() {
            return adapted1.cost() + adapted2.cost();
        }
    }
}

