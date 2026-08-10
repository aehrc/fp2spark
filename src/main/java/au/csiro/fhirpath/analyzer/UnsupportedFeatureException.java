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
 * Exception thrown when a FHIRPath feature is not yet implemented.
 *
 * <p>This is used for valid FHIRPath constructs that are recognized by the parser but not yet
 * supported by the analyzer or code generator.
 *
 * <p>Examples:
 *
 * <ul>
 *   <li>$index and $total iteration variables
 *   <li>Future FHIRPath 2.0 features
 *   <li>Complex type operations pending implementation
 * </ul>
 */
public class UnsupportedFeatureException extends AnalysisException {
  @Nonnull private final String featureName;

  /**
   * Constructs an unsupported feature exception.
   *
   * @param featureName the name of the unsupported feature
   * @param expressionContext the FHIRPath expression that caused the error
   */
  public UnsupportedFeatureException(
      @Nonnull final String featureName, @Nullable final String expressionContext) {
    super(formatMessage(featureName), expressionContext);
    this.featureName = featureName;
  }

  /**
   * Constructs an unsupported feature exception with additional detail.
   *
   * @param featureName the name of the unsupported feature
   * @param detail additional detail about why it's unsupported
   * @param expressionContext the FHIRPath expression that caused the error
   */
  public UnsupportedFeatureException(
      @Nonnull final String featureName,
      @Nonnull final String detail,
      @Nullable final String expressionContext) {
    super(formatMessageWithDetail(featureName, detail), expressionContext);
    this.featureName = featureName;
  }

  private static String formatMessage(final String featureName) {
    return String.format("FHIRPath feature '%s' is not yet implemented", featureName);
  }

  private static String formatMessageWithDetail(final String featureName, final String detail) {
    return String.format("FHIRPath feature '%s' is not yet implemented: %s", featureName, detail);
  }

  @Nonnull
  public String getFeatureName() {
    return featureName;
  }
}
