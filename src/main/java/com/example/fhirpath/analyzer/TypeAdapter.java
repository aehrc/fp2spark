package com.example.fhirpath.analyzer;

import com.example.fhirpath.ir.IRNode;
import com.example.fhirpath.typing.Type;

import jakarta.annotation.Nonnull;
import java.util.Set;

/**
 * Strategy for adapting types through implicit conversions.
 *
 * <p>TypeAdapters form a chain of adaptation rules. Given an {@link AdaptationResult},
 * each adapter can produce zero or more new adaptations by applying its conversion rule.
 *
 * <p>The adaptation process is a graph traversal where:
 * <ul>
 *   <li>Nodes are {@link AdaptationResult} (IR node + type + cost)
 *   <li>Edges are adaptation rules that transform one result into another
 *   <li>Costs are additive along paths
 *   <li>We search for the shortest path to a target type
 * </ul>
 *
 * <p><b>Example:</b>
 * <pre>
 * // Starting point: INTEGER node
 * AdaptationResult start = AdaptationResult.initial(intNode);
 *
 * // Apply PrimitiveCastAdapter
 * Set&lt;AdaptationResult&gt; nextLevel = adapter.reachableAdaptations(start);
 * // → {DECIMAL (cost=1), QUANTITY (cost=1)}
 *
 * // Apply again to DECIMAL result
 * Set&lt;AdaptationResult&gt; nextLevel2 = adapter.reachableAdaptations(decimalResult);
 * // → {QUANTITY (cost=2)}  // total cost is additive
 * </pre>
 *
 * <p>This design enables:
 * <ul>
 *   <li><b>Adaptation closure</b>: Apply repeatedly until fixed point
 *   <li><b>Direct adaptation</b>: Search until target type found
 *   <li><b>Common type finding</b>: Compute closures and find intersection
 *   <li><b>Chaining</b>: Results of one adapter feed into another
 * </ul>
 */
public interface TypeAdapter {

    /**
     * Compute all reachable adaptations from the given starting point.
     *
     * <p>This method applies this adapter's conversion rule to produce new adaptations.
     * Each result includes:
     * <ul>
     *   <li>The adapted IR node (with cast nodes inserted if needed)
     *   <li>The resulting type
     *   <li>The cumulative cost (start cost + adaptation cost)
     * </ul>
     *
     * <p><b>Contract:</b>
     * <ul>
     *   <li>If this adapter doesn't apply, return empty set
     *   <li>Otherwise, return all direct adaptations this rule can produce
     *   <li>Do NOT recurse - caller handles iteration to fixed point
     *   <li>Costs must be additive: {@code result.cost = start.cost + edgeCost}
     * </ul>
     *
     * <p><b>Example Implementations:</b>
     * <pre>
     * // PrimitiveCastAdapter: INTEGER → DECIMAL
     * reachableAdaptations(AdaptationResult(intNode, INTEGER, cost=0))
     *   → { AdaptationResult(Cast(intNode, DECIMAL), DECIMAL, cost=1) }
     *
     * // FhirSystemCastAdapter: FhirType(INTEGER) → INTEGER
     * reachableAdaptations(AdaptationResult(fhirNode, FhirType(INTEGER), cost=0))
     *   → { AdaptationResult(CastToSystem(fhirNode), INTEGER, cost=1) }
     *
     * // ExactMatchAdapter: T → T (identity)
     * reachableAdaptations(any)
     *   → { } // No new adaptations (already at starting type)
     * </pre>
     *
     * @param start the starting adaptation (IR node + type + cumulative cost)
     * @return all adaptations reachable in one step from start, with updated costs
     */
    @Nonnull
    Set<AdaptationResult> reachableAdaptations(@Nonnull AdaptationResult start);

    /**
     * Returns a human-readable name for this adapter (for debugging).
     */
    @Nonnull
    default String getName() {
        return getClass().getSimpleName();
    }
}

