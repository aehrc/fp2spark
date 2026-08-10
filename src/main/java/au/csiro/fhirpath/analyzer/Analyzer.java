/*
 * Copyright © 2025-2026 Commonwealth Scientific and Industrial Research
 * Organisation (CSIRO) ABN 41 687 119 230.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package au.csiro.fhirpath.analyzer;

import static au.csiro.fhirpath.ast.AstVariable.CONTEXT_VARIABLE;
import static au.csiro.fhirpath.ast.AstVariable.RESOURCE_VARIABLE;

import au.csiro.fhirpath.ast.AstBinaryOperator;
import au.csiro.fhirpath.ast.AstFunctionCall;
import au.csiro.fhirpath.ast.AstIterationVariable;
import au.csiro.fhirpath.ast.AstLiteral;
import au.csiro.fhirpath.ast.AstNode;
import au.csiro.fhirpath.ast.AstTraversal;
import au.csiro.fhirpath.ast.AstVariable;
import au.csiro.fhirpath.ast.WithTarget;
import au.csiro.fhirpath.ir.IRNode;
import au.csiro.fhirpath.ir.Lambda;
import au.csiro.fhirpath.ir.Literal;
import au.csiro.fhirpath.ir.Operation;
import au.csiro.fhirpath.ir.Resource;
import au.csiro.fhirpath.ir.ThisReference;
import au.csiro.fhirpath.ir.Traversal;
import au.csiro.fhirpath.operation.OperationResolver;
import au.csiro.fhirpath.operation.OperatorNormalizer;
import au.csiro.fhirpath.operation.OverloadResolutionException;
import au.csiro.fhirpath.operation.OverloadResolver;
import au.csiro.fhirpath.operation.signature.ResolvedSignature;
import au.csiro.fhirpath.operation.signature.SignatureDefinition;
import au.csiro.fhirpath.typing.Cardinality;
import au.csiro.fhirpath.typing.ChoiceTypeLike;
import au.csiro.fhirpath.typing.CodingValue;
import au.csiro.fhirpath.typing.DateTimeValue;
import au.csiro.fhirpath.typing.DateValue;
import au.csiro.fhirpath.typing.FieldSpec;
import au.csiro.fhirpath.typing.InlineResourceType;
import au.csiro.fhirpath.typing.LambdaType;
import au.csiro.fhirpath.typing.QuantityValue;
import au.csiro.fhirpath.typing.ResolvedReferenceType;
import au.csiro.fhirpath.typing.ResourceType;
import au.csiro.fhirpath.typing.Shape;
import au.csiro.fhirpath.typing.TimeValue;
import au.csiro.fhirpath.typing.Type;
import au.csiro.fhirpath.typing.TypeInfoValue;
import au.csiro.fhirpath.typing.TypeSpecifier;
import au.csiro.fhirpath.typing.Types;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.math.BigDecimal;
import java.util.ArrayList;
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

    // Check for type() reflection function — needs compile-time type info
    final Optional<IRNode> typeFunc = resolveTypeFunction(resolvedCall, targetIr);
    if (typeFunc.isPresent()) {
      return typeFunc.get();
    }

    // Check for SQL on FHIR key functions (getResourceKey, getReferenceKey)
    final Optional<IRNode> keyOp = resolveKeyFunction(resolvedCall, targetIr);
    if (keyOp.isPresent()) {
      return keyOp.get();
    }

    // Check for resolve() function — extracts type info from References
    if ("resolve".equals(resolvedCall.functionName())) {
      return resolveResolveFunction(resolvedCall, targetIr);
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

    // Choice types must be narrowed via ofType()/is/as before field traversal (D6).
    // Without explicit narrowing, the static analyzer cannot pick a variant.
    if (targetIr.getType() instanceof ChoiceTypeLike choiceType) {
      throw new InvalidExpressionException(
          "Cannot traverse field '"
              + resolvedTraversal.path()
              + "' on choice type "
              + choiceType.getName()
              + "; narrow the choice with ofType(), is, or as first",
          null);
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

    // is/as are singleton operators — reject MANY cardinality (ofType works on collections)
    if (!"ofType".equals(name) && targetIr.getCardinality() == Cardinality.MANY) {
      throw new InvalidExpressionException(
          "Operator '" + name + "' requires a singleton input; use ofType() for collections", null);
    }

    final TypeSpecifier typeSpec = extractTypeSpecifier(call);
    final Type targetType = targetIr.getType();

    if (targetType instanceof ChoiceTypeLike choiceType) {
      return Optional.of(resolveChoiceTypeOperation(name, choiceType, targetIr, typeSpec));
    }

    // Non-choice type: static type checking
    return Optional.of(resolveNonChoiceTypeOperation(name, targetIr, typeSpec));
  }

  /**
   * Resolves the {@code type()} reflection function.
   *
   * <p>The {@code type()} function returns type information (namespace, name, baseType) for each
   * element in the input collection. Since type information is known at compile time for non-choice
   * types and per-variant for choice types, the Analyzer resolves it rather than deferring to
   * runtime.
   *
   * <p>For non-choice types, creates an Operation with: [target, namespace, name, baseType] as
   * literal args. For choice types, creates an Operation with: [parent, col1, ns1, name1, bt1,
   * col2, ns2, name2, bt2, ...] where each group of 4 represents a variant.
   */
  @Nonnull
  private Optional<IRNode> resolveTypeFunction(
      @Nonnull final AstFunctionCall call, @Nonnull final IRNode targetIr) {
    if (!"type".equals(call.functionName())) {
      return Optional.empty();
    }

    if (!call.arguments().isEmpty()) {
      throw new InvalidExpressionException("Function 'type()' takes no arguments", null);
    }

    final Type targetType = targetIr.getType();

    // Empty collection → empty result
    if (targetType == Types.NULL) {
      return Optional.of(new Literal(null, Types.NULL));
    }

    final Shape resultShape = Shape.of(TypeInfoValue.TYPE_INFO_TYPE, targetIr.getCardinality());

    if (targetType instanceof ChoiceTypeLike choiceType) {
      return Optional.of(resolveChoiceTypeFunction(choiceType, targetIr, resultShape));
    }

    // Non-choice: type info is statically known
    final TypeInfoValue info = TypeInfoValue.fromType(targetType);
    final List<IRNode> args =
        List.of(
            targetIr,
            new Literal(info.namespace(), Types.STRING),
            new Literal(info.name(), Types.STRING),
            new Literal(info.baseType(), Types.STRING));
    final ResolvedSignature sig =
        new ResolvedSignature(args.stream().map(IRNode::getType).toList(), resultShape);
    return Optional.of(new Operation("type", args, sig));
  }

  /**
   * Resolves {@code type()} on a choice type by creating variant Traversal nodes.
   *
   * <p>For singular parents, each variant contributes 4 args: [variantTraversal, namespace, name,
   * baseType]. The variant Traversal nodes use existing traversal codegen for correct column
   * resolution (handles Resource parents, lambda contexts, etc.). Uses operation name "typeChoice".
   *
   * <p>For plural parents (e.g., {@code component.value.type()}), variant Traversals would produce
   * arrays rather than per-element values. Instead, the parent node is passed directly with variant
   * column names as string literals, and the codegen uses {@code transform()} for element-wise
   * resolution. Uses operation name "typeChoicePlural".
   */
  @Nonnull
  private IRNode resolveChoiceTypeFunction(
      @Nonnull final ChoiceTypeLike choiceType,
      @Nonnull final IRNode targetIr,
      @Nonnull final Shape resultShape) {
    // Skip the choice traversal to get the parent node (same pattern as is/as/ofType)
    final IRNode parentNode =
        (targetIr instanceof Traversal choiceTraversal) ? choiceTraversal.target() : targetIr;

    final List<FieldSpec> variants = choiceType.getVariants();
    final var argsBuilder = new ArrayList<IRNode>();

    if (parentNode.isSingular()) {
      // Singular: use pre-resolved variant Traversal columns
      for (final FieldSpec variant : variants) {
        final TypeInfoValue info = TypeInfoValue.fromType(variant.getType());
        argsBuilder.add(new Traversal(parentNode, variant));
        argsBuilder.add(new Literal(info.namespace(), Types.STRING));
        argsBuilder.add(new Literal(info.name(), Types.STRING));
        argsBuilder.add(new Literal(info.baseType(), Types.STRING));
      }
      final ResolvedSignature sig =
          new ResolvedSignature(argsBuilder.stream().map(IRNode::getType).toList(), resultShape);
      return new Operation("typeChoice", argsBuilder, sig);
    }

    // Plural: pass parent + variant info as string literals for element-wise transform
    argsBuilder.add(parentNode);
    for (final FieldSpec variant : variants) {
      final TypeInfoValue info = TypeInfoValue.fromType(variant.getType());
      argsBuilder.add(new Literal(variant.getName(), Types.STRING));
      argsBuilder.add(new Literal(info.namespace(), Types.STRING));
      argsBuilder.add(new Literal(info.name(), Types.STRING));
      argsBuilder.add(new Literal(info.baseType(), Types.STRING));
    }
    final ResolvedSignature sig =
        new ResolvedSignature(argsBuilder.stream().map(IRNode::getType).toList(), resultShape);
    return new Operation("typeChoicePlural", argsBuilder, sig);
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

    // For ofType with System types, resolve all FHIR variants that map to that System type.
    // E.g., System.String matches valueString, valueCode, valueId, etc.
    final List<FieldSpec> matchingVariants =
        typeSpec.toAllFhirVariantNames().stream()
            .map(choiceType::resolveVariant)
            .flatMap(Optional::stream)
            .toList();

    if (matchingVariants.isEmpty()) {
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

    // Multiple matching variants (e.g., System.String → valueString, valueCode, valueId):
    // coalesce them so that any non-null variant value is returned.
    if (matchingVariants.size() > 1 && "ofType".equals(operation)) {
      return resolveMultiVariantOfType(parentNode, matchingVariants, targetIr);
    }

    final Traversal variantTraversal = new Traversal(parentNode, matchingVariants.get(0));

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
   * Resolves ofType() when multiple FHIR variants match a System type on a choice element.
   *
   * <p>For singular parents: creates a "coalesce" operation across all variant columns. For plural
   * parents: creates a "coalesceFields" operation that does per-element coalescing with null
   * filtering (equivalent to {@code filter(transform(arr, x -> coalesce(x.f1, x.f2, ...)), y -> y
   * IS NOT NULL)}).
   */
  @Nonnull
  private IRNode resolveMultiVariantOfType(
      @Nonnull final IRNode parentNode,
      @Nonnull final List<FieldSpec> variants,
      @Nonnull final IRNode targetIr) {
    final Type resultType = variants.get(0).getType();

    if (parentNode.isSingular()) {
      // Singular parent: coalesce(parent.variant1, parent.variant2, ...)
      final List<IRNode> args =
          variants.stream().map(v -> (IRNode) new Traversal(parentNode, v)).toList();
      final Cardinality resultCard = targetIr.getCardinality();
      final ResolvedSignature sig =
          new ResolvedSignature(
              args.stream().map(IRNode::getType).toList(), Shape.of(resultType, resultCard));
      return new Operation("coalesce", args, sig);
    }

    // Plural parent: per-element coalesce via transform + filter
    // Args: [parentNode, lit("field1"), lit("field2"), ...]
    final List<IRNode> args =
        Stream.concat(
                Stream.of(parentNode),
                variants.stream().map(v -> (IRNode) new Literal(v.getName(), Types.STRING)))
            .toList();
    final ResolvedSignature sig =
        new ResolvedSignature(
            args.stream().map(IRNode::getType).toList(), Shape.of(resultType, Cardinality.MANY));
    return new Operation("coalesceFields", args, sig);
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

    // ResolvedReferenceType: dynamic type checking at runtime (the type is in the data)
    if (targetIr.getType() instanceof ResolvedReferenceType) {
      return resolveResolvedReferenceTypeOp(operation, targetIr, typeSpec);
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
      case "ofType" -> {
        if (!matches) {
          yield new Literal(null, Types.NULL);
        }
        // For plural collections, filter out null elements per FHIRPath spec §5.6.7 —
        // ofType()'s type filter necessarily excludes null items. Singular values pass through
        // since a null singleton already represents an empty collection.
        if (targetIr.getCardinality() != Cardinality.MANY) {
          yield targetIr;
        }
        final ResolvedSignature sig =
            new ResolvedSignature(List.of(targetIr.getType()), targetIr.getShape());
        yield new Operation("ofType", List.of(targetIr), sig);
      }
      default -> throw new IllegalStateException("Unexpected type operation: " + operation);
    };
  }

  /**
   * Resolves type operations ({@code is}, {@code as}, {@code ofType}) on resolved references.
   *
   * <p>Unlike static type checking, resolved references carry their type as a runtime string value.
   * Type operations are emitted as runtime comparison operations that compare the extracted type
   * string against the requested type name.
   */
  @Nonnull
  private IRNode resolveResolvedReferenceTypeOp(
      @Nonnull final String operation,
      @Nonnull final IRNode targetIr,
      @Nonnull final TypeSpecifier typeSpec) {
    // Only FHIR resource types make sense for resolve() type checking
    if (!typeSpec.isFhirType()) {
      return switch (operation) {
        case "is" -> {
          final ResolvedSignature sig =
              new ResolvedSignature(
                  List.of(targetIr.getType(), Types.BOOLEAN), Shape.single(Types.BOOLEAN));
          yield new Operation("is", List.of(targetIr, new Literal(false, Types.BOOLEAN)), sig);
        }
        case "as", "ofType" -> new Literal(null, Types.NULL);
        default -> throw new IllegalStateException("Unexpected type operation: " + operation);
      };
    }

    final String requestedType = typeSpec.getTypeName();
    final Literal typeNameLiteral = new Literal(requestedType, Types.STRING);

    return switch (operation) {
      case "is" -> {
        // Runtime: CASE WHEN typeString IS NOT NULL THEN typeString = 'RequestedType' ELSE NULL END
        final ResolvedSignature sig =
            new ResolvedSignature(
                List.of(targetIr.getType(), Types.STRING), Shape.single(Types.BOOLEAN));
        yield new Operation("is", List.of(targetIr, typeNameLiteral), sig);
      }
      case "as" -> {
        // Runtime: CASE WHEN typeString = 'RequestedType' THEN typeString ELSE NULL END
        final ResolvedSignature sig =
            new ResolvedSignature(
                List.of(targetIr.getType(), Types.STRING),
                Shape.single(ResolvedReferenceType.INSTANCE));
        yield new Operation("as", List.of(targetIr, typeNameLiteral), sig);
      }
      case "ofType" -> {
        // Runtime: filter array keeping only elements where typeString = 'RequestedType'
        final Shape resultShape =
            Shape.of(ResolvedReferenceType.INSTANCE, targetIr.getShape().cardinality());
        final ResolvedSignature sig =
            new ResolvedSignature(List.of(targetIr.getType(), Types.STRING), resultShape);
        yield new Operation("ofType", List.of(targetIr, typeNameLiteral), sig);
      }
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

  /**
   * Resolves {@code resolve()} — extracts type information from Reference elements.
   *
   * <p>Must be called on a Reference element with no arguments. Returns a {@link
   * ResolvedReferenceType} that supports {@code is}/{@code as}/{@code ofType} for dynamic type
   * checking but does not support field traversal.
   */
  @Nonnull
  private IRNode resolveResolveFunction(
      @Nonnull final AstFunctionCall resolvedCall, @Nonnull final IRNode targetIr) {
    if (!resolvedCall.arguments().isEmpty()) {
      throw new InvalidExpressionException("resolve() takes no arguments", null);
    }
    if (!"Reference".equals(targetIr.getType().getName())) {
      throw new InvalidExpressionException(
          "resolve() can only be called on Reference elements, got: "
              + targetIr.getType().getName(),
          null);
    }
    final Shape resultShape =
        Shape.of(ResolvedReferenceType.INSTANCE, targetIr.getShape().cardinality());
    final ResolvedSignature sig = new ResolvedSignature(List.of(targetIr.getType()), resultShape);
    return new Operation("resolve", List.of(targetIr), sig);
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
