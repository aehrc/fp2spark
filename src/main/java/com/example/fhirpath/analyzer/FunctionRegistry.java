package com.example.fhirpath.analyzer;

import com.example.fhirpath.ast.AstBinaryOperator;
import com.example.fhirpath.ast.AstFunctionCall;
import com.example.fhirpath.ir.*;
import com.example.fhirpath.ir.arythm.Add;
import com.example.fhirpath.ir.arythm.Divide;
import com.example.fhirpath.ir.arythm.Sub;
import com.example.fhirpath.ir.builder.IRNodeBuilder;
import com.example.fhirpath.ir.comparison.GreaterEqual;
import com.example.fhirpath.ir.comparison.GreaterThan;
import com.example.fhirpath.ir.comparison.LessThan;
import com.example.fhirpath.ir.math.Abs;
import com.example.fhirpath.ir.math.Exp;
import com.example.fhirpath.ir.string.Substring;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.example.fhirpath.ir.builder.IRNodeBuilder.forClass;

public final class FunctionRegistry {


    final static Map<String, IRNodeBuilder> OPERATORS = Map.ofEntries(
            Map.entry(">", forClass(GreaterThan.class)),
            Map.entry("<", forClass(LessThan.class)),
            Map.entry(">=", forClass(GreaterEqual.class)),
            Map.entry("+", forClass(Add.class)),
            Map.entry("-", forClass(Sub.class)),
            Map.entry("/", forClass(Divide.class)),
            Map.entry("=", forClass(Equals.class)),
            Map.entry("|", forClass(Union.class))
    );

    final static Map<String, IRNodeBuilder> FUNCTIONS = Map.ofEntries(
            Map.entry("count", forClass(Count.class)),
            Map.entry("exists", forClass(Exists.class)),
            Map.entry("abs", forClass(Abs.class)),
            Map.entry("exp", forClass(Exp.class)),
            Map.entry("getValue", forClass(GetValue.class)),
            Map.entry("substring", forClass(Substring.class))
    );

    private FunctionRegistry() {
    }

    public static IRNode resolve(Analyzer analyzer, AstFunctionCall call) {
        List<IRNode> args = call.children().map(analyzer::analyze).toList();
        String name = call.functionName();
        return Optional.ofNullable(FUNCTIONS.get(name))
                .map(builder -> doResolve(builder, args))
                .orElseThrow(() -> new UnsupportedOperationException("Function '" + name + "' is not supported"));
    }

    public static IRNode resolve(Analyzer analyzer, AstBinaryOperator biOperator) {
        List<IRNode> args = List.of(
                analyzer.analyze(biOperator.left()),
                analyzer.analyze(biOperator.right())
        );
        String name = biOperator.operator();
        return Optional.ofNullable(OPERATORS.get(name))
                .map(builder -> doResolve(builder, args))
                .orElseThrow(() -> new UnsupportedOperationException("Operator '" + name + "' is not supported"));
    }

    private static IRNode doResolve(IRNodeBuilder builder, List<IRNode> args) {
        List<FunctionSignature> candidates = builder.getSignatures();
        if (candidates.isEmpty()) {
            // TODO: This needs to be fixed to check for arity
            return builder.build(args.toArray(new IRNode[0]));
        } else {
            OverloadResolver.ResolvedCall resolvedCall = OverloadResolver.resolveCall(candidates, args);
            return builder.build(resolvedCall.args().toArray(new IRNode[0]));
        }
    }
}
