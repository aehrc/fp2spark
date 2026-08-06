package au.csiro.fhirpath.ast;

import jakarta.annotation.Nonnull;

/**
 * Represents an environment variable reference in a FHIRPath expression (e.g., %context).
 *
 * @param name the variable name, which must start with '%'
 */
public record AstVariable(@Nonnull String name) implements AstNode {

  /** The name of the context variable ({@code %context}). */
  public static final String CONTEXT_VARIABLE = "%context";

  /** The name of the resource variable ({@code %resource}). */
  public static final String RESOURCE_VARIABLE = "%resource";

  /** Validates that the variable name starts with '%'. */
  public AstVariable {
    if (!name.startsWith("%")) {
      throw new IllegalArgumentException("Variable name must start with %: " + name);
    }
  }

  /**
   * Creates an AST node for the {@code %context} variable.
   *
   * @return an AstVariable for %context
   */
  @Nonnull
  public static AstVariable contextVariable() {
    return new AstVariable(CONTEXT_VARIABLE);
  }

  /**
   * Creates an AST node for the {@code %resource} variable.
   *
   * @return an AstVariable for %resource
   */
  @Nonnull
  public static AstVariable resourceVariable() {
    return new AstVariable(RESOURCE_VARIABLE);
  }
}
