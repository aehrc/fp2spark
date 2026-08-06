package au.csiro.fhirpath.analyzer;

import au.csiro.fhirpath.typing.Cardinality;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * Exception thrown when an operation receives an argument with incompatible cardinality.
 *
 * <p>Per FHIRPath specification:
 *
 * <ul>
 *   <li>Section 3559-3566: Math operators require each operand to be a single element. If there is
 *       more than one item, the evaluator will signal an error.
 *   <li>Section 3196-3197: Comparison operators require collections with single values. The
 *       evaluator will throw an error if either collection has more than one item.
 *   <li>Section 768-783: Singleton evaluation rules define when collections can be implicitly
 *       converted to single values.
 * </ul>
 *
 * <p>Example violations:
 *
 * <pre>
 * (1 | 2) + 2        // Error: left operand has MANY cardinality, + requires SINGLE
 * (1 | 2) > 5        // Error: left operand has MANY cardinality, > requires SINGLE
 * name + 'suffix'    // Error: if name is MANY-valued field
 * </pre>
 */
public class CardinalityMismatchException extends AnalysisException {
  @Nonnull private final Cardinality expected;
  @Nonnull private final Cardinality actual;
  @Nonnull private final String operationName;
  private final int parameterIndex;

  /**
   * Constructs a cardinality mismatch exception.
   *
   * @param operationName the name of the operation (e.g., "add", "gt")
   * @param parameterIndex the zero-based index of the parameter (0 = first operand)
   * @param expected the expected cardinality (usually SINGLE for math/comparison ops)
   * @param actual the actual cardinality of the argument
   * @param expressionContext the FHIRPath expression being analyzed
   */
  public CardinalityMismatchException(
      @Nonnull final String operationName,
      final int parameterIndex,
      @Nonnull final Cardinality expected,
      @Nonnull final Cardinality actual,
      @Nullable final String expressionContext) {
    super(formatMessage(operationName, parameterIndex, expected, actual), expressionContext);
    this.operationName = operationName;
    this.parameterIndex = parameterIndex;
    this.expected = expected;
    this.actual = actual;
  }

  private static String formatMessage(
      final String operationName,
      final int parameterIndex,
      final Cardinality expected,
      final Cardinality actual) {
    final String cardinalityName = expected == Cardinality.SINGLE ? "single" : "multiple";
    final String paramName = parameterIndex == 0 ? "left operand" : "right operand";

    return String.format(
        "Operation '%s' requires %s to have %s cardinality, but got %s cardinality.\n"
            + "  FHIRPath spec: Math and comparison operators require each operand to be a %s"
            + " element.",
        operationName, paramName, expected, actual, cardinalityName);
  }

  @Nonnull
  public String getOperationName() {
    return operationName;
  }

  public int getParameterIndex() {
    return parameterIndex;
  }

  @Nonnull
  public Cardinality getExpected() {
    return expected;
  }

  @Nonnull
  public Cardinality getActual() {
    return actual;
  }
}
