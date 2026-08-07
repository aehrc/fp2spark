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
 * Base exception for errors detected during FHIRPath expression analysis.
 *
 * <p>Analysis exceptions are thrown when the type checker or analyzer detects semantic errors that
 * violate FHIRPath specification constraints, such as cardinality mismatches or type
 * incompatibilities.
 *
 * <p>These are compile-time errors in the sense that they are detected during the analysis phase
 * before code generation, not during execution.
 */
public class AnalysisException extends RuntimeException {
  @Nullable private final String expressionContext;

  /**
   * Constructs an analysis exception with a message and expression context.
   *
   * @param message the error message
   * @param expressionContext the FHIRPath expression that caused the error
   */
  public AnalysisException(
      @Nonnull final String message, @Nullable final String expressionContext) {
    super(message);
    this.expressionContext = expressionContext;
  }

  /**
   * Constructs an analysis exception with a message, expression context, and cause.
   *
   * @param message the error message
   * @param expressionContext the FHIRPath expression that caused the error
   * @param cause the underlying cause
   */
  public AnalysisException(
      @Nonnull final String message,
      @Nullable final String expressionContext,
      @Nullable final Throwable cause) {
    super(message, cause);
    this.expressionContext = expressionContext;
  }

  /** Returns the FHIRPath expression that caused this error, if available. */
  @Nullable
  public String getExpressionContext() {
    return expressionContext;
  }

  @Override
  public String getMessage() {
    final String baseMessage = super.getMessage();
    if (expressionContext != null) {
      return baseMessage + "\n  Expression: " + expressionContext;
    }
    return baseMessage;
  }
}
