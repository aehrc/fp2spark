package com.example.fhirpath.operation.signature;

import com.example.fhirpath.typing.Cardinality;
import jakarta.annotation.Nonnull;

/**
 * Specifies how the result cardinality of an operation is determined.
 *
 * <p>This sealed interface has two variants:
 *
 * <ul>
 *   <li>{@link Explicit} — a fixed cardinality (e.g., always SINGLE or always MANY)
 *   <li>{@link Preserved} — cardinality is inherited from the input (the {@code α} variable in
 *       TYPE_SYSTEM.md)
 * </ul>
 */
public sealed interface CardinalitySpec
    permits CardinalitySpec.Explicit, CardinalitySpec.Preserved {

  /** Singleton instance for cardinality preservation. */
  CardinalitySpec PRESERVED = new Preserved();

  /**
   * Resolve the result cardinality given the input's cardinality.
   *
   * @param inputCardinality the cardinality of the input collection
   * @return the resolved result cardinality
   */
  @Nonnull
  Cardinality resolve(@Nonnull Cardinality inputCardinality);

  /**
   * Fixed result cardinality, independent of input.
   *
   * @param cardinality the fixed cardinality to use
   */
  record Explicit(@Nonnull Cardinality cardinality) implements CardinalitySpec {
    @Override
    @Nonnull
    public Cardinality resolve(@Nonnull final Cardinality inputCardinality) {
      return cardinality;
    }

    @Override
    public String toString() {
      return cardinality.toString();
    }
  }

  /**
   * Result cardinality is preserved from the input — the {@code α} cardinality variable in
   * TYPE_SYSTEM.md signatures like {@code ∀ T, α. where(α T, ...) → α T}.
   */
  record Preserved() implements CardinalitySpec {
    @Override
    @Nonnull
    public Cardinality resolve(@Nonnull final Cardinality inputCardinality) {
      return inputCardinality;
    }

    @Override
    public String toString() {
      return "α";
    }
  }
}
