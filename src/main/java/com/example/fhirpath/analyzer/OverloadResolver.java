package com.example.fhirpath.analyzer;

import com.example.fhirpath.ir.Cast;
import com.example.fhirpath.ir.GetValue;
import com.example.fhirpath.ir.IRNode;
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

    public record ResolvedCall(FunctionSignature signature, List<IRNode> args) {
    }

    public static ResolvedCall resolveCall(List<FunctionSignature> candidates,
                                           List<IRNode> args) {
        ResolvedCall best = null;
        int bestCost = Integer.MAX_VALUE;

        for (FunctionSignature sig : candidates) {
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
                best = new ResolvedCall(sig,
                        Stream.concat(
                                adaptations.stream().map(a -> a.node),
                                // pad with nulls for varargs
                                Stream.generate(() -> (IRNode) null).limit(sig.arity() - adaptations.size())
                        ).toList());
            }
        }

        if (best == null) {
            throw new IllegalArgumentException("No matching overload for binary operation with arg types: "
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

        // Allow implicit casts via TypeSystem or from UNKNOWN
        if (target == Type.UNKNOWN) {
            return new Adapt(arg, true, 1);
        } else if (TypeSystem.canCast(actual, target)) {
            // we should check somehow if getValue() should be applied first
            final IRNode implicts;
            if (actual instanceof FhirType && target instanceof PrimitiveType) {
                implicts = new Cast(new GetValue(arg), target);
            } else {
                implicts = new Cast(arg, target);
            }
            return new Adapt(implicts, true, 1);
        }

        return new Adapt(arg, false, Integer.MAX_VALUE);
    }
}
