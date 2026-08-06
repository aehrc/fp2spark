package com.example.fhirpath.operation;

import static com.example.fhirpath.operation.signature.ParamSpec.many;
import static com.example.fhirpath.operation.signature.ParamSpec.single;
import static com.example.fhirpath.operation.signature.TypeGroups.forTypes;
import static com.example.fhirpath.typing.TypeSets.COMPARABLE;
import static com.example.fhirpath.typing.TypeSets.EQUATABLE;
import static com.example.fhirpath.typing.TypeSets.NUMERIC;
import static com.example.fhirpath.typing.TypeSets.STRING_LIKE;
import static com.example.fhirpath.typing.Types.ANY;
import static com.example.fhirpath.typing.Types.BOOLEAN;
import static com.example.fhirpath.typing.Types.DATE;
import static com.example.fhirpath.typing.Types.DATE_TIME;
import static com.example.fhirpath.typing.Types.DECIMAL;
import static com.example.fhirpath.typing.Types.INTEGER;
import static com.example.fhirpath.typing.Types.NULL;
import static com.example.fhirpath.typing.Types.QUANTITY;
import static com.example.fhirpath.typing.Types.STRING;
import static com.example.fhirpath.typing.Types.TIME;

import com.example.fhirpath.operation.signature.ResultTypeSpec;
import com.example.fhirpath.operation.signature.SignatureDefinition;
import com.example.fhirpath.operation.signature.Signatures;
import com.example.fhirpath.operation.signature.TypeGroup;
import jakarta.annotation.Nonnull;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Centralized registry of all FHIRPath functions and operators.
 *
 * <p>Uses Option 5 design with TypeGroup for zero-overhead single signatures and elegant
 * composition of multi-type patterns.
 *
 * <p>Each entry maps a function/operator name to its list of supported signatures. The
 * OverloadResolver uses these signatures to find the best match for given arguments.
 */
public final class OperationRegistry {

  private static final Map<String, List<SignatureDefinition>> OPERATIONS = buildRegistry();

  private OperationRegistry() {
    // Singleton - no instantiation
  }

