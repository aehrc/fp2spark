package com.example.fhirpath.analyzer;

import com.example.fhirpath.ast.*;
import com.example.fhirpath.ir.*;
import com.example.fhirpath.typing.CollectionType;
import com.example.fhirpath.typing.ComplexType;
import com.example.fhirpath.typing.LambdaType;
import com.example.fhirpath.typing.ResourceType;
import com.example.fhirpath.typing.Type;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;

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

    /**
     * Extracts the element type from a collection type.
     * If the type is not a collection, returns the type itself.
     */
    @Nonnull
    private Type extractElementType(@Nonnull final Type collectionType) {
        return (collectionType instanceof CollectionType ct)
            ? ct.elementType()
            : collectionType;
    }

    /**
     * Handles special infrastructure functions not yet in OperationRegistry.
     * These will eventually be migrated to the registry.
     */
    @Nonnull
    private IRNode handleInfrastructureFunctions(
        @Nonnull final AstFunctionCall call,
        @Nonnull final IRNode targetIR
    ) {
        // Analyze remaining arguments normally (none of these take lambdas)
        final List<IRNode> args = Stream.concat(
            Stream.of(targetIR),
            call.arguments().stream().map(this::analyze)
        ).toList();

        return switch (call.functionName()) {
            case "getValue" -> new CastToSystem(args.get(0));
            case "equals" -> new Equals(args.get(0), args.get(1));
            case "union", "|" -> new Union(args.get(0), args.get(1));
            default -> throw new UnsupportedOperationException(
                "Function '" + call.functionName() + "' is not supported"
            );
        };
    }

    @Nonnull
    private IRNode resolveFunctionCall(@Nonnull final AstFunctionCall call) {
        // Resolve implicit target if needed
        final AstFunctionCall resolvedCall = resolveWithImplicitTarget(call);

        // Resolve target (always needed, even for lambdas)
        final IRNode targetIR = analyze(resolvedCall.target());

        // Get all signatures for this function
        final List<SignatureDefinition> signatures = OperationRegistry.getSignatures(call.functionName());

        if (signatures.isEmpty()) {
            // Fallback to special handling for infrastructure functions
            return handleInfrastructureFunctions(call, targetIR);
        }

        // Filter signatures by arity (number of arguments + 1 for target)
        final int actualArgCount = call.arguments().size() + 1; // +1 for target
        final List<SignatureDefinition> matchingSignatures = signatures.stream()
            .filter(sig -> sig.canApplyToArgumentCount(actualArgCount))
            .toList();

        if (matchingSignatures.isEmpty()) {
            throw new IllegalArgumentException(
                "No signature for '" + call.functionName() + "' matches " +
                actualArgCount + " arguments"
            );
        }

        // Check lambda signature invariant:
        // If multiple matching signatures AND any has lambdas → illegal state
        if (matchingSignatures.size() > 1 &&
            matchingSignatures.stream().anyMatch(SignatureDefinition::hasLambdaParameters)) {
            throw new IllegalStateException(
                "Function '" + call.functionName() + "' has " + matchingSignatures.size() +
                " matching signatures with lambda parameters. " +
                "Lambda signatures cannot be overloaded."
            );
        }

        // Get the signature (now guaranteed to be unambiguous for arity)
        final SignatureDefinition sig = matchingSignatures.get(0);

        // Eagerly create lambda analyzer (even if not needed - cheap operation)
        final Type elementType = extractElementType(targetIR.getType());
        final Analyzer thisAnalyzer = withThisType(elementType);

        // Analyze arguments based on signature parameter types using streams
        final List<IRNode> args = Stream.concat(
            Stream.of(targetIR),
            IntStream.range(0, call.arguments().size())
                .mapToObj(i -> {
                    final Type paramType = sig.parameterTypes().get(i + 1); // +1 for target
                    final AstNode argAst = call.arguments().get(i);

                    if (paramType instanceof LambdaType) {
                        // Use thisAnalyzer for lambda context
                        final IRNode lambdaBody = thisAnalyzer.analyze(argAst);
                        return new Lambda(lambdaBody);
                    } else {
                        // Use current analyzer for normal arguments
                        return analyze(argAst);
                    }
                })
        ).toList();

        // Resolve with OverloadResolver (will pick best match)
        final OverloadResolver.ResolvedCall resolvedCallResult =
            OverloadResolver.resolveCall(matchingSignatures, args);

        return new Operation(call.functionName(), resolvedCallResult.args(),
            resolvedCallResult.signature());
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
