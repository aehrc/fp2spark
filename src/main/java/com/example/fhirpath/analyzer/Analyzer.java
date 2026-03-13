package com.example.fhirpath.analyzer;

import static com.example.fhirpath.ast.AstVariable.CONTEXT_VARIABLE;
import static com.example.fhirpath.ast.AstVariable.RESOURCE_VARIABLE;

import com.example.fhirpath.ast.AstBinaryOperator;
import com.example.fhirpath.ast.AstFunctionCall;
import com.example.fhirpath.ast.AstIterationVariable;
import com.example.fhirpath.ast.AstLiteral;
import com.example.fhirpath.ast.AstNode;
import com.example.fhirpath.ast.AstTraversal;
import com.example.fhirpath.ast.AstVariable;
import com.example.fhirpath.ast.WithTarget;
import com.example.fhirpath.ir.IRNode;
import com.example.fhirpath.ir.Lambda;
import com.example.fhirpath.ir.Literal;
import com.example.fhirpath.ir.Operation;
import com.example.fhirpath.ir.Resource;
import com.example.fhirpath.ir.ThisReference;
import com.example.fhirpath.ir.Traversal;
import com.example.fhirpath.operation.OperationResolver;
import com.example.fhirpath.operation.OperatorNormalizer;
import com.example.fhirpath.operation.OverloadResolutionException;
import com.example.fhirpath.operation.OverloadResolver;
import com.example.fhirpath.operation.signature.SignatureDefinition;
import com.example.fhirpath.typing.ComplexType;
import com.example.fhirpath.typing.DateTimeValue;
import com.example.fhirpath.typing.DateValue;
import com.example.fhirpath.typing.LambdaType;
import com.example.fhirpath.typing.ResourceType;
import com.example.fhirpath.typing.Shape;
import com.example.fhirpath.typing.TimeValue;
import com.example.fhirpath.typing.Type;
import com.example.fhirpath.typing.Types;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Analyzes FHIRPath AST nodes and produces typed IR nodes.
 *
 * <p>The analysis is a two-phase process:
 *
 * <ol>
 *   <li>Desugar: applies syntactic transformations (e.g., {@code exists(criteria)} → {@code
 *       where(criteria).exists()})
 *   <li>Resolve: performs semantic resolution with type inference and overload resolution
 * </ol>
 */
public class Analyzer {
  @Nonnull private final AstNode contextNode;
  @Nonnull private final ResourceType resourceSpec;

  @Nullable
  private final Shape thisShape; // For lambda analysis - tracks both type and cardinality of $this

  /** Creates an analyzer with no context or resource type. */
  public Analyzer() {
    // by default, no context
    this(AstVariable.resourceVariable(), ResourceType.EMPTY, null);
  }

  /**
   * Creates an analyzer with the given context node.
   *
   * @param contextNode the context node for %context resolution
   */
  public Analyzer(@Nonnull final AstNode contextNode) {
    this(contextNode, ResourceType.EMPTY, null);
  }

  /**
   * Creates an analyzer with the given resource type.
   *
   * @param resourceSpec the resource type specification
   */
  public Analyzer(@Nonnull final ResourceType resourceSpec) {
    this(AstVariable.resourceVariable(), resourceSpec, null);
  }

  /**
   * Creates an analyzer with the given context node and resource type.
   *
   * @param contextNode the context node for %context resolution
   * @param resourceSpec the resource type specification
   */
  public Analyzer(@Nonnull final AstNode contextNode, @Nonnull final ResourceType resourceSpec) {
    this(contextNode, resourceSpec, null);
  }

  /** Private constructor for lambda analysis with $this binding. */
  private Analyzer(
      @Nonnull final AstNode contextNode,
      @Nonnull final ResourceType resourceSpec,
      @Nullable final Shape thisShape) {
    this.contextNode = contextNode;
    this.resourceSpec = resourceSpec;
    this.thisShape = thisShape;
  }

