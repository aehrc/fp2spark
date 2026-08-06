package au.csiro.fhirpath.ast;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * Represents a field traversal in a FHIRPath expression (e.g., {@code name} in {@code
 * Patient.name}).
 *
 * @param path the field path name to traverse
 * @param target the target expression, or null for implicit target (resolved during analysis)
 */
public record AstTraversal(String path, @Nullable AstNode target)
    implements WithTarget<AstTraversal> {
  /**
   * Constructor for traversals without a target (standalone field access).
   *
   * @param path the field path name
   */
  public AstTraversal(final String path) {
    this(path, null);
  }

  /**
   * Create a new AstTraversal with a different target.
   *
   * @param newTarget the new target node
   * @return a new AstTraversal instance with the updated target
   */
  @Nonnull
  @Override
  public AstTraversal withTarget(@Nonnull final AstNode newTarget) {
    return new AstTraversal(this.path, newTarget);
  }
}
