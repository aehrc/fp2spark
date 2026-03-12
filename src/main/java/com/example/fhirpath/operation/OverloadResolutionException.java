package com.example.fhirpath.operation;

import com.example.fhirpath.analyzer.AnalysisException;
import com.example.fhirpath.typing.Type;
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
