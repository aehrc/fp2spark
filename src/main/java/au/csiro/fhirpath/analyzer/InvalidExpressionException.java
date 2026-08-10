/*
 * Copyright © 2025-2026 Commonwealth Scientific and Industrial Research
 * Organisation (CSIRO) ABN 41 687 119 230.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package au.csiro.fhirpath.analyzer;

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
