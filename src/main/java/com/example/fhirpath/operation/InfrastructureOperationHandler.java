package com.example.fhirpath.operation;

import com.example.fhirpath.analyzer.UnsupportedFeatureException;

import com.example.fhirpath.ast.AstFunctionCall;
import com.example.fhirpath.ir.Combine;
import com.example.fhirpath.ir.Equals;
import com.example.fhirpath.ir.IRNode;
import jakarta.annotation.Nonnull;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Handles special infrastructure operations that don't follow the standard
 * signature-based resolution pattern.
 *
 * <p>These operations are handled separately because they:
 * <ul>
 *   <li>Don't require overload resolution (single implementation)</li>
 *   <li>Directly construct specific IR node types</li>
 *   <li>Have special semantics not captured by standard signatures</li>
 * </ul>
 *
 * <p>Supported operations:
 * <ul>
 *   <li>{@code equals()} - Equality comparison</li>
 *   <li>{@code combine} / {@code ;} - Ordered collection concatenation</li>
 * </ul>
 */
public final class InfrastructureOperationHandler {

    private InfrastructureOperationHandler() {
        // Utility class - no instantiation
    }

    /**
     * Checks if the given function name is an infrastructure operation.
     *
     * @param functionName The function/operator name to check
     * @return true if this is an infrastructure operation
     */
    public static boolean isInfrastructureOperation(@Nonnull final String functionName) {
        return switch (functionName) {
            case "equals", "combine", ";" -> true;
            default -> false;
        };
    }

    /**
     * Handles infrastructure operations by analyzing arguments and constructing
     * appropriate IR nodes.
     *
     * @param call The function call AST node
     * @param targetIR The analyzed target expression
     * @param argumentAnalyzer Function to analyze additional arguments
     * @return The constructed IR node
     * @throws UnsupportedFeatureException if the function is not recognized
     */
    @Nonnull
    public static IRNode handle(
            @Nonnull final AstFunctionCall call,
            @Nonnull final IRNode targetIR,
            @Nonnull final Function<com.example.fhirpath.ast.AstNode, IRNode> argumentAnalyzer
    ) {
        // Analyze all arguments (target + call arguments)
        final List<IRNode> args = Stream.concat(
                Stream.of(targetIR),
                call.arguments().stream().map(argumentAnalyzer)
        ).toList();

        return switch (call.functionName()) {
            case "equals" -> new Equals(args.get(0), args.get(1));
            case "combine", ";" -> new Combine(args.get(0), args.get(1));
            default -> throw new UnsupportedFeatureException(
                    "Function '" + call.functionName() + "'",
                    null
            );
        };
    }
}
