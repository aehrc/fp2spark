package com.example.fhirpath.analyzer;

import com.example.fhirpath.ir.*;
import com.example.fhirpath.typing.*;
import com.example.fhirpath.typing.fhir.FhirType;

import java.util.ArrayList;
import java.util.List;

public final class OverloadResolver {

    private OverloadResolver() {
    }

    /**
     * Result of overload resolution with resolved signature and adapted arguments.
     */
    public record ResolvedCall(ResolvedSignature signature, List<IRNode> args) {
    }

    /**
     * Resolve a function call by selecting the best matching signature and adapting arguments.
     * Returns a ResolvedCall with concrete result type.
     *
     * @param operationName the name of the operation being resolved (for error messages)
     * @param candidates    the list of candidate signatures
     * @param args          the arguments to match against signatures
     * @return resolved call with adapted arguments and concrete signature
     * @throws CardinalityMismatchException if argument cardinality doesn't match parameter spec
     */
    public static ResolvedCall resolveCall(
            String operationName,
            List<SignatureDefinition> candidates,
            List<IRNode> args
    ) {
        ResolvedCall best = null;
        int bestCost = Integer.MAX_VALUE;

        for (SignatureDefinition sig : candidates) {
            if (args.size() > sig.arity() || args.size() < sig.minArity()) continue;

            int cost = 0;
            List<Adapt> adaptations = new ArrayList<>();

            for (int i = 0; i < args.size(); i++) {
                ParamSpec paramSpec = sig.parameter(i);
                Type t1 = paramSpec.type();

                // Check cardinality compatibility BEFORE type adaptation
                // Per FHIRPath spec: Math/comparison operators require SINGLE cardinality
                checkCardinality(args.get(i), paramSpec, operationName, i);

                Adapt a1 = adapt(args.get(i), t1);
                if (!a1.ok) break;
                adaptations.add(a1);
                cost += a1.cost;
            }

            if (adaptations.size() != args.size()) continue; // not all adapted

            if (cost < bestCost) {
                bestCost = cost;
                // No padding needed - Analyzer handles variadic argument padding with AstLiteral.NULL
                List<IRNode> adaptedArgs = adaptations.stream()
                        .map(a -> a.node)
                        .toList();

                // Resolve signature (ResultTypeSpec.resolve handles both static and dynamic)
                ResolvedSignature resolvedSig = ResolvedSignature.fromDefinition(sig, adaptedArgs);
                best = new ResolvedCall(resolvedSig, adaptedArgs);
            }
        }

        if (best == null) {
            throw new OverloadResolutionException(
                    operationName,
                    args.stream().map(IRNode::getType).toList(),
                    null  // Expression context not available here
            );
        }
        return best;
    }

    private record Adapt(IRNode node, boolean ok, int cost) {
    }

    /**
     * Check if argument cardinality matches parameter specification.
     *
     * <p>Per FHIRPath specification:
     * <ul>
     *   <li>Section 3559-3566: Math operators require each operand to be a single element.
     *   <li>Section 3196-3197: Comparison operators require single-valued collections.
     * </ul>
     *
     * <p>Cardinality matching rules:
     * <ul>
     *   <li>SINGLE parameter accepts only SINGLE arguments (strict)
     *   <li>MANY parameter accepts both SINGLE and MANY arguments (flexible)
     *   <li>Lambda arguments are skipped - they have special matching logic
     * </ul>
     *
     * <p>Note: Singleton evaluation (converting 1-element collection to single value)
     * happens at runtime, not during analysis. This check validates that MANY-valued
     * arguments are not passed where SINGLE is required.
     *
     * @param arg           the argument node to check
     * @param paramSpec     the parameter specification with expected cardinality
     * @param operationName the operation name for error messages
     * @param paramIndex    the parameter index (0-based) for error messages
     * @throws CardinalityMismatchException if cardinalities are incompatible
     */
    private static void checkCardinality(
            IRNode arg,
            ParamSpec paramSpec,
            String operationName,
            int paramIndex
    ) {
        // Skip cardinality checking for Lambda nodes
        // Lambdas have special matching logic in adapt() that checks LambdaType compatibility
        // Lambda's getShape() returns the body's shape, not the lambda itself
        if (arg instanceof Lambda) {
            return;
        }

        Cardinality argCard = arg.getCardinality();
        Cardinality expectedCard = paramSpec.cardinality();

        // SINGLE parameter cannot accept MANY argument
        // Per spec: "If there is more than one item, the evaluator will signal an error"
        if (expectedCard == Cardinality.SINGLE && argCard == Cardinality.MANY) {
            throw new CardinalityMismatchException(
                    operationName,
                    paramIndex,
                    expectedCard,
                    argCard,
                    null  // Expression context not available here
            );
        }

        // MANY parameter can accept both SINGLE and MANY
        // SINGLE values are implicitly lifted to singleton collections
    }

    private static Adapt adapt(IRNode arg, Type target) {
        // Special handling for Lambda nodes matching against LambdaType signatures
        if (arg instanceof Lambda lambda && target instanceof LambdaType targetLambda) {
            // Lambda nodes match if their body type is compatible with the target lambda's return type.
            // Parameter types are implicit (determined by binding strategy),
            // so we only check return type compatibility.
            Type lambdaBodyType = lambda.body().getType();
            Type targetReturnType = targetLambda.returnShape().elementType();

            boolean returnMatches = targetReturnType == Types.ANY
                    || lambdaBodyType == targetReturnType
                    || TypeSystem.canCast(lambdaBodyType, targetReturnType);

            if (returnMatches) {
                // Cost 0 for exact match, cost 1 for ANY wildcard match
                int cost = (targetReturnType == Types.ANY) ? 1 : 0;
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
        Type actual = arg.getType();
        if (actual == target) {
            return new Adapt(arg, true, 0);
        }

        // NULL (empty collection {}) matches any expected type
        // FHIRPath semantics: empty collections are polymorphic
        if (target == Types.ANY) {
            return new Adapt(arg, true, 1);
        } else if (actual == Types.NULL) {
            return new Adapt(new Literal(null, target != Types.ANY ? target : Types.NULL), true, 1);
        } else if (target == Types.ANY) {
            // ANY matches any type
            return new Adapt(arg, true, 1);
        } else if (TypeSystem.canCast(actual, target)) {
            // we should check somehow if getValue() should be applied first
            final IRNode implicts;
            if (actual instanceof FhirType && target instanceof PrimitiveType) {
                implicts = new Cast(new CastToSystem(arg), target);
            } else {
                implicts = new Cast(arg, target);
            }
            return new Adapt(implicts, true, 1);
        }

        return new Adapt(arg, false, Integer.MAX_VALUE);
    }
}