  /**
   * Creates an analyzer for lambda body with $this bound to the specified shape.
   *
   * @param shape the shape (type + cardinality) of $this
   */
  private Analyzer withThisShape(@Nonnull final Shape shape) {
    return new Analyzer(this.contextNode, this.resourceSpec, shape);
  }

  /**
   * Returns the implicit target for expressions without an explicit target. Inside a lambda
   * context, returns $this. Outside a lambda context, returns %context.
   */
  @Nonnull
  private AstNode getImplicitTarget() {
    return (thisShape != null)
        ? AstIterationVariable.thisVariable()
        : AstVariable.contextVariable();
  }

  /**
   * Resolves a node with implicit target handling. If the node has no explicit target, applies the
   * implicit target before resolution.
   *
   * @param node A node that may have an implicit target (AstFunctionCall or AstTraversal)
   * @param <T> The concrete type of WithTarget
   * @return The node with implicit target resolved (returns node with target set)
   */
  @Nonnull
  private <T extends WithTarget<T>> T resolveWithImplicitTarget(@Nonnull final T node) {
    return node.target() == null ? node.withTarget(getImplicitTarget()) : node;
  }

  /**
   * Analyzes an AST node and produces an IR node. Two-phase process: 1. Desugar: AST → AST
   * (syntactic transformations) 2. Resolve: AST → IR (semantic resolution with types)
   */
  @Nonnull
  public IRNode analyze(@Nonnull final AstNode node) {
    // Phase 1: Apply syntactic transformations (desugaring)
    final AstNode desugared = desugar(node);

    // Phase 2: Resolve to typed IR
    return resolveToIr(desugared);
  }

  /**
   * Applies AST-level transformations (desugaring) before resolution. Returns the transformed node,
   * or the original if no transformation applies.
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
   *
   * <p>Current transformations: - exists(criteria) → where(criteria).exists()
   */
  @Nonnull
  private AstNode desugarFunctionCall(@Nonnull final AstFunctionCall call) {
    // exists(criteria) → where(criteria).exists()
    if ("exists".equals(call.functionName()) && call.arguments().size() == 1) {
      final AstFunctionCall whereCall =
          new AstFunctionCall("where", call.target(), call.arguments());
      return new AstFunctionCall("exists", whereCall, List.of());
    }

    return call;
  }

  /**
   * Resolves a (possibly desugared) AST node to a typed IR node. This is the core AST → IR
   * transformation with type resolution.
   */
  @Nonnull
  private IRNode resolveToIr(@Nonnull final AstNode node) {
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
    throw new InvalidExpressionException(
        "Unsupported AST node type: " + node.getClass().getSimpleName(), null);
  }

  private IRNode resolveVariable(final AstVariable variable) {
    return switch (variable.name()) {
      case CONTEXT_VARIABLE -> new Analyzer(resourceSpec).analyze(contextNode);
      case RESOURCE_VARIABLE -> new Resource(resourceSpec);
      default ->
          throw new InvalidExpressionException(
              "Unknown FHIRPath environment variable: " + variable.name(), null);
    };
  }

  private IRNode resolveIterationVariable(final AstIterationVariable iterVar) {
    return switch (iterVar.name()) {
      case AstIterationVariable.THIS -> {
        if (thisShape == null) {
          throw new InvalidExpressionException(
              "$this can only be used in lambda expressions (e.g., within where() or select())",
              null);
        }
        yield new ThisReference(thisShape);
      }
      case AstIterationVariable.INDEX ->
          throw new UnsupportedFeatureException("$index iteration variable", null);
      case AstIterationVariable.TOTAL ->
          throw new UnsupportedFeatureException("$total iteration variable", null);
      default ->
          throw new InvalidExpressionException(
              "Unknown iteration variable: " + iterVar.name(), null);
    };
  }

  /**
   * Extracts the element type. In the new type system, Type is always the element type (cardinality
   * is separate in Shape).
   */
  @Nonnull
  private Type extractElementType(@Nonnull final Type type) {
    return type;
  }

