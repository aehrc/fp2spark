package com.example.fhirpath.analyzer;

import com.example.fhirpath.ir.Cast;
import com.example.fhirpath.ir.IRNode;
import com.example.fhirpath.typing.Type;
import com.example.fhirpath.typing.TypeSystem;

import java.util.List;

public final class OverloadResolver {

    private OverloadResolver() {
    }

    public record ResolvedCall(IRNode left, IRNode right, Type resultType) {
    }

    public static ResolvedCall resolveBinary(List<FunctionSignature> candidates,
                                             IRNode left,
                                             IRNode right) {
        ResolvedCall best = null;
        int bestCost = Integer.MAX_VALUE;

        for (FunctionSignature sig : candidates) {
            if (sig.arity() != 2) continue;

            Type t1 = sig.parameterTypes().get(0);
            Type t2 = sig.parameterTypes().get(1);

            Adapt a1 = adapt(left, t1);
            if (!a1.ok) continue;

            Adapt a2 = adapt(right, t2);
            if (!a2.ok) continue;

            int cost = a1.cost + a2.cost;
            if (cost < bestCost) {
                bestCost = cost;
                best = new ResolvedCall(a1.node, a2.node, sig.resultType());
            }
        }

        if (best == null) {
            throw new IllegalArgumentException("No matching overload for binary operation with arg types: "
                    + left.getType() + ", " + right.getType());
        }
        return best;
    }

    private record Adapt(IRNode node, boolean ok, int cost) {
    }

    private static Adapt adapt(IRNode arg, Type target) {
        Type actual = arg.getType();
        if (actual == target) return new Adapt(arg, true, 0);

        // Allow implicit casts via TypeSystem or from UNKNOWN
        if (target == Type.UNKNOWN) {
            return new Adapt(arg, true, 1);
        } else if (TypeSystem.canCast(actual, target)) {
            return new Adapt(new Cast(arg, target), true, 1);
        }

        return new Adapt(arg, false, Integer.MAX_VALUE);
    }
}
