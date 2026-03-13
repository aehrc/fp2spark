package com.example.fhirpath.operation;

import static com.example.fhirpath.operation.signature.TypeGroups.forTypes;
import static com.example.fhirpath.typing.TypeSets.COMPARABLE;
import static com.example.fhirpath.typing.TypeSets.EQUATABLE;
import static com.example.fhirpath.typing.TypeSets.NUMERIC;
import static com.example.fhirpath.typing.TypeSets.STRING_LIKE;
import static com.example.fhirpath.typing.Types.ANY;
import static com.example.fhirpath.typing.Types.BOOLEAN;
import static com.example.fhirpath.typing.Types.INTEGER;
import static com.example.fhirpath.typing.Types.NULL;
import static com.example.fhirpath.typing.Types.STRING;

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
        // Phase 1: Only numeric types (INTEGER, DECIMAL) and STRING

        register("add", forTypes(NUMERIC, STRING_LIKE).define(Signatures::binaryOp)),
        register("sub", forTypes(NUMERIC).define(Signatures::binaryOp)),
        register("multiply", forTypes(NUMERIC).define(Signatures::binaryOp)),
        register("divide", forTypes(NUMERIC).define(Signatures::divisionOp)),
        register("mod", forTypes(NUMERIC).define(Signatures::binaryOp)),
        register("div", forTypes(NUMERIC).define(Signatures::binaryOp)),
        register("stringConcat", Signatures.binaryOp(STRING)),
        register("unaryPlus", forTypes(NUMERIC).define(Signatures::unaryOp)),
        register("unaryMinus", forTypes(NUMERIC).define(Signatures::unaryOp)),

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

        // COLLECTION FUNCTIONS (FHIRPath Spec 6.6)

        // first() returns the first element from a collection
        // Collection<T> → T (extracts element type)
        register("first", Signatures.elementExtractor(ANY)),

        // indexer ([]) returns the element at the given index (0-based)
        // (*T, ?INTEGER) → ?T (singular inputs treated as one-element collection)
        register("indexer", Signatures.indexer(ANY)),

        // count() returns the number of items in the collection
        register("count", Signatures.collectionAggregator(ANY, INTEGER)),

        // exists() returns true if the collection is not empty
        register("exists", Signatures.collectionAggregator(ANY, BOOLEAN)),

        // empty() returns true if the collection is empty
        register("empty", Signatures.collectionAggregator(ANY, BOOLEAN)),

        // FILTERING AND PROJECTION (FHIRPath Spec 5.2.5)

        // where() filters a collection based on lambda criteria
        // Collection<T>.where(Lambda(T, Boolean)) → Collection<T>
        register("where", Signatures.collectionFilter(ANY)),

        // CONDITIONAL OPERATIONS (FHIRPath Spec 6.7)

        // iif() evaluates collection-level conditional with lambda parameters
        // Collection<T>.iif(Lambda<Boolean>, Lambda<Collection<R>>) → Collection<R>
        // Both lambdas operate on entire collection (COLLECTION_WISE binding)
        // Phase 1: Use ANY for generic types (Phase 2 will add type variables)
        register("iif", Signatures.conditionalIif(ANY, ANY)));
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
