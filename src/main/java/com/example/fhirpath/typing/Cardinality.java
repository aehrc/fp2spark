package com.example.fhirpath.typing;

/**
 * Represents cardinality in the FHIRPath type system.
 *
 * <p>FHIRPath uses an element-first type model where cardinality is separate from element type.
 * This matches the spec notation:
 * <ul>
 *   <li>{@code ?T} (single) - zero or one element (0..1)</li>
 *   <li>{@code *T} (many) - zero or more elements (0..*)</li>
 * </ul>
 *
 * <p>Cardinality is metadata about the result shape, not part of the type hierarchy.
 */
public enum Cardinality {
    /**
     * Single cardinality (0..1).
     * Corresponds to the spec's {@code ?T} notation.
     */
    SINGLE,

    /**
     * Many cardinality (0..*).
     * Corresponds to the spec's {@code *T} notation.
     */
    MANY;

    /**
     * Join (union) of two cardinalities.
     * Used when combining results from different branches (e.g., iif, union).
     *
     * @param other the other cardinality
     * @return MANY if either cardinality is MANY, otherwise SINGLE
     */
    public Cardinality join(Cardinality other) {
        return (this == MANY || other == MANY) ? MANY : SINGLE;
    }

    /**
     * Returns whether this cardinality represents a single element (0..1).
     */
    public boolean isSingle() {
        return this == SINGLE;
    }

    /**
     * Returns whether this cardinality represents many elements (0..*).
     */
    public boolean isMany() {
        return this == MANY;
    }

    @Override
    public String toString() {
        return switch (this) {
            case SINGLE -> "?";
            case MANY -> "*";
        };
    }
}