  private static Map<String, List<SignatureDefinition>> buildRegistry() {
    return Map.ofEntries(

        // ARITHMETIC OPERATORS (FHIRPath Spec 6.2)
        // Numeric (INTEGER, DECIMAL), STRING, QUANTITY, and temporal types.

        register(
            "add",
            forTypes(NUMERIC, STRING_LIKE).define(Signatures::binaryOp),
            Signatures.binaryOp(QUANTITY),
            Signatures.temporalArithmetic(DATE),
            Signatures.temporalArithmetic(DATE_TIME),
            Signatures.temporalArithmetic(TIME)),
        register(
            "sub",
            forTypes(NUMERIC).define(Signatures::binaryOp),
            Signatures.binaryOp(QUANTITY),
            Signatures.temporalArithmetic(DATE),
            Signatures.temporalArithmetic(DATE_TIME),
            Signatures.temporalArithmetic(TIME)),
        register(
            "multiply",
            forTypes(NUMERIC).define(Signatures::binaryOp),
            Signatures.binaryOp(QUANTITY)),
        // Quantity division returns Quantity (unit algebra: cm2 / cm → cm),
        // unlike numeric division which always returns DECIMAL.
        register(
            "divide",
            forTypes(NUMERIC).define(Signatures::divisionOp),
            Signatures.binaryOp(QUANTITY)),
        register("mod", forTypes(NUMERIC).define(Signatures::binaryOp)),
        register("div", forTypes(NUMERIC).define(Signatures::binaryOp)),
        register("stringConcat", Signatures.binaryOp(STRING)),
        register(
            "unaryPlus",
            forTypes(NUMERIC).define(Signatures::unaryOp),
            Signatures.unaryOp(QUANTITY)),
        register(
            "unaryMinus",
            forTypes(NUMERIC).define(Signatures::unaryOp),
            Signatures.unaryOp(QUANTITY)),

        // EQUALITY OPERATORS (FHIRPath Spec 6.4)
        // Three tiers for equality overload resolution:
        // 1. forTypes(EQUATABLE): matched for compatible concrete types (INTEGER=INTEGER, etc.)
        // 2. equalityOp(NULL): matched when either operand is empty (NULL type) — returns {}
        // 3. equalityOp(ANY): fallback for incompatible types (e.g., INTEGER=STRING) — false/true

        register(
            "equals",
            forTypes(EQUATABLE).define(Signatures::equalityOp),
            Signatures.equalityOp(NULL),
            Signatures.equalityOp(ANY)),
        register(
            "notEquals",
            forTypes(EQUATABLE).define(Signatures::equalityOp),
            Signatures.equalityOp(NULL),
            Signatures.equalityOp(ANY)),

        // MEMBERSHIP OPERATORS (FHIRPath Spec 6.5)
        // Three tiers matching the equality pattern:
        // 1. forTypes(EQUATABLE): matched for compatible concrete types
        // 2. membershipIn/Contains(NULL): when element or collection is empty (NULL type)
        //    - codegen returns {} for empty element, false for empty collection
        // 3. membershipIn/Contains(ANY): fallback for incompatible types → false

        register(
            "in",
            forTypes(EQUATABLE).define(Signatures::membershipIn),
            Signatures.membershipIn(NULL),
            Signatures.membershipIn(ANY)),
        register(
            "memberContains",
            forTypes(EQUATABLE).define(Signatures::membershipContains),
            Signatures.membershipContains(NULL),
            Signatures.membershipContains(ANY)),

        // COMPARISON OPERATORS (FHIRPath Spec 6.3)

        register("gt", forTypes(COMPARABLE).define(Signatures::comparisonOp)),
        register("lt", forTypes(COMPARABLE).define(Signatures::comparisonOp)),
        register("geq", forTypes(COMPARABLE).define(Signatures::comparisonOp)),
        register("leq", forTypes(COMPARABLE).define(Signatures::comparisonOp)),

        // BOOLEAN OPERATORS (FHIRPath Spec 6.8)

        register(
            "and", Signatures.binaryOp(BOOLEAN) // (T, T) -> T pattern
            ),
        register(
            "or", Signatures.binaryOp(BOOLEAN) // (T, T) -> T pattern
            ),
        register(
            "xor", Signatures.binaryOp(BOOLEAN) // (T, T) -> T pattern
            ),
        register("implies", Signatures.binaryOp(BOOLEAN)),
        register(
            "not", Signatures.unaryOp(BOOLEAN) // T -> T pattern
            ),

        // COLLECTION FUNCTIONS (FHIRPath Spec 6.6 — filtering/projection)

        // first() returns the first element from a collection
        // Collection<T> → T (extracts element type)
        register("first", Signatures.elementExtractor(ANY)),

        // last() returns the last element from a collection
        // Collection<T> → T (extracts element type)
        register("last", Signatures.elementExtractor(ANY)),

        // tail() returns all but the first element
        // α T → α T (preserves element type and input cardinality)
        register("tail", Signatures.collectionSubsetter(ANY)),

        // skip(num) skips first num elements
        // (α T, ?INTEGER) → α T
        register("skip", Signatures.collectionSubsetter(ANY, single(INTEGER))),

        // take(num) takes first num elements
        // (α T, ?INTEGER) → α T
        register("take", Signatures.collectionSubsetter(ANY, single(INTEGER))),

        // single() returns the value if exactly one element, empty otherwise
        // Collection<T> → T (extracts element type)
        register("single", Signatures.elementExtractor(ANY)),

        // indexer ([]) returns the element at the given index (0-based)
        // (*T, ?INTEGER) → ?T (singular inputs treated as one-element collection)
        register("indexer", Signatures.indexer(ANY)),

        // count() returns the number of items in the collection
        register("count", Signatures.collectionAggregator(ANY, INTEGER)),

        // exists() returns true if the collection is not empty
        register("exists", Signatures.collectionAggregator(ANY, BOOLEAN)),

        // empty() returns true if the collection is empty
        register("empty", Signatures.collectionAggregator(ANY, BOOLEAN)),

        // BOOLEAN COLLECTION FUNCTIONS (FHIRPath Spec 5.6.1)

        register("allTrue", Signatures.collectionAggregator(BOOLEAN, BOOLEAN)),
        register("anyTrue", Signatures.collectionAggregator(BOOLEAN, BOOLEAN)),
        register("allFalse", Signatures.collectionAggregator(BOOLEAN, BOOLEAN)),
        register("anyFalse", Signatures.collectionAggregator(BOOLEAN, BOOLEAN)),
        register("all", Signatures.collectionTest(ANY)),

        // FILTERING AND PROJECTION (FHIRPath Spec 5.2.5)

        // where() filters a collection based on lambda criteria
        // Collection<T>.where(Lambda(T, Boolean)) → Collection<T>
        register("where", Signatures.collectionFilter(ANY)),

        // select() projects/maps collection elements through a lambda expression
        // Collection<T>.select(Lambda(T, S)) → Collection<S>
        register("select", Signatures.collectionProjection(ANY)),

        // COMBINE AND UNION OPERATORS (FHIRPath Spec 6.6)
        // Three tiers for overload resolution:
        // 1. forTypes(EQUATABLE): matched for compatible concrete types with coercion
        // 2. union(NULL): matched when either operand is empty (NULL type)
        // 3. union(ANY): fallback for complex types; code generator validates compatibility

        register(
            "combine",
            forTypes(EQUATABLE).define(Signatures::union),
            Signatures.union(NULL),
            Signatures.union(ANY)),
        register(
            "union",
            forTypes(EQUATABLE).define(Signatures::union),
            Signatures.union(NULL),
            Signatures.union(ANY)),

        // SET OPERATIONS (FHIRPath Spec 5.6.3 / 5.6.4)

        // distinct(): α T → α T (removes duplicates, preserves input cardinality)
        register("distinct", Signatures.collectionSubsetter(ANY)),

        // isDistinct(): *T → ?BOOLEAN (true if all items distinct; empty → true)
        register("isDistinct", Signatures.collectionAggregator(ANY, BOOLEAN)),

        // intersect(other): (*T, *T) → *T (elements in both; duplicates eliminated)
        register(
            "intersect",
            forTypes(EQUATABLE).define(Signatures::union),
            Signatures.union(NULL),
            Signatures.union(ANY)),

        // exclude(other): (*T, *T) → *T (elements NOT in other)
        register(
            "exclude",
            forTypes(EQUATABLE).define(Signatures::union),
            Signatures.union(NULL),
            Signatures.union(ANY)),

        // subsetOf(other): (*T, *T) → ?BOOLEAN (all input items in other)
        // Reuses equalityOp shape (*T, *T) → ?BOOLEAN; actual subset semantics in SetOps
        register(
            "subsetOf",
            forTypes(EQUATABLE).define(Signatures::equalityOp),
            Signatures.equalityOp(NULL),
            Signatures.equalityOp(ANY)),

        // supersetOf(other): (*T, *T) → ?BOOLEAN (all other items in input)
        // Reuses equalityOp shape (*T, *T) → ?BOOLEAN; actual superset semantics in SetOps
        register(
            "supersetOf",
            forTypes(EQUATABLE).define(Signatures::equalityOp),
            Signatures.equalityOp(NULL),
            Signatures.equalityOp(ANY)),

        // FHIR-SPECIFIC FUNCTIONS

        register("getValue", Signatures.unaryFunc(ANY, ANY)),
        register("hasValue", Signatures.unaryFunc(ANY, BOOLEAN)),

        // TERMINOLOGY FUNCTIONS (FHIR FHIRPath)

        // memberOf(valueSet): (?Coding | ?CodeableConcept, ?STRING) → ?BOOLEAN
        // The input is declared ANY because CodeableConcept is a FhirComplexType with no static
        // Type constant; TerminologyOps rejects other input types during code generation instead
        // of here at analysis time — a known gap against ARCHITECTURE.md Principle 5 (Static
        // Validity), not solved by #260's type-variable work since that explicitly excludes
        // predicate-based constraints. See #284. Singular input is enforced here, matching the
        // spec's "more than one value" rule under D1.
        register("memberOf", Signatures.binaryFunc(ANY, STRING, BOOLEAN)),

        // TYPE TESTING (FHIRPath Spec 6.1)
        // Note: is/as/ofType are intercepted early by Analyzer.resolveTypeOperation() before
        // normal signature resolution. This registration exists so that the "unknown function"
        // guard in resolveFunctionCall() does not reject the operation name.

        register("is", Signatures.typeTest(ANY)),

        // type() is intercepted early by Analyzer.resolveTypeFunction() before normal signature
        // resolution. This registration exists so that the "unknown function" guard in
        // resolveFunctionCall() does not reject the operation name.
        register("type", Signatures.unaryFunc(ANY, ANY)),

        // CONDITIONAL OPERATIONS (FHIRPath Spec 6.7)

        // iif() evaluates collection-level conditional with lambda parameters
        // Collection<T>.iif(Lambda<Boolean>, Lambda<Collection<R>>) → Collection<R>
        // Both lambdas operate on entire collection (COLLECTION_WISE binding)
        // Generic types use ANY here; type variables would let this be expressed as
        // (*T, ?Lambda(?BOOLEAN), ?Lambda(S)) → S — see #260.
        register("iif", Signatures.conditionalIif(ANY, ANY)),

        // ANALYZER-INTERCEPTED FUNCTIONS
        // These registrations are never reached by OverloadResolver — the Analyzer intercept
        // fires first. They exist so that the "unknown function" guard in resolveFunctionCall()
        // does not reject the operation name. Spark code generation is registered in FhirOps.
        register("getResourceKey", Signatures.unaryFunc(ANY, STRING)),
        register("getReferenceKey", Signatures.unaryFunc(ANY, STRING)),
        register("resolve", Signatures.unaryFunc(ANY, ANY)),

        // STRING FUNCTIONS (FHIRPath Spec 5.7)

        register("length", Signatures.unaryFunc(STRING, INTEGER)),
        register("upper", Signatures.unaryOp(STRING)),
        register("lower", Signatures.unaryOp(STRING)),
        register("trim", Signatures.unaryOp(STRING)),
        register("startsWith", Signatures.binaryFunc(STRING, STRING, BOOLEAN)),
        register("endsWith", Signatures.binaryFunc(STRING, STRING, BOOLEAN)),
        // String contains() function (not the 'contains' membership operator, which is
        // normalized to 'memberContains' by OperatorNormalizer)
        register("contains", Signatures.binaryFunc(STRING, STRING, BOOLEAN)),
        register("indexOf", Signatures.binaryFunc(STRING, STRING, INTEGER)),
        // substring(start, [length]) — variadic, minArity=2
        register(
            "substring",
            Signatures.variadic(
                List.of(single(STRING), single(INTEGER), single(INTEGER)),
                ResultTypeSpec.single(STRING),
                2)),
        register("replace", Signatures.ternaryFunc(STRING, STRING, STRING, STRING)),
        register("matches", Signatures.binaryFunc(STRING, STRING, BOOLEAN)),
        register("replaceMatches", Signatures.ternaryFunc(STRING, STRING, STRING, STRING)),
        // split(separator) → *STRING
        register("split", Signatures.collectionPreserver(STRING, single(STRING))),
        // join([separator]) → ?STRING — input is *STRING
        register(
            "join",
            Signatures.variadic(
                List.of(many(STRING), single(STRING)), ResultTypeSpec.single(STRING), 1)),
        // toChars() → *STRING
        register("toChars", Signatures.collectionPreserver(STRING)),

        // TYPE CONVERSION FUNCTIONS (FHIRPath Spec 5.7.1)
        // Each: ?ANY → ?TargetType (returns empty on failure)

        register("toBoolean", Signatures.unaryFunc(ANY, BOOLEAN)),
        register("toInteger", Signatures.unaryFunc(ANY, INTEGER)),
        register("toDecimal", Signatures.unaryFunc(ANY, DECIMAL)),
        register("toString", Signatures.unaryFunc(ANY, STRING)),
        register("toDate", Signatures.unaryFunc(ANY, DATE)),
        register("toDateTime", Signatures.unaryFunc(ANY, DATE_TIME)),
        register("toTime", Signatures.unaryFunc(ANY, TIME)),
        // toQuantity([unit: String]) — optional unit arg for UCUM unit conversion
        register(
            "toQuantity",
            Signatures.variadic(
                List.of(single(ANY), single(STRING)), ResultTypeSpec.single(QUANTITY), 1)),

        // CONVERSION VALIDATION FUNCTIONS (FHIRPath Spec 5.7.2)
        // Each: ?ANY → ?BOOLEAN

        register("convertsToBoolean", Signatures.unaryFunc(ANY, BOOLEAN)),
        register("convertsToInteger", Signatures.unaryFunc(ANY, BOOLEAN)),
        register("convertsToDecimal", Signatures.unaryFunc(ANY, BOOLEAN)),
        register("convertsToString", Signatures.unaryFunc(ANY, BOOLEAN)),
        register("convertsToDate", Signatures.unaryFunc(ANY, BOOLEAN)),
        register("convertsToDateTime", Signatures.unaryFunc(ANY, BOOLEAN)),
        register("convertsToTime", Signatures.unaryFunc(ANY, BOOLEAN)),
        // convertsToQuantity([unit: String]) — optional unit arg for UCUM unit conversion
        register(
            "convertsToQuantity",
            Signatures.variadic(
                List.of(single(ANY), single(STRING)), ResultTypeSpec.single(BOOLEAN), 1)),

        // UTILITY FUNCTIONS (FHIRPath Spec 5.9)

        // trace(name [, projection]): α T → α T — returns the input collection unaltered.
        // The projection is a COLLECTION_WISE lambda ($this = the input collection) so that it
        // is evaluated against the input, per the spec. Diagnostic output is not emitted: fp2sql
        // has no evaluation context to carry a diagnostic sink — see #277.
        register("trace", Signatures.diagnosticPassThrough(ANY)),

        // MATH FUNCTIONS (FHIRPath Spec 5.7.3)

        // abs(): preserves input type (INTEGER → INTEGER, DECIMAL → DECIMAL, QUANTITY → QUANTITY)
        register(
            "abs", forTypes(NUMERIC).define(Signatures::unaryOp), Signatures.unaryOp(QUANTITY)),
        // ceiling(), floor(), truncate(): always return INTEGER
        register("ceiling", forTypes(NUMERIC).define(t -> Signatures.unaryFunc(t, INTEGER))),
        register("floor", forTypes(NUMERIC).define(t -> Signatures.unaryFunc(t, INTEGER))),
        register("truncate", forTypes(NUMERIC).define(t -> Signatures.unaryFunc(t, INTEGER))),
        // round([precision]): always returns DECIMAL
        register(
            "round",
            Signatures.variadic(
                List.of(single(INTEGER), single(INTEGER)), ResultTypeSpec.single(DECIMAL), 1),
            Signatures.variadic(
                List.of(single(DECIMAL), single(INTEGER)), ResultTypeSpec.single(DECIMAL), 1)),
        // exp(), ln(), sqrt(): always return DECIMAL
        register("exp", forTypes(NUMERIC).define(t -> Signatures.unaryFunc(t, DECIMAL))),
        register("ln", forTypes(NUMERIC).define(t -> Signatures.unaryFunc(t, DECIMAL))),
        register("sqrt", forTypes(NUMERIC).define(t -> Signatures.unaryFunc(t, DECIMAL))),
        // log(base): always returns DECIMAL.
        // Explicit (INTEGER, INTEGER) avoids a Cast node for the common 16.log(2) case.
        // (DECIMAL, INTEGER) is covered by INTEGER→DECIMAL widening into (DECIMAL, DECIMAL).
        register(
            "log",
            forTypes(NUMERIC).define(t -> Signatures.binaryFunc(t, DECIMAL, DECIMAL)),
            Signatures.binaryFunc(INTEGER, INTEGER, DECIMAL)),
        // power(exponent): INTEGER^INTEGER → INTEGER, otherwise DECIMAL
        register(
            "power",
            Signatures.binaryOp(INTEGER),
            Signatures.binaryFunc(DECIMAL, DECIMAL, DECIMAL),
            Signatures.binaryFunc(INTEGER, DECIMAL, DECIMAL),
            Signatures.binaryFunc(DECIMAL, INTEGER, DECIMAL)));
  }

  /**
   * Varargs register with flatMap for natural composition. Accepts any number of TypeGroups
   * (including SignatureDefinitions directly).
   */
  @Nonnull
  private static Map.Entry<String, List<SignatureDefinition>> register(
      @Nonnull final String name, @Nonnull final TypeGroup... groups) {
    final List<SignatureDefinition> signatures =
        Stream.of(groups).flatMap(TypeGroup::expand).toList();
    return Map.entry(name, signatures);
  }

  /**
   * Returns all signatures for the given function/operator name. Returns empty list if operation is
   * not registered.
   */
  @Nonnull
  public static List<SignatureDefinition> getSignatures(@Nonnull final String name) {
    return OPERATIONS.getOrDefault(name, List.of());
  }
}
