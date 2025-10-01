package com.example.fhirpath.analyzer;

import com.example.fhirpath.ast.*;
import com.example.fhirpath.ir.*;
import com.example.fhirpath.typing.Type;

import java.math.BigDecimal;

public class Analyzer {

    public IRNode analyze(AstNode node) {
        if (node instanceof AstLiteral lit) {
            return new Literal(lit.value(), inferType(lit.value()));
        }
        if (node instanceof AstTraversal trav) {
            return resolveTraversal(trav);
        }
        if (node instanceof AstFunctionCall call) {
            return FunctionRegistry.resolve(this, call);
        }
        if (node instanceof AstBinaryOperator binaryOp) {
            return resolveBinaryOp(binaryOp);
        }
        throw new IllegalArgumentException("Unsupported AST node: " + node);
    }

    private IRNode resolveTraversal(AstTraversal traversal) {
        if (traversal.target() != null) {
            // Handle member access like "expr.field" - for now, treat as unsupported
            throw new UnsupportedOperationException("Member traversal with target not yet implemented: " + traversal.path());
        }
        // Handle standalone field access
        return new Traversal(traversal.path());
    }

    private IRNode resolveBinaryOp(AstBinaryOperator binaryOp) {
        IRNode left = analyze(binaryOp.left());
        IRNode right = analyze(binaryOp.right());
        String op = binaryOp.operator();

        return switch (op) {
            case "+" -> FunctionRegistry.buildAdd(left, right);
            case "-" -> FunctionRegistry.buildSub(left, right);
            case "=" -> FunctionRegistry.buildEquals(left, right);
            default -> throw new IllegalArgumentException("Unsupported binary operator: " + op);
        };
    }

    private Type inferType(Object value) {
        if (value == null) return Type.NULL;
        if (value instanceof Integer) return Type.INTEGER;
        if (value instanceof BigDecimal) return Type.DECIMAL;
        if (value instanceof Boolean) return Type.BOOLEAN;
        if (value instanceof String) return Type.STRING;
        throw new IllegalArgumentException("Unsupported literal value: " + value);
    }
}
