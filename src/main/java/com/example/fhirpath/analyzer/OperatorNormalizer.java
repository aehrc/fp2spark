package com.example.fhirpath.analyzer;

import jakarta.annotation.Nonnull;

import java.util.Map;

/**
 * Utility for normalizing operator symbols to their canonical function names.
 *
 * <p>FHIRPath supports both symbolic operators (+, -, *, /, etc.) and named functions
 * (add, subtract, multiply, divide, etc.). This class provides a centralized mapping
 * from operator symbols to their canonical names used in the operation registry.
 *
 * <p>Example mappings:
 * <ul>
 *   <li>{@code "+"} → {@code "add"}</li>
 *   <li>{@code ">"} → {@code "gt"}</li>
 *   <li>{@code "="} → {@code "equals"}</li>
 *   <li>{@code "|"} → {@code "union"}</li>
 * </ul>
 */
public final class OperatorNormalizer {

    /**
     * Mapping of operator symbols to canonical names.
     * Immutable map defined once at class load time.
     */
    private static final Map<String, String> CANONICAL_NAMES = Map.ofEntries(
            Map.entry(">", "gt"),
            Map.entry("<", "lt"),
            Map.entry(">=", "geq"),
            Map.entry("<=", "leq"),
            Map.entry("+", "add"),
            Map.entry("-", "sub"),
            Map.entry("*", "multiply"),
            Map.entry("/", "divide"),
            Map.entry("%", "mod"),
            Map.entry("=", "equals"),
            Map.entry("|", "union")
    );

    private OperatorNormalizer() {
        // Utility class - no instantiation
    }

    /**
     * Normalizes an operator symbol or function name to its canonical name.
     * If the input is already a canonical name, returns it unchanged.
     *
     * @param operatorSymbol The operator symbol or function name
     * @return The canonical function name
     */
    @Nonnull
    public static String normalize(@Nonnull final String operatorSymbol) {
        return CANONICAL_NAMES.getOrDefault(operatorSymbol, operatorSymbol);
    }

    /**
     * Checks if the given string is an operator symbol (vs. a function name).
     *
     * @param symbol The string to check
     * @return true if this is an operator symbol, false if it's a function name
     */
    public static boolean isOperatorSymbol(@Nonnull final String symbol) {
        return CANONICAL_NAMES.containsKey(symbol);
    }
}
