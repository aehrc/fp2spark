package com.example.fhirpath.parser;

import com.example.fhirpath.ast.*;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.ArrayList;
import java.util.List;

public class AstBuilderVisitor extends FhirPathBaseVisitor<AstNode> {

    @Override
    public AstNode visitParse(FhirPathParser.ParseContext ctx) {
        return visit(ctx.expression());
    }

    @Override
    public AstNode visitLiteral(FhirPathParser.LiteralContext ctx) {
        TerminalNode intNode = ctx.INT();
        if (intNode != null) {
            String text = intNode.getText();
            // Fit into Integer if small; else Long
            try {
                return new AstLiteral(Integer.parseInt(text));
            } catch (NumberFormatException nfe) {
                return new AstLiteral(Long.parseLong(text));
            }
        }
        TerminalNode strNode = ctx.STRING();
        if (strNode != null) {
            String val = strNode.getText();
            // Strip quotes only; no escape handling in this minimal scaffold
            return new AstLiteral(val.substring(1, val.length() - 1));
        }
        throw new UnsupportedOperationException("Unsupported literal: " + ctx.getText());
    }

    @Override
    public AstNode visitIdentifier(FhirPathParser.IdentifierContext ctx) {
        return new AstTraversal(ctx.getText());
    }

    @Override
    public AstNode visitFunctionCall(FhirPathParser.FunctionCallContext ctx) {
        String fn = ctx.identifier().getText();
        List<AstNode> args = new ArrayList<>();
        List<FhirPathParser.ExpressionContext> exprs = ctx.expression();
        if (exprs != null) {
            for (var e : exprs) {
                args.add(visit(e));
            }
        }
        return new AstFunctionCall(fn, args);
    }

    @Override
    public AstNode visitAdditiveExpr(FhirPathParser.AdditiveExprContext ctx) {
        // Left associative fold of primary (op primary)* into nested function calls
        AstNode current = visit(ctx.primary(0));
        int termCount = ctx.primary().size();
        for (int i = 1; i < termCount; i++) {
            String op = ctx.getChild(2 * i - 1).getText();
            AstNode right = visit(ctx.primary(i));
            String fn = switch (op) {
                case "+" -> "add";
                case "-" -> "sub"; // Not implemented yet in registry; kept for future
                default -> throw new IllegalArgumentException("Unknown op: " + op);
            };
            current = new AstFunctionCall(fn, List.of(current, right));
        }
        return current;
    }
}

