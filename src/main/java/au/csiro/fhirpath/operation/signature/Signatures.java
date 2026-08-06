package au.csiro.fhirpath.operation.signature;

import static au.csiro.fhirpath.operation.signature.ParamSpec.many;
import static au.csiro.fhirpath.operation.signature.ParamSpec.single;

import au.csiro.fhirpath.typing.Cardinality;
import au.csiro.fhirpath.typing.LambdaType;
import au.csiro.fhirpath.typing.Shape;
import au.csiro.fhirpath.typing.Type;
import au.csiro.fhirpath.typing.Types;
import jakarta.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Signature factory methods with explicit cardinality.
 *
 * <p>Each factory builds a {@link SignatureDefinition} from concrete {@link ParamSpec} and {@link
 * ResultTypeSpec} entries. Polymorphism is currently expressed by enumeration over {@code
 * TypeGroup}s rather than by type variables — see #260 for the deferred refactor.
 */
public final class Signatures {
  private Signatures() {
    throw new AssertionError("No instances");
  }

  /**
   * Unary function: ?T → ?R.
   *
   * <p>Example: length(?STRING) → ?INTEGER
   */
  @Nonnull
  public static SignatureDefinition unaryFunc(
      @Nonnull final Type paramType, @Nonnull final Type resultType) {
    return new SignatureDefinition(List.of(single(paramType)), ResultTypeSpec.single(resultType));
  }

  /**
   * Unary operation where input type = result type: ?T → ?T.
   *
   * <p>Example: abs(?INTEGER) → ?INTEGER
   */
  @Nonnull
  public static SignatureDefinition unaryOp(@Nonnull final Type type) {
    return unaryFunc(type, type);
  }

  /**
   * Binary function: (?T1, ?T2) → ?R.
   *
   * <p>Example: startsWith(?STRING, ?STRING) → ?BOOLEAN
   */
  @Nonnull
  public static SignatureDefinition binaryFunc(
      @Nonnull final Type leftType, @Nonnull final Type rightType, @Nonnull final Type resultType) {
    return new SignatureDefinition(
        List.of(single(leftType), single(rightType)), ResultTypeSpec.single(resultType));
  }

  /**
   * Binary operation where all types are the same: (?T, ?T) → ?T.
   *
   * <p>Example: add(?INTEGER, ?INTEGER) → ?INTEGER
   */
  @Nonnull
  public static SignatureDefinition binaryOp(@Nonnull final Type type) {
    return binaryFunc(type, type, type);
  }

  /**
   * Division operation where result is always DECIMAL: (?T, ?T) → ?DECIMAL.
   *
   * <p>Per FHIRPath spec, the `/` operator always returns a Decimal value, even when both operands
   * are Integer.
   */
  @Nonnull
  public static SignatureDefinition divisionOp(@Nonnull final Type type) {
    return binaryFunc(type, type, Types.DECIMAL);
  }

  /**
   * Temporal arithmetic operation: (?Temporal, ?Quantity) → ?Temporal.
   *
   * <p>Example: ?DATE + ?QUANTITY → ?DATE
   */
  @Nonnull
  public static SignatureDefinition temporalArithmetic(@Nonnull final Type temporalType) {
    return binaryFunc(temporalType, Types.QUANTITY, temporalType);
  }

  /**
   * Ternary function: (?T1, ?T2, ?T3) → ?R.
   *
   * <p>Example: replace(?STRING, ?STRING, ?STRING) → ?STRING
   */
  @Nonnull
  public static SignatureDefinition ternaryFunc(
      @Nonnull final Type firstType,
      @Nonnull final Type secondType,
      @Nonnull final Type thirdType,
      @Nonnull final Type resultType) {
    return new SignatureDefinition(
        List.of(single(firstType), single(secondType), single(thirdType)),
        ResultTypeSpec.single(resultType));
  }

  /**
   * Comparison operation: (?T, ?T) → ?BOOLEAN.
   *
   * <p>Example: gt(?INTEGER, ?INTEGER) → ?BOOLEAN
   */
  @Nonnull
  public static SignatureDefinition comparisonOp(@Nonnull final Type type) {
    return binaryFunc(type, type, Types.BOOLEAN);
  }

