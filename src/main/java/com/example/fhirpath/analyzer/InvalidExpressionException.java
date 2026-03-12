package com.example.fhirpath.analyzer;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * Exception thrown when an expression is semantically invalid or malformed.
 *
 * <p>This covers errors that aren't caught by the parser but violate FHIRPath semantic rules, such
 * as:
 *
 * <ul>
 *   <li>Using variables in invalid contexts
 *   <li>Unsupported AST node types
 *   <li>Malformed literal values
 *   <li>Invalid traversal paths
 * </ul>
 */
public class InvalidExpressionException extends AnalysisException {
  /**
   * Constructs an invalid expression exception.
   *
   * @param message the error message
   * @param expressionContext the FHIRPath expression that caused the error
   */
  public InvalidExpressionException(
      @Nonnull final String message, @Nullable final String expressionContext) {
    super(message, expressionContext);
  }

  /**
   * Constructs an invalid expression exception with a cause.
   *
   * @param message the error message
   * @param expressionContext the FHIRPath expression that caused the error
   * @param cause the underlying cause
   */
  public InvalidExpressionException(
      @Nonnull final String message,
      @Nullable final String expressionContext,
      @Nullable final Throwable cause) {
    super(message, expressionContext, cause);
  }
}
