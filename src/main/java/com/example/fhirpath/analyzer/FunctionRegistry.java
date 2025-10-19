package com.example.fhirpath.analyzer;

import com.example.fhirpath.ast.AstBinaryOperator;
import com.example.fhirpath.ir.*;

import java.util.List;

public final class FunctionRegistry {

    private FunctionRegistry() {
    }

    public static IRNode resolve(Analyzer analyzer, AstBinaryOperator biOperator) {
        List<IRNode> args = List.of(
                analyzer.analyze(biOperator.left()),
                analyzer.analyze(biOperator.right())
        );
        String operatorSymbol = biOperator.operator();

        // Convert operator symbol to canonical name
        String operationName = OperatorNormalizer.normalize(operatorSymbol);

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
            OverloadResolver.ResolvedCall resolvedCall = OverloadResolver.resolveCall(
                    operationName, signatures, args
            );
            return new Operation(operationName, resolvedCall.args(), resolvedCall.signature());
        }

        throw new UnsupportedOperatorException(operatorSymbol, null);
    }
}