  /**
   * Membership "in" operation: (?T, *T) → ?BOOLEAN.
   *
   * <p>Element (singular) in collection (many). Returns true if the element is found in the
   * collection using equality semantics.
   *
   * <p>Example: 1 in (1 ; 2 ; 3) → true
   */
  @Nonnull
  public static SignatureDefinition membershipIn(@Nonnull final Type type) {
    return new SignatureDefinition(
        List.of(single(type), many(type)), ResultTypeSpec.single(Types.BOOLEAN));
  }

  /**
   * Membership "contains" operation: (*T, ?T) → ?BOOLEAN.
   *
   * <p>Collection (many) contains element (singular). Returns true if the collection contains the
   * element using equality semantics. Converse of {@link #membershipIn}.
   *
   * <p>Example: (1 ; 2 ; 3) contains 1 → true
   */
  @Nonnull
  public static SignatureDefinition membershipContains(@Nonnull final Type type) {
    return new SignatureDefinition(
        List.of(many(type), single(type)), ResultTypeSpec.single(Types.BOOLEAN));
  }

  /**
   * Equality operation: (*T, *T) → ?BOOLEAN.
   *
   * <p>Uses {@code many()} params to accept both singular and collection operands. Equality
   * compares entire collections when either operand is plural.
   *
   * <p>Example: equals(*INTEGER, *INTEGER) → ?BOOLEAN
   */
  @Nonnull
  public static SignatureDefinition equalityOp(@Nonnull final Type type) {
    return new SignatureDefinition(
        List.of(many(type), many(type)), ResultTypeSpec.single(Types.BOOLEAN));
  }

  /**
   * Element extractor from collection: *T → ?T Example: *T.first() → ?T
   *
   * <p>Uses dynamic result type resolution ({@link ResultTypeSpec#effectiveInputType}) to extract
   * the element type from the input collection. With type variables (#260) this could be expressed
   * declaratively as {@code (*T) → ?T}.
   */
  @Nonnull
  public static SignatureDefinition elementExtractor(@Nonnull final Type elementType) {
    return new SignatureDefinition(
        List.of(many(elementType)),
        ResultTypeSpec.effectiveInputType(
            Cardinality.SINGLE) // Dynamic: extract element type with SINGLE cardinality
        );
  }

  /**
   * Indexer: (*T, ?INTEGER) → ?T. Extracts element at given index (0-based).
   *
   * <p>The first parameter accepts both MANY (*T) and SINGLE (?T) collections. A singular value
   * behaves as a one-element collection per the FHIRPath specification.
   *
   * <p>Examples:
   *
   * <ul>
   *   <li>{@code (1 ; 2 ; 3)[1] → 2}
   *   <li>{@code 5[0] → 5} (singular treated as one-element collection)
   *   <li>{@code (1 ; 2)[3] → {}} (out-of-bounds → empty)
   * </ul>
   */
  @Nonnull
  public static SignatureDefinition indexer(@Nonnull final Type elementType) {
    return new SignatureDefinition(
        List.of(many(elementType), single(Types.INTEGER)),
        ResultTypeSpec.effectiveInputType(Cardinality.SINGLE));
  }

  /**
   * Collection preserver: (*T, ...) → *T. Always produces MANY cardinality regardless of input.
   *
   * <p>Use this for operations that inherently produce collections (e.g., {@code split}, {@code
   * toChars}). For operations that should preserve the input's cardinality (the {@code α}
   * variable), use {@link #collectionSubsetter} instead.
   */
  @Nonnull
  public static SignatureDefinition collectionPreserver(
      @Nonnull final Type elementType, @Nonnull final ParamSpec... additionalParams) {
    return collectionOperation(ResultTypeSpec.many(elementType), elementType, additionalParams);
  }

  /**
   * Collection subsetter: (α T, ...) → α T. Returns a sub-collection preserving element type and
   * input cardinality.
   *
   * <p>Uses dynamic result type resolution to preserve both the actual element type and cardinality
   * from the input collection (the {@code α} variable in TYPE_SYSTEM.md).
   *
   * <p>Examples: α T.tail() → α T, α T.skip(?INTEGER) → α T
   */
  @Nonnull
  public static SignatureDefinition collectionSubsetter(
      @Nonnull final Type elementType, @Nonnull final ParamSpec... additionalParams) {
    return collectionOperation(ResultTypeSpec.effectiveInputType(), elementType, additionalParams);
  }

