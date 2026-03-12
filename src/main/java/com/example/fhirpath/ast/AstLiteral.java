package com.example.fhirpath.ast;

import jakarta.annotation.Nullable;

/**
 * Represents a literal value in a FHIRPath expression (e.g., {@code 'hello'}, {@code 42}).
 *
 * @param value the literal value, or null for the empty collection literal ({@code {}})
 */
public record AstLiteral(@Nullable Object value) implements AstNode {

  /**
   * Singleton instance representing null/empty collection literal. Used for padding optional
   * parameters in variadic functions.
   */
  public static final AstLiteral NULL = new AstLiteral(null);
}