  @Nonnull
  private IRNode resolveFunctionCall(@Nonnull final AstFunctionCall call) {
    // Resolve implicit target if needed
    final AstFunctionCall resolvedCall = resolveWithImplicitTarget(call);

    // Resolve target (always needed, even for lambdas)
    final IRNode targetIr = analyze(resolvedCall.target());

    // Get all signatures for this function
    final List<SignatureDefinition> signatures =
        OperationResolver.getSignatures(call.functionName());

    if (signatures.isEmpty()) {
      throw new UnsupportedFeatureException("Function '" + call.functionName() + "'", null);
    }

    // Filter signatures by arity (number of arguments + 1 for target)
    final int actualArgCount = call.arguments().size() + 1; // +1 for target
    final List<SignatureDefinition> matchingSignatures =
        signatures.stream().filter(sig -> sig.canApplyToArgumentCount(actualArgCount)).toList();

    if (matchingSignatures.isEmpty()) {
      throw new OverloadResolutionException(
          call.functionName(),
          List.of(), // Argument types not yet resolved
          null);
    }

    // Check lambda signature invariant:
    // If multiple matching signatures AND any has lambdas → illegal state
    if (matchingSignatures.size() > 1
        && matchingSignatures.stream().anyMatch(SignatureDefinition::hasLambdaParameters)) {
      // This is an internal error - registry should not have ambiguous lambda signatures
      throw new IllegalStateException(
          "INTERNAL ERROR: Function '"
              + call.functionName()
              + "' has "
              + matchingSignatures.size()
              + " matching signatures with lambda parameters. Lambda signatures cannot be"
              + " overloaded. This indicates a bug in the operation registry.");
    }

    // Get the signature (now guaranteed to be unambiguous for arity)
    final SignatureDefinition sig = matchingSignatures.get(0);

    // Create lambda analyzer with appropriate $this binding
    final Analyzer lambdaAnalyzer = createLambdaAnalyzer(sig, targetIr);

    // Analyze arguments based on signature parameter types
    final List<IRNode> args = analyzeArguments(call, sig, targetIr, lambdaAnalyzer);

    // Resolve with OperationResolver (will pick best match and check cardinality)
    final OverloadResolver.ResolvedCall resolvedCallResult =
        OperationResolver.resolveCall(call.functionName(), matchingSignatures, args);

    return new Operation(
        call.functionName(), resolvedCallResult.args(), resolvedCallResult.signature());
  }

  /**
   * Creates a lambda analyzer with appropriate $this binding based on signature's lambda binding
   * strategy.
   *
   * <p>Lambda parameters in FHIRPath can be bound to either individual elements (ELEMENT_WISE) or
   * the entire collection (COLLECTION_WISE), determined by the function's signature.
   *
   * @param sig The signature definition containing lambda binding strategy
   * @param targetIr The analyzed target expression whose type/shape determines $this binding
   * @return Analyzer with $this binding for lambda evaluation, or null if signature has no lambda
   *     parameters
   */
  @Nullable
  private Analyzer createLambdaAnalyzer(
      @Nonnull final SignatureDefinition sig, @Nonnull final IRNode targetIr) {
    if (sig.lambdaBinding() == null) {
      return null; // No lambda parameters in this signature
    }

    // Determine $this binding shape based on lambda binding strategy
    final Shape thisBindingShape =
        switch (sig.lambdaBinding()) {
          case ELEMENT_WISE -> Shape.single(extractElementType(targetIr.getType()));
          case COLLECTION_WISE -> targetIr.getShape(); // Preserves MANY cardinality
        };

    return withThisShape(thisBindingShape);
  }

  /**
   * Analyzes function call arguments based on signature parameter types. Handles both lambda and
   * non-lambda arguments, with variadic padding support.
   *
   * @param call The function call AST node
   * @param sig The signature to match
   * @param targetIr The analyzed target expression
   * @param lambdaAnalyzer Analyzer with $this binding for lambda parameters (nullable)
   * @return List of analyzed arguments including target
   */
  @Nonnull
  private List<IRNode> analyzeArguments(
      @Nonnull final AstFunctionCall call,
      @Nonnull final SignatureDefinition sig,
      @Nonnull final IRNode targetIr,
      @Nullable final Analyzer lambdaAnalyzer) {
    return Stream.concat(
            Stream.of(targetIr),
            IntStream.range(1, sig.parameterTypes().size()) // Start at 1 (skip target at index 0)
                .mapToObj(i -> analyzeArgument(call, sig, i, lambdaAnalyzer)))
        .toList();
  }

