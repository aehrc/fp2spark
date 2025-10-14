package com.example.fhirpath.analyzer;

import com.example.fhirpath.ir.Cast;
import com.example.fhirpath.ir.CastToSystem;
import com.example.fhirpath.ir.IRNode;
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
                List<IRNode> adaptedArgs = Stream.concat(
                        adaptations.stream().map(a -> a.node),
                        // pad with nulls for varargs
                        Stream.generate(() -> (IRNode) null).limit(sig.arity() - adaptations.size())
                ).toList();

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
        Type actual = arg.getType();
        if (actual.effectiveType() == target.effectiveType()) {
            return new Adapt(arg, true, 0);
        }

        // Special handling for LambdaType matching
        if (actual instanceof LambdaType actualLambda && target instanceof LambdaType targetLambda) {
            // Lambda types match if:
            // 1. Parameter types are compatible (target ANY matches any actual parameter)
            // 2. Return types are compatible (target ANY matches any actual return)
            boolean paramMatches = targetLambda.parameterType() == Type.ANY
                || actualLambda.parameterType() == targetLambda.parameterType()
                || TypeSystem.canCast(actualLambda.parameterType(), targetLambda.parameterType());

            boolean returnMatches = targetLambda.returnType() == Type.ANY
                || actualLambda.returnType() == targetLambda.returnType()
                || TypeSystem.canCast(actualLambda.returnType(), targetLambda.returnType());

            if (paramMatches && returnMatches) {
                // Cost 0 for exact match, cost 1 for ANY wildcard match
                int cost = (targetLambda.parameterType() == Type.ANY || targetLambda.returnType() == Type.ANY) ? 1 : 0;
                return new Adapt(arg, true, cost);
            }
        }

        // Allow implicit casts via TypeSystem or from UNKNOWN
        if (target == Type.ANY) {
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
