package com.example.fhirpath.analyzer;

import com.example.fhirpath.ir.Cast;
import com.example.fhirpath.ir.CastToSystem;
import com.example.fhirpath.ir.IRNode;
import com.example.fhirpath.ir.Lambda;
import com.example.fhirpath.typing.CollectionType;
import com.example.fhirpath.typing.LambdaType;
import com.example.fhirpath.typing.PrimitiveType;
import com.example.fhirpath.typing.Type;
import com.example.fhirpath.typing.TypeSystem;
import com.example.fhirpath.typing.fhir.FhirType;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

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
     */
    public static ResolvedCall resolveCall(List<SignatureDefinition> candidates,
                                           List<IRNode> args) {
        ResolvedCall best = null;
        int bestCost = Integer.MAX_VALUE;

        for (SignatureDefinition sig : candidates) {
            if (args.size() > sig.arity() || args.size() < sig.minArity()) continue;

            int cost = 0;
            List<Adapt> adaptations = new ArrayList<>();

            for (int i = 0; i < args.size(); i++) {
                Type t1 = sig.parameterTypes().get(i);

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

                // Resolve the signature to get concrete result type
                ResolvedSignature resolvedSig = ResolvedSignature.resolve(sig, adaptedArgs);
                best = new ResolvedCall(resolvedSig, adaptedArgs);
            }
        }

        if (best == null) {
            throw new IllegalArgumentException("No matching overload for operation with arg types: "
                    + args.stream().map(IRNode::getType).toList());
        }
        return best;
    }

    private record Adapt(IRNode node, boolean ok, int cost) {
    }

    private static Adapt adapt(IRNode arg, Type target) {
        // Special handling for Lambda nodes matching against LambdaType signatures
        if (arg instanceof Lambda lambda && target instanceof LambdaType targetLambda) {
            // Lambda nodes match if their body type is compatible with the target lambda's return type.
            // Parameter types are implicit (determined by binding strategy),
            // so we only check return type compatibility.
            Type lambdaBodyType = lambda.body().getType();
            boolean returnMatches = targetLambda.returnType() == Type.ANY
                || lambdaBodyType == targetLambda.returnType()
                || (targetLambda.returnType() instanceof CollectionType targetColl
                    && targetColl.elementType() == Type.ANY
                    && lambdaBodyType instanceof CollectionType)
                || TypeSystem.canCast(lambdaBodyType, targetLambda.returnType());

            if (returnMatches) {
                // Cost 0 for exact match, cost 1 for ANY wildcard match
                int cost = (targetLambda.returnType() == Type.ANY
                    || (targetLambda.returnType() instanceof CollectionType tc && tc.elementType() == Type.ANY)) ? 1 : 0;
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
        if (actual.effectiveType() == target.effectiveType()) {
            return new Adapt(arg, true, 0);
        }

        // NULL (empty collection {}) matches any expected type
        // FHIRPath semantics: empty collections are polymorphic
        if (actual == Type.NULL) {
            return new Adapt(arg, true, 1);
        }

        // Allow implicit casts via TypeSystem or from UNKNOWN
        if (target == Type.ANY) {
            return new Adapt(arg, true, 1);
        } else if (target instanceof CollectionType targetColl && targetColl.elementType() == Type.ANY) {
            // CollectionType<ANY> matches:
            // - NULL (empty collection)
            // - Any CollectionType (regardless of element type)
            // - Any scalar type (implicitly a singleton collection in FHIRPath)
            if (actual == Type.NULL || actual instanceof CollectionType) {
                return new Adapt(arg, true, 1);
            }
            // Scalar types implicitly match CollectionType<ANY> (singleton collections)
            return new Adapt(arg, true, 2);  // Higher cost for implicit conversion
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
