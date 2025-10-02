package com.example.fhirpath.analyzer;

import com.example.fhirpath.ast.AstFunctionCall;
import com.example.fhirpath.ir.*;
import com.example.fhirpath.typing.fhir.FhirType;

import java.util.List;

public final class FunctionRegistry {

    private FunctionRegistry() {
    }

    public static IRNode resolve(Analyzer analyzer, AstFunctionCall call) {
        List<IRNode> args = call.children().map(analyzer::analyze).toList();
        String name = call.functionName();
        return switch (name) {
            case "+" -> buildAddFromArgs(args);
            case "-" -> buildSubFromArgs(args);
            case "=" -> buildEqualsFromArgs(args);
            case "|" -> buildUnionFromArgs(args);
            case "count" -> buildCount(args);
            case "exists" -> buildExists(args);
            case "getValue" -> buildGetValue(args);
            default -> throw new UnsupportedOperationException("No matching overload for '" + name + "'");
        };
    }

    private static IRNode buildGetValue(List<IRNode> args) {
        ensureArity("getValue", args, 1);
        // check that the type is OK
        if (args.get(0).getType() instanceof FhirType) {
            return new GetValue(args.get(0));
        } else {
            throw  new UnsupportedOperationException("No matching overload for 'getValue'");
        }
    }

    // Public static methods for binary operations (called from Analyzer)
    public static IRNode buildAdd(IRNode left, IRNode right) {
        return Add.create(left, right);
    }

    public static IRNode buildSub(IRNode left, IRNode right) {
        // Use overloaded resolution similar to Add
        return Sub.create(left, right);
    }

    public static IRNode buildEquals(IRNode left, IRNode right) {
        return Equals.create(left, right);
    }

    public static IRNode buildUnion(IRNode left, IRNode right) {
        return Union.create(left, right);
    }

    // Private methods for function call resolution (with arity checking)
    private static IRNode buildAddFromArgs(List<IRNode> args) {
        ensureArity("add", args, 2);
        return buildAdd(args.get(0), args.get(1));
    }

    private static IRNode buildSubFromArgs(List<IRNode> args) {
        ensureArity("sub", args, 2);
        return buildSub(args.get(0), args.get(1));
    }

    private static IRNode buildEqualsFromArgs(List<IRNode> args) {
        ensureArity("equals", args, 2);
        return buildEquals(args.get(0), args.get(1));
    }

    private static IRNode buildUnionFromArgs(List<IRNode> args) {
        ensureArity("union", args, 2);
        return buildUnion(args.get(0), args.get(1));
    }

    private static IRNode buildCount(List<IRNode> args) {
        ensureArity("count", args, 1);
        return new Count(args.get(0));
    }

    private static IRNode buildExists(List<IRNode> args) {
        ensureArity("exists", args, 1);
        return new Exists(args.get(0));
    }

    private static void ensureArity(String name, List<IRNode> args, int arity) {
        if (args.size() != arity) {
            throw new IllegalArgumentException("Function '" + name + "' expects " + arity + " args, got " + args.size());
        }
    }
}
