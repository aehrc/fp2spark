package com.example.fhirpath.ir;

import com.example.fhirpath.analyzer.SignatureDefinition;
import com.example.fhirpath.analyzer.Signatures;
import com.example.fhirpath.analyzer.TypeGroup;
import jakarta.annotation.Nonnull;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static com.example.fhirpath.analyzer.TypeGroups.forTypes;
import static com.example.fhirpath.analyzer.TypeSets.*;
import static com.example.fhirpath.typing.Type.*;

/**
 * Centralized registry of all FHIRPath functions and operators.
 * <p>
 * Uses Option 5 design with TypeGroup for zero-overhead single signatures
 * and elegant composition of multi-type patterns.
 * <p>
 * Each entry maps a function/operator name to its list of supported signatures.
 * The OverloadResolver uses these signatures to find the best match for given arguments.
 */
public final class OperationRegistry {

    private static final Map<String, List<SignatureDefinition>> OPERATIONS = buildRegistry();

    private OperationRegistry() {
        // Singleton - no instantiation
    }

    private static Map<String, List<SignatureDefinition>> buildRegistry() {
        return Map.ofEntries(

            // ARITHMETIC OPERATORS (FHIRPath Spec 6.2)

            // Addition: numeric types, strings, and temporal + Quantity
            // FHIRPath Spec 3740-3809: Date/DateTime/Time + Quantity
            register("add",
                forTypes(NUMERIC_WITH_QUANTITY, STRING_LIKE).define(Signatures::binaryOp),
                forTypes(TEMPORAL).define(Signatures::temporalArithmetic)
            ),

            // Subtraction: numeric types and temporal - Quantity
            // FHIRPath Spec 3810-3867: Date/DateTime/Time - Quantity
            register("sub",
                forTypes(NUMERIC_WITH_QUANTITY).define(Signatures::binaryOp),
                forTypes(TEMPORAL).define(Signatures::temporalArithmetic)
            ),

            register("multiply",
                forTypes(NUMERIC_WITH_QUANTITY).define(Signatures::binaryOp)
            ),

            register("divide",
                forTypes(NUMERIC_WITH_QUANTITY).define(Signatures::binaryOp)
            ),

            register("mod",
                forTypes(NUMERIC).define(Signatures::binaryOp)
            ),

            // COMPARISON OPERATORS (FHIRPath Spec 6.3)

            register("gt",
                forTypes(COMPARABLE).define(Signatures::comparisonOp)
            ),

            register("lt",
                forTypes(COMPARABLE).define(Signatures::comparisonOp)
            ),

            register("geq",
                forTypes(COMPARABLE).define(Signatures::comparisonOp)
            ),

            register("leq",
                forTypes(COMPARABLE).define(Signatures::comparisonOp)
            ),

            // MATH FUNCTIONS (FHIRPath Spec 6.4)

            register("abs",
                forTypes(NUMERIC_WITH_QUANTITY).define(Signatures::unaryOp)
            ),

            register("ceiling",
                forTypes(NUMERIC).define(Signatures::unaryOp)
            ),

            register("floor",
                forTypes(NUMERIC).define(Signatures::unaryOp)
            ),

            register("truncate",
                forTypes(NUMERIC).define(Signatures::unaryOp)
            ),

            register("exp",
                forTypes(NUMERIC).define(Signatures::unaryOp)
            ),

            register("ln",
                forTypes(NUMERIC).define(Signatures::unaryOp)
            ),

            register("log",
                forTypes(NUMERIC).define(Signatures::unaryOp)
            ),

            register("sqrt",
                forTypes(NUMERIC).define(Signatures::unaryOp)
            ),

            // STRING FUNCTIONS (FHIRPath Spec 6.5)

            // substring(string, start) and substring(string, start, length)
            register("substring",
                Signatures.variadic(List.of(STRING, INTEGER, INTEGER), STRING, 2)
            ),

            register("startsWith",
                Signatures.binaryFunc(STRING, STRING, BOOLEAN)
            ),

            register("endsWith",
                Signatures.binaryFunc(STRING, STRING, BOOLEAN)
            ),

            register("contains",
                Signatures.binaryFunc(STRING, STRING, BOOLEAN)
            ),

            register("upper",
                Signatures.unaryOp(STRING)  // T -> T pattern
            ),

            register("lower",
                Signatures.unaryOp(STRING)  // T -> T pattern
            ),

            register("replace",
                Signatures.ternaryFunc(STRING, STRING, STRING, STRING)
            ),

            register("matches",
                Signatures.binaryFunc(STRING, STRING, BOOLEAN)
            ),

            register("length",
                Signatures.unaryFunc(STRING, INTEGER)
            ),

            // BOOLEAN OPERATORS (FHIRPath Spec 6.8)

            register("and",
                Signatures.binaryOp(BOOLEAN)  // (T, T) -> T pattern
            ),

            register("or",
                Signatures.binaryOp(BOOLEAN)  // (T, T) -> T pattern
            ),

            register("xor",
                Signatures.binaryOp(BOOLEAN)  // (T, T) -> T pattern
            ),

            register("implies",
                Signatures.binaryOp(BOOLEAN)
            ),

            register("not",
                Signatures.unaryOp(BOOLEAN)  // T -> T pattern
            ),

            // COLLECTION FUNCTIONS (FHIRPath Spec 6.6)

            // first() returns the first element from a collection
            // Collection<T> → T (extracts element type)
            register("first",
                Signatures.elementExtractor(ANY)
            ),

            // count() returns the number of items in the collection
            register("count",
                Signatures.collectionAggregator(ANY, INTEGER)
            ),

            // exists() returns true if the collection is not empty
            register("exists",
                Signatures.collectionAggregator(ANY, BOOLEAN)
            ),

            // empty() returns true if the collection is empty
            register("empty",
                Signatures.collectionAggregator(ANY, BOOLEAN)
            )
        );
    }

    /**
     * Varargs register with flatMap for natural composition.
     * Accepts any number of TypeGroups (including SignatureDefinitions directly).
     */
    @Nonnull
    private static Map.Entry<String, List<SignatureDefinition>> register(
            @Nonnull final String name,
            @Nonnull final TypeGroup... groups) {
        final List<SignatureDefinition> signatures = Stream.of(groups)
            .flatMap(TypeGroup::expand)
            .toList();
        return Map.entry(name, signatures);
    }

    /**
     * Returns all signatures for the given function/operator name.
     * Returns empty list if operation is not registered.
     */
    @Nonnull
    public static List<SignatureDefinition> getSignatures(@Nonnull final String name) {
        return OPERATIONS.getOrDefault(name, List.of());
    }

}