  /** Common builder for collection operations that take *T input and optional extra params. */
  @Nonnull
  private static SignatureDefinition collectionOperation(
      @Nonnull final ResultTypeSpec resultSpec,
      @Nonnull final Type elementType,
      @Nonnull final ParamSpec... additionalParams) {
    final List<ParamSpec> params = new ArrayList<>();
    params.add(many(elementType));
    params.addAll(Arrays.asList(additionalParams));
    return new SignatureDefinition(params, resultSpec);
  }

  /**
   * Collection aggregator: *T → ?R. Reduces collection to single value.
   *
   * <p>Example: *T.count() → ?INTEGER
   */
  @Nonnull
  public static SignatureDefinition collectionAggregator(
      @Nonnull final Type inputType, @Nonnull final Type resultType) {
    return new SignatureDefinition(List.of(many(inputType)), ResultTypeSpec.single(resultType));
  }

  /**
   * Variadic operation with optional parameters.
   *
   * <p>Example: substring(?STRING, ?INTEGER, ?INTEGER) with minArity=2
   */
  @Nonnull
  public static SignatureDefinition variadic(
      @Nonnull final List<ParamSpec> parameters,
      @Nonnull final ResultTypeSpec resultSpec,
      final int minArity) {
    return new SignatureDefinition(parameters, resultSpec, minArity);
  }

  /**
   * Type test operation: (?T, ?STRING) → ?BOOLEAN.
   *
   * <p>Example: is(?T, ?STRING) → ?BOOLEAN
   */
  @Nonnull
  public static SignatureDefinition typeTest(@Nonnull final Type inputType) {
    return new SignatureDefinition(
        List.of(single(inputType), single(Types.STRING)), ResultTypeSpec.single(Types.BOOLEAN));
  }

  /**
   * Collection projection with lambda: (*T, ?Lambda(S)) → *S.
   *
   * <p>Example: *T.select(?Lambda(S)) → *S
   *
   * <p>Uses ELEMENT_WISE binding: $this = T (element type). The result type is determined by the
   * lambda body's element type via {@link ResultTypeSpec#lambdaBodyTypeMany(int)}, which extracts
   * the type from the lambda body and forces MANY cardinality.
   *
   * <p>If the lambda returns MANY, the results are flattened (FHIRPath collections are
   * one-dimensional). If the lambda returns SINGLE, results are collected into an array.
   *
   * @param elementType the element type of the input collection
   * @return a signature definition for collection-projection operations
   */
  @Nonnull
  public static SignatureDefinition collectionProjection(@Nonnull final Type elementType) {
    // LambdaType uses Shape.single(elementType) as a placeholder; the actual return
    // type/cardinality is extracted dynamically at resolution time via
    // ResultTypeSpec.lambdaBodyTypeMany(). #260 would let this be expressed declaratively.
    final LambdaType lambdaType = new LambdaType(Shape.single(elementType));

    return new SignatureDefinition(
        List.of(many(elementType), single(lambdaType)),
        ResultTypeSpec.lambdaBodyTypeMany(1), // Dynamic: extract type from lambda body, always MANY
        2, // minArity
        LambdaBindingStrategy.ELEMENT_WISE // $this = element
        );
  }

  /**
   * Collection filter with lambda: (*T, ?Lambda(?BOOLEAN)) → *T Example:
   * *T.where(?Lambda(?BOOLEAN)) → *T
   *
   * <p>Uses ELEMENT_WISE binding: $this = T (element type)
   *
   * <p>Uses dynamic result type resolution ({@link ResultTypeSpec#inputType()}) to preserve the
   * input element type and cardinality (the α variable in the type-system notation).
   */
  @Nonnull
  public static SignatureDefinition collectionFilter(@Nonnull final Type elementType) {
    // Lambda expects single BOOLEAN result
    final LambdaType lambdaType = new LambdaType(Shape.single(Types.BOOLEAN));

    // Use dynamic result resolution to preserve input type and cardinality (α variable)
    return new SignatureDefinition(
        List.of(many(elementType), single(lambdaType)),
        ResultTypeSpec.inputType(), // Dynamic: preserve input type and cardinality
        2, // minArity
        LambdaBindingStrategy.ELEMENT_WISE // $this = element
        );
  }

