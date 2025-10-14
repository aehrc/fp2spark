package com.example.fhirpath.analyzer;

import com.example.fhirpath.ast.*;
import com.example.fhirpath.ir.*;
import com.example.fhirpath.typing.CollectionType;
import com.example.fhirpath.typing.ComplexType;
import com.example.fhirpath.typing.ResourceType;
import com.example.fhirpath.typing.Type;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.math.BigDecimal;
import java.util.Optional;

import static com.example.fhirpath.ast.AstVariable.CONTEXT_VARIABLE;
import static com.example.fhirpath.ast.AstVariable.RESOURCE_VARIABLE;

public class Analyzer {
    @Nonnull
    private final AstNode contextNode;
    @Nonnull
    private final ResourceType resourceSpec;
    @Nullable
    private final Type thisType;  // For lambda analysis

    public Analyzer() {
        // by default, no context
        this(AstVariable.resourceVariable(), ResourceType.EMPTY, null);
    }

    public Analyzer(@Nonnull AstNode contextNode) {
        this(contextNode, ResourceType.EMPTY, null);
    }

    public Analyzer(@Nonnull ResourceType resourceSpec) {
        this(AstVariable.resourceVariable(), resourceSpec, null);
    }

    public Analyzer(@Nonnull AstNode contextNode, @Nonnull ResourceType resourceSpec) {
        this(contextNode, resourceSpec, null);
    }

    /**
     * Private constructor for lambda analysis with $this binding.
     */
    private Analyzer(
        @Nonnull AstNode contextNode,
        @Nonnull ResourceType resourceSpec,
        @Nullable Type thisType
    ) {
        this.contextNode = contextNode;
        this.resourceSpec = resourceSpec;
        this.thisType = thisType;
    }

    /**
     * Creates an analyzer for lambda body with $this bound to elementType.
     */
    private Analyzer withThisType(@Nonnull Type elementType) {
        return new Analyzer(this.contextNode, this.resourceSpec, elementType);
    }

    /**
     * Returns the implicit target for expressions without an explicit target.
     * Inside a lambda context, returns $this.
     * Outside a lambda context, returns %context.
     */
    @Nonnull
    private AstNode getImplicitTarget() {
        return (thisType != null)
            ? AstIterationVariable.thisVariable()
            : AstVariable.contextVariable();
    }

    /**
     * Resolves a node with implicit target handling.
     * If the node has no explicit target, applies the implicit target before resolution.
     *
     * @param node A node that may have an implicit target (AstFunctionCall or AstTraversal)
     * @param <T> The concrete type of WithTarget
     * @return The node with implicit target resolved (returns node with target set)
     */
    @Nonnull
    private <T extends WithTarget<T>> T resolveWithImplicitTarget(@Nonnull T node) {
        return node.target() == null ? node.withTarget(getImplicitTarget()) : node;
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
        if (node instanceof AstIterationVariable iterVar) {
            return resolveIterationVariable(iterVar);
        }
        throw new IllegalArgumentException("Unsupported AST node: " + node);
    }

    private IRNode resolveVariable(AstVariable variable) {
        return switch (variable.name()) {
            // TODO: the context node should be analyzed with empty context to avoid recursion
            case CONTEXT_VARIABLE -> new Analyzer(resourceSpec).analyze(contextNode);
            case RESOURCE_VARIABLE -> new Resource(resourceSpec);
            default -> throw new IllegalArgumentException("Unsupported FHIRPath environment variable: " + variable.name());
        };
    }

    private IRNode resolveIterationVariable(AstIterationVariable iterVar) {
        return switch (iterVar.name()) {
            case AstIterationVariable.THIS -> {
                if (thisType == null) {
                    throw new IllegalArgumentException(
                        "$this can only be used in lambda expressions (e.g., within where() or select())"
                    );
                }
                yield new ThisReference(thisType);
            }
            case AstIterationVariable.INDEX -> throw new UnsupportedOperationException(
                "$index is not yet supported"
            );
            case AstIterationVariable.TOTAL -> throw new UnsupportedOperationException(
                "$total is not yet supported"
            );
            default -> throw new IllegalArgumentException("Unknown iteration variable: " + iterVar.name());
        };
    }

    private IRNode resolveFunctionCall(AstFunctionCall call) {
        // Special handling for lambda-taking functions (where, select, etc.)
        if (isLambdaFunction(call.functionName())) {
            return resolveLambdaFunction(call);
        }

        // Resolve implicit target if needed
        return FunctionRegistry.resolve(this, resolveWithImplicitTarget(call));
    }

    /**
     * Checks if a function takes lambda arguments.
     */
    private boolean isLambdaFunction(String functionName) {
        return functionName.equals("where") || functionName.equals("select");
    }

    /**
     * Resolves function calls that take lambda arguments (where, select, etc.).
     */
    private IRNode resolveLambdaFunction(AstFunctionCall call) {
        // Resolve target collection
        AstNode targetAst = call.target() != null ? call.target() : AstVariable.contextVariable();
        IRNode targetIR = analyze(targetAst);

        // Extract element type from collection
        Type targetType = targetIR.getType();
        Type elementType = (targetType instanceof CollectionType ct)
            ? ct.elementType()
            : targetType;

        // Resolve lambda argument with $this bound to element type
        if (call.arguments().isEmpty()) {
            throw new IllegalArgumentException(
                call.functionName() + "() requires a criteria/projection expression"
            );
        }

        AstNode lambdaBodyAst = call.arguments().get(0);
        Analyzer lambdaAnalyzer = withThisType(elementType);
        IRNode lambdaBody = lambdaAnalyzer.analyze(lambdaBodyAst);

        // Create Lambda IR node
        Lambda lambdaIR = new Lambda(
            java.util.List.of("$this"),
            lambdaBody,
            elementType
        );

        // Create function call with target and lambda
        AstFunctionCall callWithLambda = new AstFunctionCall(
            call.functionName(),
            targetAst,
            java.util.List.of(lambdaBodyAst)  // Keep original AST for debugging
        );

        // Resolve through FunctionRegistry with lambda IR
        return FunctionRegistry.resolveLambda(this, callWithLambda, targetIR, lambdaIR);
    }

    private IRNode resolveTraversal(AstTraversal traversal) {
        // Resolve implicit target if needed
        AstTraversal resolvedTraversal = resolveWithImplicitTarget(traversal);
        IRNode targetIR = analyze(resolvedTraversal.target());
        return Optional.of(targetIR.getType().effectiveType())
                .filter(ComplexType.class::isInstance)
                .map(ComplexType.class::cast)
                .flatMap(ct -> ct.getField(resolvedTraversal.path()))
                // for ComplexTypes with required field create a Traversal
                .map(fieldSpec -> (IRNode) new Traversal(targetIR, fieldSpec))
                // otherwise an empty collection
                .orElseGet(() -> new Literal(null, Type.NULL));
    }

    private IRNode resolveBinaryOp(AstBinaryOperator binaryOp) {
        return FunctionRegistry.resolve(this, binaryOp);
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
