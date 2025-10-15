package com.example.fhirpath.analyzer;

/**
 * Defines how $this is bound when analyzing lambda parameters.
 *
 * Different FHIRPath functions bind $this at different levels:
 * - Element-wise: $this refers to individual collection elements
 * - Collection-wise: $this refers to the entire collection
 */
public enum LambdaBindingStrategy {
    /**
     * Lambda operates on individual elements.
     * $this = targetType.effectiveType()
     *
     * Examples:
     * - Collection<T>.where(criteria) - $this is T
     * - Collection<T>.select(projection) - $this is T
     * - Collection<T>.exists(criteria) - $this is T (element-wise check)
     */
    ELEMENT_WISE,

    /**
     * Lambda operates on entire collection.
     * $this = targetType (the full collection)
     *
     * Examples:
     * - Collection<T>.iif(criteria, result) - $this is Collection<T>
     */
    COLLECTION_WISE
}
