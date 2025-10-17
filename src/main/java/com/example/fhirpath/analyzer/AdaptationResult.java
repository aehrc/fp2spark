package com.example.fhirpath.analyzer;

import com.example.fhirpath.ir.IRNode;
import com.example.fhirpath.typing.Type;

import jakarta.annotation.Nonnull;
import java.util.Objects;

/**
 * Result of a type adaptation, tracking the adapted IR node, resulting type, and cumulative cost.
 *
 * <p>This is a node in the adaptation graph. It represents a state in the type adaptation
 * process, where we have:
 * <ul>
 *   <li>An IR node (possibly wrapped in Cast nodes)
 *   <li>The current type
 *   <li>The cumulative cost to reach this state
 * </ul>
 *
 * <p>Costs are additive as we chain adaptations:
 * <pre>
 * INTEGER (cost=0)
 *   → DECIMAL (cost=1)     // +1 for primitive cast
 *   → QUANTITY (cost=2)    // +1 for another cast
 * </pre>
 *
 * <p>Two results are considered equal if they have the same type, regardless of cost.
 * This allows us to keep only the cheapest path to each type when computing closures.
 */
public final class AdaptationResult {

    @Nonnull
    private final IRNode node;

    @Nonnull
    private final Type type;

    private final int cost;

    private AdaptationResult(@Nonnull IRNode node, @Nonnull Type type, int cost) {
        this.node = Objects.requireNonNull(node, "node");
        this.type = Objects.requireNonNull(type, "type");
        this.cost = cost;

        if (cost < 0) {
            throw new IllegalArgumentException("Cost cannot be negative: " + cost);
        }
    }

    /**
     * Create an initial adaptation result with cost 0.
     * This is the starting point for adaptation graph traversal.
     *
     * @param node the IR node to start from
     * @return initial result with node's type and cost=0
     */
    @Nonnull
    public static AdaptationResult initial(@Nonnull IRNode node) {
        return new AdaptationResult(node, node.getType(), 0);
    }

    /**
     * Create an adaptation result by applying an edge with the given cost.
     * The cumulative cost is the sum of the current cost and the edge cost.
     *
     * @param node the adapted IR node (e.g., wrapped in Cast)
     * @param type the resulting type
     * @param edgeCost the cost of this single adaptation step
     * @return new result with updated node, type, and cumulative cost
     */
    @Nonnull
    public AdaptationResult withAdaptation(@Nonnull IRNode node, @Nonnull Type type, int edgeCost) {
        return new AdaptationResult(node, type, this.cost + edgeCost);
    }

    /**
     * Create a successful adaptation result (for compatibility with existing code).
     *
     * @param node the adapted IR node
     * @param cost the total cost
     * @return new result with the given node and cost
     */
    @Nonnull
    public static AdaptationResult success(@Nonnull IRNode node, int cost) {
        return new AdaptationResult(node, node.getType(), cost);
    }

    /**
     * Get the adapted IR node.
     */
    @Nonnull
    public IRNode node() {
        return node;
    }

    /**
     * Get the resulting type.
     */
    @Nonnull
    public Type type() {
        return type;
    }

    /**
     * Get the cumulative cost to reach this state.
     */
    public int cost() {
        return cost;
    }

    /**
     * Check if this result matches the target type.
     */
    public boolean hasType(@Nonnull Type targetType) {
        return type.equals(targetType);
    }

    /**
     * Check if this result has reached the target type or its effective type.
     * This handles both exact matches and collection element matches.
     */
    public boolean matches(@Nonnull Type targetType) {
        return type.equals(targetType) || type.effectiveType().equals(targetType.effectiveType());
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof AdaptationResult other)) return false;
        // Two results are equal if they have the same type
        // This allows keeping only the cheapest path to each type
        return type.equals(other.type);
    }

    @Override
    public int hashCode() {
        return type.hashCode();
    }

    @Override
    public String toString() {
        return String.format("AdaptationResult{type=%s, cost=%d, node=%s}",
            type.getName(), cost, node.getClass().getSimpleName());
    }
}