  /**
   * Collection test with lambda: (*T, ?Lambda(?BOOLEAN)) → ?BOOLEAN.
   *
   * <p>Example: *T.all(?Lambda(?BOOLEAN)) → ?BOOLEAN
   *
   * <p>Uses ELEMENT_WISE binding: $this = T (element type). Like {@link #collectionFilter} but
   * returns a single BOOLEAN instead of preserving the collection.
   *
   * @param elementType the element type of the input collection
   * @return a signature definition for collection-test operations
   */
  @Nonnull
  public static SignatureDefinition collectionTest(@Nonnull final Type elementType) {
    final LambdaType lambdaType = new LambdaType(Shape.single(Types.BOOLEAN));
    return new SignatureDefinition(
        List.of(many(elementType), single(lambdaType)),
        ResultTypeSpec.single(Types.BOOLEAN),
        2, // minArity
        LambdaBindingStrategy.ELEMENT_WISE // $this = element
        );
  }

  /**
   * Diagnostic pass-through with an optional projection lambda: (α T, ?STRING [, ?Lambda(*ANY)]) →
   * α T.
   *
   * <p>Example: {@code α T.trace(?STRING [, ?Lambda(*ANY)]) → α T}
   *
   * <p>Used by {@code trace()} (FHIRPath §5.9.1). The result type and cardinality are preserved
   * exactly via {@link ResultTypeSpec#inputType()}. The optional projection is a {@code
   * COLLECTION_WISE} lambda ({@code $this} = the input collection), matching the spec's "evaluating
   * the projection expression on the input"; its body may return any type.
   *
   * <p>The diagnostic side channel that gives {@code trace()} its purpose is not implemented:
   * fp2sql compiles to SQL and has no evaluation context to carry a diagnostic sink. The projection
   * is still type-checked for static validity, then discarded by code generation. See #277.
   */
  @Nonnull
  public static SignatureDefinition diagnosticPassThrough(@Nonnull final Type elementType) {
    return new SignatureDefinition(
        List.of(
            many(elementType), single(Types.STRING), single(new LambdaType(Shape.many(Types.ANY)))),
        ResultTypeSpec.inputType(),
        2, // minArity — the projection is optional
        LambdaBindingStrategy.COLLECTION_WISE);
  }

  /**
   * Union operation: (*T, *T) → *T with dynamic result type.
   *
   * <p>Used by both union ({@code |}) and combine ({@code ;}) operators. The result type is
   * resolved dynamically from the first argument's type, preserving MANY cardinality.
   *
   * <p>Both sides must currently resolve to the same element type (overloads in {@code
   * OperationRegistry} cover the common cases via {@code forTypes(EQUATABLE)} plus NULL and ANY
   * tiers). #260 would let mixed-type unions resolve via LUB.
   */
  @Nonnull
  public static SignatureDefinition union(@Nonnull final Type elementType) {
    return new SignatureDefinition(
        List.of(many(elementType), many(elementType)), ResultTypeSpec.inputType(Cardinality.MANY));
  }

  /**
   * Conditional iif operation: (*T, ?Lambda(?BOOLEAN), ?Lambda(S)) → S
   *
   * <p>Uses COLLECTION_WISE binding: $this = *T (entire collection)
   *
   * <p>Uses dynamic result shape resolution ({@link ResultTypeSpec#lambdaBodyType(int)}) to extract
   * the result shape (type + cardinality) from the "then" lambda's body.
   *
   * <p>The result shape S is whatever the lambda body returns:
   *
   * <ul>
   *   <li>If lambda returns ?R (single), result is ?R
   *   <li>If lambda returns *R (many), result is *R
   * </ul>
   *
   * <p>For the 3-arg form (with else-branch), the result must instead be {@code LUB(then, else)} —
   * see #186 (3-arg iif implementation) and #260 (broader type-variable refactor).
   */
  @Nonnull
  public static SignatureDefinition conditionalIif(
      @Nonnull final Type inputType, @Nonnull final Type resultType) {
    return new SignatureDefinition(
        List.of(
            many(inputType),
            single(new LambdaType(Shape.single(Types.BOOLEAN))),
            single(new LambdaType(Shape.single(resultType)))),
        // Dynamic: extract result shape from lambda at argument index 2 (the "then" lambda)
        ResultTypeSpec.lambdaBodyType(2),
        3,
        LambdaBindingStrategy.COLLECTION_WISE);
  }
}
