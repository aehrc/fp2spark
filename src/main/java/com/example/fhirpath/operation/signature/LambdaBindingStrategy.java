package com.example.fhirpath.operation.signature;

/**
 * Defines how $this is bound when analyzing lambda parameters.
 *
 * <p>Different FHIRPath functions bind $this at different levels: - Element-wise: $this refers to
 * individual collection elements - Collection-wise: $this refers to the entire collection
 */
public enum LambdaBindingStrategy {
  /**
   * Lambda operates on individual elements. {@code $this = targetType.effectiveType()}.
   *
   * <p>Examples:
   *
   * <ul>
   *   <li>{@code Collection<T>.where(criteria)} - $this is T
   *   <li>{@code Collection<T>.select(projection)} - $this is T
   *   <li>{@code Collection<T>.exists(criteria)} - $this is T (element-wise check)
   * </ul>
   */
  ELEMENT_WISE,

  /**
   * Lambda operates on entire collection. {@code $this = targetType} (the full collection).
   *
   * <p>Examples:
   *
   * <ul>
   *   <li>{@code Collection<T>.iif(criteria, result)} - $this is {@code Collection<T>}
   * </ul>
   */
  COLLECTION_WISE
}
