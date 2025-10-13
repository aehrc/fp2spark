package com.example.fhirpath.ir;

import com.example.fhirpath.analyzer.FunctionSignature;
import com.example.fhirpath.typing.Type;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static com.example.fhirpath.analyzer.FunctionSignature.*;
import static com.example.fhirpath.typing.Type.*;

/**
 * Centralized registry of all FHIRPath functions and operators.
 *
 * Each entry maps a function/operator name to its list of supported signatures.
 * The OverloadResolver uses these signatures to find the best match for given arguments.
 *
 * Adding a new FHIRPath function requires only adding an entry here -
 * no new classes needed.
 */
public final class OperationRegistry {

    private static final Map<String, List<FunctionSignature>> OPERATIONS = new HashMap<>();

    static {
        // Arithmetic operators
        // FHIRPath Spec: 6.2.1 - 6.2.4
        registerBinaryOp("add", INTEGER, DECIMAL, QUANTITY, STRING);
        OPERATIONS.put("add", appendSignature(
            OPERATIONS.get("add"),
            biOperatorLeft(DATE_TIME, QUANTITY) // DateTime + Quantity → DateTime
        ));

        registerBinaryOp("sub", INTEGER, DECIMAL, QUANTITY);
        OPERATIONS.put("sub", appendSignature(
            OPERATIONS.get("sub"),
            biOperatorLeft(DATE_TIME, QUANTITY) // DateTime - Quantity → DateTime
        ));

        registerBinaryOp("multiply", INTEGER, DECIMAL, QUANTITY);
        registerBinaryOp("divide", INTEGER, DECIMAL, QUANTITY);
        registerBinaryOp("mod", INTEGER, DECIMAL);

        // Comparison operators
        // FHIRPath Spec: 6.3.1 - 6.3.6
        registerComparison("gt", INTEGER, DECIMAL, STRING, QUANTITY,
                          DATE, DATE_TIME, TIME);
        registerComparison("lt", INTEGER, DECIMAL, STRING, QUANTITY,
                          DATE, DATE_TIME, TIME);
        registerComparison("geq", INTEGER, DECIMAL, STRING, QUANTITY,
                          DATE, DATE_TIME, TIME);
        registerComparison("leq", INTEGER, DECIMAL, STRING, QUANTITY,
                          DATE, DATE_TIME, TIME);

        // Math functions
        // FHIRPath Spec: 6.4.1 - 6.4.5
        registerUnaryOp("abs", INTEGER, DECIMAL, QUANTITY);
        registerUnaryOp("ceiling", INTEGER, DECIMAL);
        registerUnaryOp("floor", INTEGER, DECIMAL);
        registerUnaryOp("truncate", INTEGER, DECIMAL);
        registerUnaryOp("exp", INTEGER, DECIMAL);
        registerUnaryOp("ln", INTEGER, DECIMAL);
        registerUnaryOp("log", INTEGER, DECIMAL);
        registerUnaryOp("sqrt", INTEGER, DECIMAL);

        // String functions
        // FHIRPath Spec: 6.5.1 - 6.5.10
        register("substring", List.of(
            // substring(string, start) and substring(string, start, length)
            new FunctionSignature(List.of(STRING, INTEGER, INTEGER), STRING, 2)
        ));

        register("startsWith", List.of(
            new FunctionSignature(List.of(STRING, STRING), BOOLEAN)
        ));

        register("endsWith", List.of(
            new FunctionSignature(List.of(STRING, STRING), BOOLEAN)
        ));

        register("contains", List.of(
            new FunctionSignature(List.of(STRING, STRING), BOOLEAN)
        ));

        register("upper", List.of(
            new FunctionSignature(List.of(STRING), STRING)
        ));

        register("lower", List.of(
            new FunctionSignature(List.of(STRING), STRING)
        ));

        register("replace", List.of(
            new FunctionSignature(List.of(STRING, STRING, STRING), STRING)
        ));

        register("matches", List.of(
            new FunctionSignature(List.of(STRING, STRING), BOOLEAN)
        ));

        register("length", List.of(
            new FunctionSignature(List.of(STRING), INTEGER)
        ));

        // Boolean operators
        // FHIRPath Spec: 6.8.1 - 6.8.4
        register("and", List.of(biOperator(BOOLEAN)));
        register("or", List.of(biOperator(BOOLEAN)));
        register("xor", List.of(biOperator(BOOLEAN)));
        register("implies", List.of(biOperator(BOOLEAN)));
        register("not", List.of(new FunctionSignature(List.of(BOOLEAN), BOOLEAN)));
    }

    private OperationRegistry() {
        // Utility class - no instantiation
    }

    /**
     * Returns all signatures for the given function/operator name.
     * Returns empty list if operation is not registered.
     */
    @Nonnull
    public static List<FunctionSignature> getSignatures(@Nonnull String name) {
        return OPERATIONS.getOrDefault(name, List.of());
    }

    /**
     * Checks if an operation is registered.
     */
    public static boolean isRegistered(@Nonnull String name) {
        return OPERATIONS.containsKey(name);
    }

    /**
     * Helper: Register a binary operation with same input/output type.
     */
    private static void registerBinaryOp(String name, Type... types) {
        register(name, Stream.of(types)
            .map(FunctionSignature::biOperator)
            .toList());
    }

    /**
     * Helper: Register a unary operation with same input/output type.
     */
    private static void registerUnaryOp(String name, Type... types) {
        register(name, Stream.of(types)
            .map(t -> new FunctionSignature(List.of(t), t))
            .toList());
    }

    /**
     * Helper: Register comparison operations (input types → BOOLEAN).
     */
    private static void registerComparison(String name, Type... types) {
        register(name, Stream.of(types)
            .map(t -> biOperator(t, BOOLEAN))
            .toList());
    }

    /**
     * Core registration method.
     */
    private static void register(String name, List<FunctionSignature> signatures) {
        if (OPERATIONS.containsKey(name)) {
            throw new IllegalStateException("Operation already registered: " + name);
        }
        OPERATIONS.put(name, signatures);
    }

    /**
     * Helper: Append a signature to existing list (for special cases).
     */
    private static List<FunctionSignature> appendSignature(
            List<FunctionSignature> existing,
            FunctionSignature additional) {
        return Stream.concat(existing.stream(), Stream.of(additional)).toList();
    }
}
