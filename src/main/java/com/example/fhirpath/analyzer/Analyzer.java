package com.example.fhirpath.analyzer;

import com.example.fhirpath.ast.*;
import com.example.fhirpath.ir.*;
import com.example.fhirpath.typing.*;

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
    private final Shape thisShape;  // For lambda analysis - tracks both type and cardinality of $this

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
            @Nullable Shape thisShape
    ) {
        this.contextNode = contextNode;
        this.resourceSpec = resourceSpec;
        this.thisShape = thisShape;
    }

    /**
     * Creates an analyzer for lambda body with $this bound to the specified shape.
     *
     * @param shape the shape (type + cardinality) of $this
     */
    private Analyzer withThisShape(@Nonnull Shape shape) {
        return new Analyzer(this.contextNode, this.resourceSpec, shape);
    }

    /**
     * Returns the implicit target for expressions without an explicit target.
     * Inside a lambda context, returns $this.
     * Outside a lambda context, returns %context.
     */
    @Nonnull
    private AstNode getImplicitTarget() {
        return (thisShape != null)
                ? AstIterationVariable.thisVariable()
                : AstVariable.contextVariable();
    }

    /**
     * Resolves a node with implicit target handling.
     * If the node has no explicit target, applies the implicit target before resolution.
     *
     * @param node A node that may have an implicit target (AstFunctionCall or AstTraversal)
     * @param <T>  The concrete type of WithTarget
     * @return The node with implicit target resolved (returns node with target set)
     */
    @Nonnull
    private <T extends WithTarget<T>> T resolveWithImplicitTarget(@Nonnull T node) {
        return node.target() == null ? node.withTarget(getImplicitTarget()) : node;
    }

    /**
     * Analyzes an AST node and produces an IR node.
     * Two-phase process:
     * 1. Desugar: AST → AST (syntactic transformations)
     * 2. Resolve: AST → IR (semantic resolution with types)
     */
    @Nonnull
    public IRNode analyze(@Nonnull final AstNode node) {
        // Phase 1: Apply syntactic transformations (desugaring)
        final AstNode desugared = desugar(node);

        // Phase 2: Resolve to typed IR
        return resolveToIR(desugared);
    }

    /**
     * Applies AST-level transformations (desugaring) before resolution.
     * Returns the transformed node, or the original if no transformation applies.
     */
    @Nonnull
    private AstNode desugar(@Nonnull final AstNode node) {
        if (node instanceof AstFunctionCall call) {
            return desugarFunctionCall(call);
        }
        return node;
    }

    /**
     * Desugars function calls with known equivalences.
     * <p>
     * Current transformations:
     * - exists(criteria) → where(criteria).exists()
     */
    @Nonnull
    private AstNode desugarFunctionCall(@Nonnull final AstFunctionCall call) {
        // exists(criteria) → where(criteria).exists()
        if ("exists".equals(call.functionName()) && call.arguments().size() == 1) {
            final AstFunctionCall whereCall = new AstFunctionCall(
                    "where", call.target(), call.arguments()
            );
            return new AstFunctionCall("exists", whereCall, List.of());
        }

        return call;
    }

    /**
     * Resolves a (possibly desugared) AST node to a typed IR node.
     * This is the core AST → IR transformation with type resolution.
     */
    @Nonnull
    private IRNode resolveToIR(@Nonnull final AstNode node) {
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
        throw new InvalidExpressionException("Unsupported AST node type: " + node.getClass().getSimpleName(), null);
    }

    private IRNode resolveVariable(AstVariable variable) {
        return switch (variable.name()) {
            case CONTEXT_VARIABLE -> new Analyzer(resourceSpec).analyze(contextNode);
            case RESOURCE_VARIABLE -> new Resource(resourceSpec);
            default ->
                    throw new InvalidExpressionException(
                            "Unknown FHIRPath environment variable: " + variable.name(),
                            null
                    );
        };
    }

    private IRNode resolveIterationVariable(AstIterationVariable iterVar) {
        return switch (iterVar.name()) {
            case AstIterationVariable.THIS -> {
                if (thisShape == null) {
                    throw new InvalidExpressionException(
                            "$this can only be used in lambda expressions (e.g., within where() or select())",
                            null
                    );
                }
                yield new ThisReference(thisShape);
            }
            case AstIterationVariable.INDEX -> throw new UnsupportedFeatureException(
                    "$index iteration variable",
                    null
            );
            case AstIterationVariable.TOTAL -> throw new UnsupportedFeatureException(
                    "$total iteration variable",
                    null
            );
            default -> throw new InvalidExpressionException(
                    "Unknown iteration variable: " + iterVar.name(),
                    null
            );
        };
    }

    /**
     * Extracts the element type.
     * In the new type system, Type is always the element type (cardinality is separate in Shape).
     */
    @Nonnull
    private Type extractElementType(@Nonnull final Type type) {
        return type;
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
            default -> throw new UnsupportedFeatureException(
                    "Function '" + call.functionName() + "'",
                    null
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
            throw new OverloadResolutionException(
                    call.functionName(),
                    List.of(),  // Argument types not yet resolved
                    null
            );
        }

        // Check lambda signature invariant:
        // If multiple matching signatures AND any has lambdas → illegal state
        if (matchingSignatures.size() > 1 &&
                matchingSignatures.stream().anyMatch(SignatureDefinition::hasLambdaParameters)) {
            // This is an internal error - registry should not have ambiguous lambda signatures
            throw new IllegalStateException(
                    "INTERNAL ERROR: Function '" + call.functionName() + "' has " + matchingSignatures.size() +
                            " matching signatures with lambda parameters. " +
                            "Lambda signatures cannot be overloaded. This indicates a bug in the operation registry."
            );
        }

        // Get the signature (now guaranteed to be unambiguous for arity)
        final SignatureDefinition sig = matchingSignatures.get(0);

        // Determine $this binding shape based on lambda binding strategy
        final Shape thisBindingShape;
        if (sig.lambdaBinding() != null) {
            thisBindingShape = switch (sig.lambdaBinding()) {
                case ELEMENT_WISE -> Shape.single(extractElementType(targetIR.getType()));
                case COLLECTION_WISE -> targetIR.getShape();  // Preserves MANY cardinality
            };
        } else {
            // No lambda parameters - thisBindingShape won't be used
            thisBindingShape = null;
        }

        // Create lambda analyzer if needed
        final Analyzer thisAnalyzer = thisBindingShape != null
                ? withThisShape(thisBindingShape)
                : null;

        // Analyze arguments based on signature parameter types
        // For variadic functions, pad missing arguments with AstLiteral.NULL
        final List<IRNode> args = Stream.concat(
                Stream.of(targetIR),
                IntStream.range(1, sig.parameterTypes().size())  // Start at 1 (skip target at index 0)
                        .mapToObj(i -> {
                            final Type paramType = sig.parameterTypes().get(i);

                            // Get AST argument, or use null literal if exhausted (variadic padding)
                            final AstNode argAst = (i - 1) < call.arguments().size()
                                    ? call.arguments().get(i - 1)
                                    : AstLiteral.NULL;

                            if (paramType instanceof LambdaType) {
                                if (thisAnalyzer == null) {
                                    throw new IllegalStateException(
                                            "Lambda parameter found but no binding strategy specified for function: " +
                                                    call.functionName()
                                    );
                                }
                                // Use thisAnalyzer for lambda context
                                final IRNode lambdaBody = thisAnalyzer.analyze(argAst);
                                return new Lambda(lambdaBody);
                            } else {
                                // Use current analyzer for normal arguments
                                // AstLiteral.NULL → Literal(null, Type.NULL)
                                return analyze(argAst);
                            }
                        })
        ).toList();

        // Resolve with OverloadResolver (will pick best match and check cardinality)
        final OverloadResolver.ResolvedCall resolvedCallResult =
                OverloadResolver.resolveCall(call.functionName(), matchingSignatures, args);

        return new Operation(call.functionName(), resolvedCallResult.args(),
                resolvedCallResult.signature());
    }

    private IRNode resolveTraversal(AstTraversal traversal) {
        // Resolve implicit target if needed
        AstTraversal resolvedTraversal = resolveWithImplicitTarget(traversal);
        IRNode targetIR = analyze(resolvedTraversal.target());
        return Optional.of(targetIR.getType())
                .filter(ComplexType.class::isInstance)
                .map(ComplexType.class::cast)
                .flatMap(ct -> ct.getField(resolvedTraversal.path()))
                // for ComplexTypes with required field create a Traversal
                .map(fieldSpec -> (IRNode) new Traversal(targetIR, fieldSpec))
                // otherwise an empty collection
                .orElseGet(() -> new Literal(null, Types.NULL));
    }

    private IRNode resolveBinaryOp(AstBinaryOperator binaryOp) {
        return FunctionRegistry.resolve(this, binaryOp);
    }

    private Type inferType(Object value) {
        if (value == null) return Types.NULL;
        if (value instanceof Integer) return Types.INTEGER;
        if (value instanceof BigDecimal) return Types.DECIMAL;
        if (value instanceof Boolean) return Types.BOOLEAN;
        if (value instanceof String) return Types.STRING;
        throw new InvalidExpressionException(
                "Unsupported literal value type: " + value.getClass().getSimpleName(),
                null
        );
    }
}
