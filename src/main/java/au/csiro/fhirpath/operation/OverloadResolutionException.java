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
package au.csiro.fhirpath.operation;

import au.csiro.fhirpath.analyzer.AnalysisException;
import au.csiro.fhirpath.typing.Type;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.List;

/**
 * Exception thrown when no matching function/operator overload can be found for the given argument
 * types.
 *
 * <p>This occurs when calling a function or operator with arguments whose types don't match any
 * registered signature after type adaptation attempts.
 *
 * <p>Example: Calling {@code add(STRING, INTEGER)} when only {@code add(INTEGER, INTEGER)} and
 * {@code add(DECIMAL, DECIMAL)} are defined.
 */
public class OverloadResolutionException extends AnalysisException {
  @Nonnull private final String operationName;
  @Nonnull private final List<Type> argumentTypes;

  /**
   * Constructs an overload resolution exception.
   *
   * @param operationName the name of the operation (function/operator)
   * @param argumentTypes the actual argument types provided
   * @param expressionContext the FHIRPath expression that caused the error
   */
  public OverloadResolutionException(
      @Nonnull final String operationName,
      @Nonnull final List<Type> argumentTypes,
      @Nullable final String expressionContext) {
    super(formatMessage(operationName, argumentTypes), expressionContext);
    this.operationName = operationName;
    this.argumentTypes = argumentTypes;
  }

  private static String formatMessage(final String operationName, final List<Type> argumentTypes) {
    return String.format(
        "No matching overload for operation '%s' with argument types: %s",
        operationName, argumentTypes.stream().map(Type::getName).toList());
  }

  @Nonnull
  public String getOperationName() {
    return operationName;
  }

  @Nonnull
  public List<Type> getArgumentTypes() {
    return argumentTypes;
  }
}
