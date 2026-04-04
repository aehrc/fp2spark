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
import com.example.fhirpath.operation.signature.ResolvedSignature;
import com.example.fhirpath.operation.signature.SignatureDefinition;
import com.example.fhirpath.typing.Cardinality;
import com.example.fhirpath.typing.ChoiceTypeLike;
import com.example.fhirpath.typing.CodingValue;
import com.example.fhirpath.typing.DateTimeValue;
import com.example.fhirpath.typing.DateValue;
import com.example.fhirpath.typing.FieldSpec;
import com.example.fhirpath.typing.InlineResourceType;
import com.example.fhirpath.typing.LambdaType;
import com.example.fhirpath.typing.QuantityValue;
import com.example.fhirpath.typing.ResourceType;
import com.example.fhirpath.typing.Shape;
import com.example.fhirpath.typing.TimeValue;
import com.example.fhirpath.typing.Type;
import com.example.fhirpath.typing.TypeSpecifier;
import com.example.fhirpath.typing.Types;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
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

  @Nonnull private final Map<String, IRNode> userVariables;

  /** Creates an analyzer with no context or resource type. */
  public Analyzer() {
    this(AstVariable.resourceVariable(), InlineResourceType.EMPTY, null, Map.of());
  }

  /**
   * Creates an analyzer with the given context node.
   *
   * @param contextNode the context node for %context resolution
   */
  public Analyzer(@Nonnull final AstNode contextNode) {
    this(contextNode, InlineResourceType.EMPTY, null, Map.of());
  }

  /**
   * Creates an analyzer with the given resource type.
   *
   * @param resourceSpec the resource type specification
   */
  public Analyzer(@Nonnull final ResourceType resourceSpec) {
    this(AstVariable.resourceVariable(), resourceSpec, null, Map.of());
  }

  /**
   * Creates an analyzer with the given context node and resource type.
   *
   * @param contextNode the context node for %context resolution
   * @param resourceSpec the resource type specification
   */
  public Analyzer(@Nonnull final AstNode contextNode, @Nonnull final ResourceType resourceSpec) {
    this(contextNode, resourceSpec, null, Map.of());
  }

  /**
   * Creates an analyzer with the given resource type and user-defined variables.
   *
   * @param resourceSpec the resource type specification
   * @param userVariables named variables available as %name in FHIRPath expressions
   */
  public Analyzer(
      @Nonnull final ResourceType resourceSpec, @Nonnull final Map<String, IRNode> userVariables) {
    this(AstVariable.resourceVariable(), resourceSpec, null, userVariables);
  }

  /** Private constructor for full configuration including lambda $this binding. */
  private Analyzer(
      @Nonnull final AstNode contextNode,
      @Nonnull final ResourceType resourceSpec,
      @Nullable final Shape thisShape,
      @Nonnull final Map<String, IRNode> userVariables) {
    this.contextNode = contextNode;
    this.resourceSpec = resourceSpec;
    this.thisShape = thisShape;
    this.userVariables = userVariables;
  }

  /**
   * Creates an analyzer for lambda body with $this bound to the specified shape.
   *
   * @param shape the shape (type + cardinality) of $this
   */
  private Analyzer withThisShape(@Nonnull final Shape shape) {
    return new Analyzer(this.contextNode, this.resourceSpec, shape, this.userVariables);
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
   * <p>Current transformations:
   *
   * <ul>
   *   <li>{@code exists(criteria)} → {@code where(criteria).exists()}
   *   <li>{@code extension()} → {@code .extension} traversal (all extensions, convenience shortcut)
   *   <li>{@code extension(url)} → {@code .extension.where(url = <url>)}
   * </ul>
   */
  @Nonnull
  private AstNode desugarFunctionCall(@Nonnull final AstFunctionCall call) {
    // exists(criteria) → where(criteria).exists()
    if ("exists".equals(call.functionName()) && call.arguments().size() == 1) {
      final AstFunctionCall whereCall =
          new AstFunctionCall("where", call.target(), call.arguments());
      return new AstFunctionCall("exists", whereCall, List.of());
    }

    // extension() with no args is a convenience shortcut (not in the FHIR FHIRPath spec).
    // Equivalent to the plain .extension traversal, returning all extensions on the element.
    if ("extension".equals(call.functionName()) && call.arguments().isEmpty()) {
      return new AstTraversal("extension", call.target());
    }

    // extension(url) → .extension.where(url = <url>)
    if ("extension".equals(call.functionName()) && call.arguments().size() == 1) {
      final AstTraversal extensionTraversal = new AstTraversal("extension", call.target());
      final AstBinaryOperator urlEquals =
          new AstBinaryOperator("=", new AstTraversal("url"), call.arguments().get(0));
      return new AstFunctionCall("where", extensionTraversal, List.of(urlEquals));
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
      default -> {
        // Check user-defined variables before throwing.
        final IRNode userVar = userVariables.get(variable.name());
        if (userVar != null) {
          yield userVar;
        }
        throw new InvalidExpressionException(
            "Unknown FHIRPath environment variable: " + variable.name(), null);
      }
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

    // Check for type operations (ofType, is, as) — these take type specifiers, not expressions
    final Optional<IRNode> typeOp = resolveTypeOperation(resolvedCall, targetIr);
    if (typeOp.isPresent()) {
      return typeOp.get();
    }

    // Check for SQL on FHIR key functions (getResourceKey, getReferenceKey)
    final Optional<IRNode> keyOp = resolveKeyFunction(resolvedCall, targetIr);
    if (keyOp.isPresent()) {
      return keyOp.get();
    }

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

    // FHIRPath type specifier shorthand: "Patient.X" ≡ "X" when the context is already a Patient.
    // Assumes resource type name never collides with a field name (true for FHIR resources).
    // Must be extended if polymorphic collection support is added.
    if (targetIr instanceof Resource res
        && resolvedTraversal.path().equals(res.type().getResourceName())) {
      return res;
    }

    return targetIr
        .getType()
        .resolveField(resolvedTraversal.path())
        .map(fieldSpec -> (IRNode) new Traversal(targetIr, fieldSpec))
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

  /**
   * Resolves type operations (ofType, is, as) that take type specifiers instead of expressions.
   *
   * <p>These are intercepted before normal signature resolution because their arguments are type
   * names, not evaluated expressions.
   *
   * @param call the function call AST node
   * @param targetIr the analyzed target expression
   * @return the resolved IR node, or empty if this is not a type operation
   */
  @Nonnull
  private Optional<IRNode> resolveTypeOperation(
      @Nonnull final AstFunctionCall call, @Nonnull final IRNode targetIr) {
    final String name = call.functionName();
    if (!"ofType".equals(name) && !"is".equals(name) && !"as".equals(name)) {
      return Optional.empty();
    }

    final TypeSpecifier typeSpec = extractTypeSpecifier(call);
    final Type targetType = targetIr.getType();

    // is/as are singleton operators — reject MANY cardinality (ofType works on collections)
    if (!"ofType".equals(name) && targetIr.getCardinality() == Cardinality.MANY) {
      throw new InvalidExpressionException(
          "Operator '" + name + "' requires a singleton input; use ofType() for collections", null);
    }

    if (targetType instanceof ChoiceTypeLike choiceType) {
      return Optional.of(resolveChoiceTypeOperation(name, choiceType, targetIr, typeSpec));
    }

    // Non-choice type: static type checking
    return Optional.of(resolveNonChoiceTypeOperation(name, targetIr, typeSpec));
  }

  /**
   * Extracts a validated TypeSpecifier from a function call argument.
   *
   * <p>Handles both desugared is/as (AstLiteral("Quantity")) and ofType(Quantity) parsed as
   * AstTraversal. Also used by {@code getReferenceKey(Type)} for its optional type argument.
   */
  @Nonnull
  private TypeSpecifier extractTypeSpecifier(@Nonnull final AstFunctionCall call) {
    if (call.arguments().isEmpty()) {
      throw new InvalidExpressionException(
          "Function '" + call.functionName() + "' requires a type argument", null);
    }
    final AstNode arg = call.arguments().get(0);
    final String raw;
    if (arg instanceof AstLiteral lit && lit.value() instanceof String s) {
      raw = s;
    } else if (arg instanceof AstTraversal trav) {
      raw = reconstructQualifiedName(trav);
    } else {
      throw new InvalidExpressionException(
          "Function '"
              + call.functionName()
              + "' requires a type specifier, got: "
              + arg.getClass().getSimpleName(),
          null);
    }
    try {
      return TypeSpecifier.fromExpression(raw);
    } catch (final IllegalArgumentException e) {
      throw new InvalidExpressionException(
          "Invalid type specifier '" + raw + "': " + e.getMessage(), null);
    }
  }

  /**
   * Reconstructs a qualified type name from a traversal chain.
   *
   * <p>When a qualified type specifier like {@code System.HumanName} is parsed as a function
   * argument, the parser creates a chain of traversals: {@code AstTraversal("HumanName",
   * target=AstTraversal("System"))}. This method walks the chain to reconstruct the dotted name.
   *
   * <p>For unqualified names (e.g., {@code ofType(Quantity)}), the traversal has no target and the
   * bare path is returned directly.
   *
   * @param trav the traversal node representing the type specifier argument
   * @return the reconstructed type name (e.g., "System.HumanName" or "Quantity")
   */
  @Nonnull
  private static String reconstructQualifiedName(@Nonnull final AstTraversal trav) {
    if (trav.target() instanceof AstTraversal parent) {
      if (parent.target() != null) {
        throw new InvalidExpressionException(
            "Type specifier must have at most one namespace qualifier, got: "
                + parent.target()
                + "."
                + parent.path()
                + "."
                + trav.path(),
            null);
      }
      return parent.path() + "." + trav.path();
    }
    return trav.path();
  }

  /**
   * Resolves a type operation on a choice type by narrowing to a specific variant.
   *
   * <p>Pathling's FHIR encoders flatten choice types: variant columns (e.g., {@code valueQuantity},
   * {@code valueString}) are siblings at the parent level, not nested under a {@code value} struct.
   * The variant traversal therefore skips the choice-type traversal and attaches directly to the
   * parent node.
   */
  @Nonnull
  private IRNode resolveChoiceTypeOperation(
      @Nonnull final String operation,
      @Nonnull final ChoiceTypeLike choiceType,
      @Nonnull final IRNode targetIr,
      @Nonnull final TypeSpecifier typeSpec) {
    final Optional<FieldSpec> variant = choiceType.resolveVariant(typeSpec.toFhirVariantName());
    if (variant.isEmpty()) {
      // Unknown variant — return empty for ofType/as, false for is
      if ("is".equals(operation)) {
        return new Literal(false, Types.BOOLEAN);
      }
      return new Literal(null, Types.NULL);
    }

    // Skip the choice traversal node and attach variant to its parent.
    // e.g., value.ofType(Quantity) → Traversal(resource, "valueQuantity")
    //        not Traversal(Traversal(resource, "value"), "valueQuantity")
    final IRNode parentNode =
        (targetIr instanceof Traversal choiceTraversal) ? choiceTraversal.target() : targetIr;
    final Traversal variantTraversal = new Traversal(parentNode, variant.get());

    return switch (operation) {
      case "ofType", "as" -> variantTraversal;
      case "is" -> {
        // is → check if the variant column is non-null
        final ResolvedSignature isSig =
            new ResolvedSignature(List.of(variantTraversal.getType()), Shape.single(Types.BOOLEAN));
        yield new Operation("is", List.of(variantTraversal), isSig);
      }
      default -> throw new IllegalStateException("Unexpected type operation: " + operation);
    };
  }

  /**
   * Resolves a type operation on a non-choice type.
   *
   * <p>Cardinality validation for {@code is}/{@code as} is handled upstream in {@link
   * #resolveTypeOperation}, so this method only handles type matching logic.
   *
   * <p>For {@code is}, a runtime null-propagating {@link Operation} node is emitted instead of a
   * static {@link Literal}, so that empty (null) singletons return empty rather than a boolean.
   */
  @Nonnull
  private IRNode resolveNonChoiceTypeOperation(
      @Nonnull final String operation,
      @Nonnull final IRNode targetIr,
      @Nonnull final TypeSpecifier typeSpec) {

    // NULL type (e.g., {}) → always empty regardless of operation
    if (targetIr.getType() == Types.NULL) {
      return new Literal(null, Types.NULL);
    }

    final boolean matches = typeSpec.matchesType(targetIr.getType());

    return switch (operation) {
      case "is" -> {
        // Emit runtime null-check: CASE WHEN value IS NOT NULL THEN matches ELSE NULL END
        final ResolvedSignature sig =
            new ResolvedSignature(
                List.of(targetIr.getType(), Types.BOOLEAN), Shape.single(Types.BOOLEAN));
        yield new Operation("is", List.of(targetIr, new Literal(matches, Types.BOOLEAN)), sig);
      }
      case "as" -> matches ? targetIr : new Literal(null, Types.NULL);
      case "ofType" -> matches ? targetIr : new Literal(null, Types.NULL);
      default -> throw new IllegalStateException("Unexpected type operation: " + operation);
    };
  }

  /**
   * Resolves SQL on FHIR key functions (getResourceKey, getReferenceKey).
   *
   * <p>These are intercepted before normal signature resolution because:
   *
   * <ul>
   *   <li>{@code getResourceKey()} requires access to the resource type name from the IR
   *   <li>{@code getReferenceKey(Type)} takes an optional type specifier argument (not an
   *       expression)
   * </ul>
   */
  @Nonnull
  private Optional<IRNode> resolveKeyFunction(
      @Nonnull final AstFunctionCall call, @Nonnull final IRNode targetIr) {
    return switch (call.functionName()) {
      case "getResourceKey" -> Optional.of(resolveGetResourceKey(call, targetIr));
      case "getReferenceKey" -> Optional.of(resolveGetReferenceKey(call, targetIr));
      default -> Optional.empty();
    };
  }

  /**
   * Resolves {@code getResourceKey()} — returns "ResourceType/id".
   *
   * <p>Must be called on a Resource node with no arguments.
   */
  @Nonnull
  private IRNode resolveGetResourceKey(
      @Nonnull final AstFunctionCall call, @Nonnull final IRNode targetIr) {
    if (!call.arguments().isEmpty()) {
      throw new InvalidExpressionException("getResourceKey() takes no arguments", null);
    }
    if (!(targetIr instanceof Resource)) {
      throw new InvalidExpressionException(
          "getResourceKey() can only be called on a resource root", null);
    }
    final ResolvedSignature sig =
        new ResolvedSignature(List.of(targetIr.getType()), Shape.single(Types.STRING));
    return new Operation("getResourceKey", List.of(targetIr), sig);
  }

  /**
   * Resolves {@code getReferenceKey([type])} — returns the reference string, optionally filtered by
   * type.
   *
   * <p>Must be called on a Reference element. The optional type argument is a type specifier (not
   * an expression).
   */
  @Nonnull
  private IRNode resolveGetReferenceKey(
      @Nonnull final AstFunctionCall call, @Nonnull final IRNode targetIr) {
    if (call.arguments().size() > 1) {
      throw new InvalidExpressionException("getReferenceKey() takes 0 or 1 arguments", null);
    }
    if (!"Reference".equals(targetIr.getType().getName())) {
      throw new InvalidExpressionException(
          "getReferenceKey() can only be called on a Reference element", null);
    }
    // Preserve target cardinality: singular Reference → ?STRING, collection → *STRING
    final Shape resultShape = Shape.of(Types.STRING, targetIr.getShape().cardinality());
    final ResolvedSignature sig = new ResolvedSignature(List.of(targetIr.getType()), resultShape);
    if (call.arguments().isEmpty()) {
      return new Operation("getReferenceKey", List.of(targetIr), sig);
    }
    // Extract type specifier from argument
    final TypeSpecifier typeSpec = extractTypeSpecifier(call);
    return new Operation(
        "getReferenceKey",
        List.of(targetIr, new Literal(typeSpec.getTypeName(), Types.STRING)),
        sig);
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
      case QuantityValue ignored -> Types.QUANTITY;
      case CodingValue ignored -> Types.CODING;
      default ->
          throw new InvalidExpressionException(
              "Unsupported literal value type: " + value.getClass().getSimpleName(), null);
    };
  }
}
