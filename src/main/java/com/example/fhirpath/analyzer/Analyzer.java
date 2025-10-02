package com.example.fhirpath.analyzer;

import com.example.fhirpath.ast.*;
import com.example.fhirpath.ir.IRNode;
import com.example.fhirpath.ir.Literal;
import com.example.fhirpath.ir.Traversal;
import com.example.fhirpath.typing.Type;

import javax.annotation.Nonnull;
import java.math.BigDecimal;

import static com.example.fhirpath.ast.AstVariable.CONTEXT_VARIABLE;

public class Analyzer {
    @Nonnull
    private final AstNode contextNode;

    public Analyzer() {
        // by default, no context
        this(new AstLiteral(null));
    }

    public Analyzer(@Nonnull AstNode contextNode) {
        this.contextNode = contextNode;
    }

    public IRNode analyze(AstNode node) {
        if (node instanceof AstLiteral lit) {
            return new Literal(lit.value(), inferType(lit.value()));
        }
        if (node instanceof AstTraversal trav) {
            return resolveTraversal(trav);
        }
        if (node instanceof AstFunctionCall call) {
            return resolveFunctionCall(call);
        }
        if (node instanceof AstBinaryOperator binaryOp) {
            return resolveBinaryOp(binaryOp);
        }
        if (node instanceof AstVariable var) {
            return resolveVariable(var);
        }
        throw new IllegalArgumentException("Unsupported AST node: " + node);
    }

    private IRNode resolveVariable(AstVariable variable) {
        return switch (variable.name()) {
            // TODO: the context node should be analyzed with empty context to avoid recursion
            case CONTEXT_VARIABLE -> new Analyzer().analyze(contextNode);
            default -> throw new IllegalArgumentException("Unsupported Fhirpath variable " + variable.name());
        };
    }

    private IRNode resolveFunctionCall(AstFunctionCall call) {
        // If no target is specified, use %context as implicit target
        if (call.target() == null) {
            // Create implicit context target
            return FunctionRegistry.resolve(this, call.withTarget(AstVariable.contextVariable()));
        }
        return FunctionRegistry.resolve(this, call);
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
            case "|" -> FunctionRegistry.buildUnion(left, right);
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
