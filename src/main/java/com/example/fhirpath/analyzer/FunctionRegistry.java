package com.example.fhirpath.analyzer;

import com.example.fhirpath.ast.AstFunctionCall;
import com.example.fhirpath.ir.*;
import com.example.fhirpath.typing.Type;
import com.example.fhirpath.typing.TypeSystem;

import java.util.List;

public final class FunctionRegistry {

    private FunctionRegistry() {}

    public static IRNode resolve(Analyzer analyzer, AstFunctionCall call) {
        List<IRNode> args = call.arguments().stream().map(analyzer::analyze).toList();
        String name = call.functionName();
        return switch (name) {
            case "+", "add" -> buildAdd(args);
            case "count" -> buildCount(args);
            case "exists" -> buildExists(args);
            default -> throw new UnsupportedOperationException("No matching overload for '" + name + "'");
        };
    }

    private static IRNode buildAdd(List<IRNode> args) {
        ensureArity("add", args, 2);
        IRNode l = ensureDecimal(args.get(0));
        IRNode r = ensureDecimal(args.get(1));
        return new Add(l, r);
    }

    private static IRNode buildCount(List<IRNode> args) {
        ensureArity("count", args, 1);
        return new Count(args.get(0));
    }

    private static IRNode buildExists(List<IRNode> args) {
        ensureArity("exists", args, 1);
        return new Exists(args.get(0));
    }

    private static IRNode ensureDecimal(IRNode n) {
        Type t = n.getType();
        if (t == Type.DECIMAL) return n;
        if (TypeSystem.canCast(t, Type.DECIMAL)) return new Cast(n, Type.DECIMAL);
        // Last-resort: try casting unknowns
        if (t == Type.UNKNOWN) return new Cast(n, Type.DECIMAL);
        throw new IllegalArgumentException("Cannot cast " + t + " to DECIMAL");
    }

    private static void ensureArity(String name, List<IRNode> args, int arity) {
        if (args.size() != arity) {
            throw new IllegalArgumentException("Function '" + name + "' expects " + arity + " args, got " + args.size());
        }
    }
}
