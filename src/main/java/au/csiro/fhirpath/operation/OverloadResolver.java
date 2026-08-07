package au.csiro.fhirpath.operation;

import au.csiro.fhirpath.analyzer.CardinalityMismatchException;
import au.csiro.fhirpath.ir.Cast;
import au.csiro.fhirpath.ir.IRNode;
import au.csiro.fhirpath.ir.Lambda;
import au.csiro.fhirpath.ir.Literal;
import au.csiro.fhirpath.operation.signature.ParamSpec;
import au.csiro.fhirpath.operation.signature.ResolvedSignature;
import au.csiro.fhirpath.operation.signature.SignatureDefinition;
import au.csiro.fhirpath.typing.Cardinality;
import au.csiro.fhirpath.typing.LambdaType;
import au.csiro.fhirpath.typing.Type;
import au.csiro.fhirpath.typing.TypeSystem;
import au.csiro.fhirpath.typing.Types;
import java.util.ArrayList;
import java.util.List;

/**
 * Resolves overloaded function calls to their best matching signature.
 *
 * <p>Given a list of candidate signatures and actual arguments, selects the best match based on
 * type compatibility and adaptation cost (e.g., exact match = 0, cast = 1, ANY wildcard = 1).
 */
public final class OverloadResolver {

  private OverloadResolver() {}

  /**
   * Result of overload resolution with resolved signature and adapted arguments.
   *
   * @param signature the resolved signature with concrete types
   * @param args the adapted argument list
   */
  public record ResolvedCall(ResolvedSignature signature, List<IRNode> args) {}

  /**
   * Resolve a function call by selecting the best matching signature and adapting arguments.
   * Returns a ResolvedCall with concrete result type.
   *
   * @param operationName the name of the operation being resolved (for error messages)
   * @param candidates the list of candidate signatures
   * @param args the arguments to match against signatures
   * @return resolved call with adapted arguments and concrete signature
   * @throws CardinalityMismatchException if argument cardinality doesn't match parameter spec
   */
  public static ResolvedCall resolveCall(
      final String operationName,
      final List<SignatureDefinition> candidates,
      final List<IRNode> args) {
    ResolvedCall best = null;
    int bestCost = Integer.MAX_VALUE;

    for (final SignatureDefinition sig : candidates) {
      if (args.size() > sig.arity() || args.size() < sig.minArity()) continue;

      int cost = 0;
      final List<Adapt> adaptations = new ArrayList<>();

      for (int i = 0; i < args.size(); i++) {
        final ParamSpec paramSpec = sig.parameter(i);
        final Type t1 = paramSpec.type();

        // Check cardinality compatibility BEFORE type adaptation
        // Per FHIRPath spec: Math/comparison operators require SINGLE cardinality
        checkCardinality(args.get(i), paramSpec, operationName, i);

        final Adapt a1 = adapt(args.get(i), t1);
        if (!a1.ok) break;
        adaptations.add(a1);
        cost += a1.cost;
      }

      if (adaptations.size() != args.size()) continue; // not all adapted

      if (cost < bestCost) {
        bestCost = cost;
        // No padding needed - Analyzer handles variadic argument padding with AstLiteral.NULL
        final List<IRNode> adaptedArgs = adaptations.stream().map(a -> a.node).toList();

        // Resolve signature (ResultTypeSpec.resolve handles both static and dynamic)
        final ResolvedSignature resolvedSig = ResolvedSignature.fromDefinition(sig, adaptedArgs);
        best = new ResolvedCall(resolvedSig, adaptedArgs);
      }
    }

    if (best == null) {
      throw new OverloadResolutionException(
          operationName,
          args.stream().map(IRNode::getType).toList(),
          null // Expression context not available here
          );
    }
    return best;
  }

  private record Adapt(IRNode node, boolean ok, int cost) {}

