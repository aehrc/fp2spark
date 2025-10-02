package com.example.fhirpath.analyzer;

import com.example.fhirpath.ast.*;
import com.example.fhirpath.ir.IRNode;
import com.example.fhirpath.ir.Literal;
import com.example.fhirpath.ir.Resource;
import com.example.fhirpath.ir.Traversal;
import com.example.fhirpath.typing.ComplexType;
import com.example.fhirpath.typing.ResourceType;
import com.example.fhirpath.typing.Type;

import javax.annotation.Nonnull;
import java.math.BigDecimal;
import java.util.Optional;

import static com.example.fhirpath.ast.AstVariable.CONTEXT_VARIABLE;
import static com.example.fhirpath.ast.AstVariable.RESOURCE_VARIABLE;

public class Analyzer {
    @Nonnull
    private final AstNode contextNode;
    @Nonnull
    private final ResourceType resourceSpec;

    public Analyzer() {
        // by default, no context
        this(AstVariable.resourceVariable(), ResourceType.EMPTY);
    }

    public Analyzer(@Nonnull AstNode contextNode) {
        this(contextNode, ResourceType.EMPTY);
    }

    public Analyzer(@Nonnull ResourceType resourceSpec) {
        this(AstVariable.resourceVariable(), resourceSpec);
    }

    public Analyzer(@Nonnull AstNode contextNode, @Nonnull ResourceType resourceSpec) {
        this.contextNode = contextNode;
        this.resourceSpec = resourceSpec;
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
            case CONTEXT_VARIABLE -> new Analyzer(resourceSpec).analyze(contextNode);
            case RESOURCE_VARIABLE -> new Resource(resourceSpec);
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
        // add implicit context if no target is specified
        IRNode targetIR = analyze(traversal.target() != null ? traversal.target() : AstVariable.contextVariable());
        return Optional.of(targetIR.getType())
                .filter(ComplexType.class::isInstance)
                .map(ComplexType.class::cast)
                .flatMap(ct -> ct.getField(traversal.path()))
                // for ComplexTypes with required field create a Traversal
                .map(fieldSpec -> (IRNode) new Traversal(targetIR, fieldSpec))
                // otherwise an empty collection
                .orElseGet(() -> new Literal(null, Type.NULL));
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
