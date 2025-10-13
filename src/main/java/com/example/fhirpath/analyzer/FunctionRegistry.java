package com.example.fhirpath.analyzer;

import com.example.fhirpath.ast.AstBinaryOperator;
import com.example.fhirpath.ast.AstFunctionCall;
import com.example.fhirpath.ir.*;

import java.util.List;
import java.util.Map;

public final class FunctionRegistry {

    // Mapping of operator symbols to canonical names
    private static final Map<String, String> OPERATOR_NAMES = Map.ofEntries(
            Map.entry(">", "gt"),
            Map.entry("<", "lt"),
            Map.entry(">=", "geq"),
            Map.entry("<=", "leq"),
            Map.entry("+", "add"),
            Map.entry("-", "sub"),
            Map.entry("*", "multiply"),
            Map.entry("/", "divide"),
            Map.entry("%", "mod"),
            Map.entry("=", "equals"),
            Map.entry("|", "union")
    );

    private FunctionRegistry() {
    }

    public static IRNode resolve(Analyzer analyzer, AstFunctionCall call) {
        List<IRNode> args = call.children().map(analyzer::analyze).toList();
        String name = call.functionName();

        // Try OperationRegistry first (for functions with signatures)
        List<SignatureDefinition> signatures = OperationRegistry.getSignatures(name);
        if (!signatures.isEmpty()) {
            OverloadResolver.ResolvedCall resolvedCall = OverloadResolver.resolveCall(signatures, args);
            return new Operation(name, resolvedCall.args(), resolvedCall.signature());
        }

        // Special handling for infrastructure functions (not yet in registry)
        return switch (name) {
            case "getValue" -> new CastToSystem(args.get(0));
            case "equals" -> new Equals(args.get(0), args.get(1));
            case "union", "|" -> new Union(args.get(0), args.get(1));
            default -> throw new UnsupportedOperationException("Function '" + name + "' is not supported");
        };
    }

    public static IRNode resolve(Analyzer analyzer, AstBinaryOperator biOperator) {
        List<IRNode> args = List.of(
                analyzer.analyze(biOperator.left()),
                analyzer.analyze(biOperator.right())
        );
        String operatorSymbol = biOperator.operator();

        // Convert operator symbol to canonical name
        String operationName = OPERATOR_NAMES.getOrDefault(operatorSymbol, operatorSymbol);

        // Special handling for equals and union (infrastructure nodes)
        if ("equals".equals(operationName)) {
            return new Equals(args.get(0), args.get(1));
        }
        if ("union".equals(operationName)) {
            return new Union(args.get(0), args.get(1));
        }

        // Try OperationRegistry
        List<SignatureDefinition> signatures = OperationRegistry.getSignatures(operationName);
        if (!signatures.isEmpty()) {
            OverloadResolver.ResolvedCall resolvedCall = OverloadResolver.resolveCall(signatures, args);
            return new Operation(operationName, resolvedCall.args(), resolvedCall.signature());
        }

        throw new UnsupportedOperationException("Operator '" + operatorSymbol + "' is not supported");
    }
}
