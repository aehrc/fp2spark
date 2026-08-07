package au.csiro.fhirpath.operation;

import au.csiro.fhirpath.ir.IRNode;
import au.csiro.fhirpath.operation.signature.SignatureDefinition;
import jakarta.annotation.Nonnull;
import java.util.List;

/**
 * Facade for operation resolution, coordinating registry lookup and overload resolution.
 *
 * <p>This facade provides a clean interface for resolving FHIRPath operations to their
 * best-matching signatures. It coordinates:
 *
 * <ul>
 *   <li>Operation registry lookup ({@link #getSignatures})
 *   <li>Overload resolution ({@link #resolveCall})
 *   <li>Complete resolution for simple cases ({@link #resolveBinaryOperator})
 * </ul>
 *
 * <p><b>Design Note:</b> Function call resolution requires interleaved argument analysis (lambdas
 * need signature information before analysis), so this facade provides both high-level ({@link
 * #resolveBinaryOperator}) and low-level ({@link #getSignatures}, {@link #resolveCall}) methods to
 * support different resolution patterns.
 *
 * <p>Usage:
 *
 * <pre>{@code
 * // Simple case: binary operators (operands already analyzed)
 * OverloadResolver.ResolvedCall resolved = OperationResolver.resolveBinaryOperator("+", left, right);
 *
 * // Complex case: function calls (requires interleaved analysis)
 * List<SignatureDefinition> sigs = OperationResolver.getSignatures("where");
 * // ... analyze arguments using signature information ...
 * OverloadResolver.ResolvedCall resolved = OperationResolver.resolveCall("where", sigs, args);
 * }</pre>
 */
public final class OperationResolver {

  private OperationResolver() {
    // Utility class - no instantiation
  }

  /**
   * Gets all registered signatures for the specified operation.
   *
   * <p>This is a low-level method for cases where signature information is needed before argument
   * analysis (e.g., lambda parameter detection).
   *
   * @param operationName The operation name (e.g., "where", "select", "add")
   * @return List of registered signatures (may be empty if operation is unknown)
   */
  @Nonnull
  public static List<SignatureDefinition> getSignatures(@Nonnull final String operationName) {
    return OperationRegistry.getSignatures(operationName);
  }

  /**
   * Resolves an operation call by selecting the best-matching signature.
   *
   * <p>This is a low-level method that performs overload resolution given a list of candidate
   * signatures and analyzed arguments.
   *
   * @param operationName The operation name (for error messages)
   * @param signatures The candidate signatures to match against
   * @param args The resolved IR arguments (including target as first argument)
   * @return Resolved call with selected signature and adapted arguments
   * @throws OverloadResolutionException if no matching signature can be found
   */
  @Nonnull
  public static OverloadResolver.ResolvedCall resolveCall(
      @Nonnull final String operationName,
      @Nonnull final List<SignatureDefinition> signatures,
      @Nonnull final List<IRNode> args) {
    return OverloadResolver.resolveCall(operationName, signatures, args);
  }

  /**
   * Resolves a binary operator to its best-matching signature.
   *
   * <p>Normalizes the operator symbol to canonical name (e.g., "+" → "add", "=" → "equals"), then
   * performs registry lookup and overload resolution.
   *
   * <p><b>Note:</b> Infrastructure operations (equals, union) should be handled separately before
   * calling this method, as they have special resolution semantics.
   *
   * @param operatorSymbol The operator symbol (e.g., "+", "-", ">", "=")
   * @param leftArg The left operand (resolved IR)
   * @param rightArg The right operand (resolved IR)
   * @return Resolved call with selected signature and adapted arguments
   * @throws UnsupportedOperatorException if the operator is not registered
   * @throws OverloadResolutionException if no matching signature can be found
   */
  @Nonnull
  public static OverloadResolver.ResolvedCall resolveBinaryOperator(
      @Nonnull final String operatorSymbol,
      @Nonnull final IRNode leftArg,
      @Nonnull final IRNode rightArg) {
    // Normalize operator symbol to canonical function name
    final String operationName = OperatorNormalizer.normalize(operatorSymbol);

    // Get signatures from registry
    final List<SignatureDefinition> signatures = getSignatures(operationName);

    if (signatures.isEmpty()) {
      throw new UnsupportedOperatorException(operatorSymbol, null);
    }

    // Resolve with two arguments
    return resolveCall(operationName, signatures, List.of(leftArg, rightArg));
  }
}