  /**
   * Analyzes a single function call argument at the specified parameter index.
   *
   * @param call The function call AST node
   * @param sig The signature definition
   * @param paramIndex Parameter index in signature (0 is target, 1+ are call arguments)
   * @param lambdaAnalyzer Analyzer with $this binding for lambda parameters (nullable)
   * @return Analyzed argument IR node
   */
  @Nonnull
  private IRNode analyzeArgument(
      @Nonnull final AstFunctionCall call,
      @Nonnull final SignatureDefinition sig,
      final int paramIndex,
      @Nullable final Analyzer lambdaAnalyzer) {
    final Type paramType = sig.parameterTypes().get(paramIndex);

    // Get AST argument, or use null literal if exhausted (variadic padding)
    final AstNode argAst =
        (paramIndex - 1) < call.arguments().size()
            ? call.arguments().get(paramIndex - 1)
            : AstLiteral.NULL;

    if (paramType instanceof LambdaType) {
      if (lambdaAnalyzer == null) {
        throw new IllegalStateException(
            "Lambda parameter found but no binding strategy specified for function: "
                + call.functionName());
      }
      // Use lambdaAnalyzer for lambda context
      final IRNode lambdaBody = lambdaAnalyzer.analyze(argAst);
      return new Lambda(lambdaBody);
    } else {
      // Use current analyzer for normal arguments
      // AstLiteral.NULL → Literal(null, Type.NULL)
      return analyze(argAst);
    }
  }

  private IRNode resolveTraversal(final AstTraversal traversal) {
    // Resolve implicit target if needed
    final AstTraversal resolvedTraversal = resolveWithImplicitTarget(traversal);
    final IRNode targetIr = analyze(resolvedTraversal.target());
    return Optional.of(targetIr.getType())
        .filter(ComplexType.class::isInstance)
        .map(ComplexType.class::cast)
        .flatMap(ct -> ct.getField(resolvedTraversal.path()))
        // for ComplexTypes with required field create a Traversal
        .map(fieldSpec -> (IRNode) new Traversal(targetIr, fieldSpec))
        // otherwise an empty collection
        .orElseGet(() -> new Literal(null, Types.NULL));
  }

  /**
   * Resolves binary operators to IR nodes. Binary operators are syntactic sugar for function calls
   * (e.g., a + b ≡ add(a, b)).
   */
  private IRNode resolveBinaryOp(final AstBinaryOperator binaryOp) {
    // Analyze both operands
    final IRNode leftArg = analyze(binaryOp.left());
    final IRNode rightArg = analyze(binaryOp.right());

    final String operatorSymbol = binaryOp.operator();

    // Normalize operator symbol to canonical function name (e.g., "=" → "equals", "+" → "add")
    final String operationName = OperatorNormalizer.normalize(operatorSymbol);

    // Standard operations: delegate to OperationResolver
    final OverloadResolver.ResolvedCall resolvedCall =
        OperationResolver.resolveBinaryOperator(operatorSymbol, leftArg, rightArg);
    return new Operation(operationName, resolvedCall.args(), resolvedCall.signature());
  }

  private Type inferType(final Object value) {
    if (value == null) return Types.NULL;
    return switch (value) {
      case Integer ignored -> Types.INTEGER;
      case BigDecimal ignored -> Types.DECIMAL;
      case Boolean ignored -> Types.BOOLEAN;
      case String ignored -> Types.STRING;
      case DateValue ignored -> Types.DATE;
      case DateTimeValue ignored -> Types.DATE_TIME;
      case TimeValue ignored -> Types.TIME;
      default ->
          throw new InvalidExpressionException(
              "Unsupported literal value type: " + value.getClass().getSimpleName(), null);
    };
  }
}