  /**
   * Check if argument cardinality matches parameter specification.
   *
   * <p>Per FHIRPath specification:
   *
   * <ul>
   *   <li>Section 3559-3566: Math operators require each operand to be a single element.
   *   <li>Section 3196-3197: Comparison operators require single-valued collections.
   * </ul>
   *
   * <p>Cardinality matching rules:
   *
   * <ul>
   *   <li>SINGLE parameter accepts only SINGLE arguments (strict)
   *   <li>MANY parameter accepts both SINGLE and MANY arguments (flexible)
   *   <li>Lambda arguments are skipped - they have special matching logic
   * </ul>
   *
   * <p>Note: Singleton evaluation (converting 1-element collection to single value) happens at
   * runtime, not during analysis. This check validates that MANY-valued arguments are not passed
   * where SINGLE is required.
   *
   * @param arg the argument node to check
   * @param paramSpec the parameter specification with expected cardinality
   * @param operationName the operation name for error messages
   * @param paramIndex the parameter index (0-based) for error messages
   * @throws CardinalityMismatchException if cardinalities are incompatible
   */
  private static void checkCardinality(
      final IRNode arg,
      final ParamSpec paramSpec,
      final String operationName,
      final int paramIndex) {
    // Skip cardinality checking for Lambda nodes
    // Lambdas have special matching logic in adapt() that checks LambdaType compatibility
    // Lambda's getShape() returns the body's shape, not the lambda itself
    if (arg instanceof Lambda) {
      return;
    }

    final Cardinality argCard = arg.getCardinality();
    final Cardinality expectedCard = paramSpec.cardinality();

    // SINGLE parameter cannot accept MANY argument
    // Per spec: "If there is more than one item, the evaluator will signal an error"
    if (expectedCard == Cardinality.SINGLE && argCard == Cardinality.MANY) {
      throw new CardinalityMismatchException(
          operationName,
          paramIndex,
          expectedCard,
          argCard,
          null // Expression context not available here
          );
    }

    // MANY parameter can accept both SINGLE and MANY
    // SINGLE values are implicitly lifted to singleton collections
  }

  private static Adapt adapt(final IRNode arg, final Type target) {
    // Special handling for Lambda nodes matching against LambdaType signatures
    if (arg instanceof Lambda lambda && target instanceof LambdaType targetLambda) {
      // Lambda nodes match if their body type is compatible with the target lambda's return type.
      // Parameter types are implicit (determined by binding strategy),
      // so we only check return type compatibility.
      final Type lambdaBodyType = lambda.body().getType();
      final Type targetReturnType = targetLambda.returnShape().elementType();

      final boolean returnMatches =
          targetReturnType == Types.ANY
              || lambdaBodyType == targetReturnType
              || TypeSystem.canCast(lambdaBodyType, targetReturnType);

      if (returnMatches) {
        // Cost 0 for exact match, cost 1 for ANY wildcard match
        final int cost = (targetReturnType == Types.ANY) ? 1 : 0;
        return new Adapt(arg, true, cost);
      }
      // Lambda doesn't match expected return type
      return new Adapt(arg, false, Integer.MAX_VALUE);
    }

    // Lambda node but target is not LambdaType - mismatch
    if (arg instanceof Lambda) {
      return new Adapt(arg, false, Integer.MAX_VALUE);
    }

    // Non-lambda arg but target expects LambdaType - mismatch
    if (target instanceof LambdaType) {
      return new Adapt(arg, false, Integer.MAX_VALUE);
    }

    // Regular type matching for non-lambda arguments
    final Type actual = arg.getType();
    if (actual == target) {
      return new Adapt(arg, true, 0);
    }

    // ANY matches any type. FHIR→System unwrapping happens lazily in codegen via
    // SparkOpContext.systemArgType so that shape-preserving operations like
    // where(*T, lambda) → *T keep the original FHIR element type on their result.
    if (target == Types.ANY) {
      return new Adapt(arg, true, 1);
    }

    // NULL (empty collection {}) matches any expected type
    // FHIRPath semantics: empty collections are polymorphic
    if (actual == Types.NULL) {
      return new Adapt(new Literal(null, target), true, 1);
    }

    if (TypeSystem.canCast(actual, target)) {
      return new Adapt(new Cast(arg, target), true, 1);
    }

    return new Adapt(arg, false, Integer.MAX_VALUE);
  }
}
