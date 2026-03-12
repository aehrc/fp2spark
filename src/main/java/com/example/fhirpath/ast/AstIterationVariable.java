package com.example.fhirpath.ast;

import jakarta.annotation.Nonnull;

/**
 * Represents iteration variables in lambda expressions.
 *
 * <p>These are special variables that refer to the current iteration context:
 *
 * <ul>
 *   <li>{@code $this}: current item being evaluated
 *   <li>{@code $index}: position of current item in collection (0-based)
 *   <li>{@code $total}: accumulated value in aggregate functions
 * </ul>
 *
 * <p>Unlike environment variables (%), iteration variables ($) are bound within the scope of
 * specific functions like where(), select(), aggregate().
 *
 * <p>FHIRPath Spec: "These expressions may refer to the special $this and $index elements, which
 * represent the item from the input collection currently under evaluation, and its index in the
 * collection, respectively."
 *
 * @param name the iteration variable name (must start with '$')
 */
public record AstIterationVariable(@Nonnull String name) implements AstNode {

  /** The name of the {@code $this} iteration variable. */
  public static final String THIS = "$this";

  /** The name of the {@code $index} iteration variable. */
  public static final String INDEX = "$index";

  /** The name of the {@code $total} iteration variable. */
  public static final String TOTAL = "$total";

  /** Validates that the variable name starts with '$' and is a known iteration variable. */
  public AstIterationVariable {
    if (!name.startsWith("$")) {
      throw new IllegalArgumentException("Iteration variable name must start with $: " + name);
    }
    // Validate it's one of the known iteration variables
    if (!name.equals(THIS) && !name.equals(INDEX) && !name.equals(TOTAL)) {
      throw new IllegalArgumentException(
          "Unknown iteration variable: " + name + ". Valid variables are: $this, $index, $total");
    }
  }

  /**
   * Creates an AST node for the {@code $this} iteration variable.
   *
   * @return an AstIterationVariable for $this
   */
  @Nonnull
  public static AstIterationVariable thisVariable() {
    return new AstIterationVariable(THIS);
  }

  /**
   * Creates an AST node for the {@code $index} iteration variable.
   *
   * @return an AstIterationVariable for $index
   */
  @Nonnull
  public static AstIterationVariable indexVariable() {
    return new AstIterationVariable(INDEX);
  }

  /**
   * Creates an AST node for the {@code $total} iteration variable.
   *
   * @return an AstIterationVariable for $total
   */
  @Nonnull
  public static AstIterationVariable totalVariable() {
    return new AstIterationVariable(TOTAL);
  }
}
