package com.example.fhirpath.operation;

import com.example.fhirpath.analyzer.AnalysisException;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * Exception thrown when an operator is not supported or not yet implemented.
 *
 * <p>This occurs when the parser recognizes an operator symbol but the analyzer
 * doesn't have a registered implementation for it.
 *
 * <p>Example: Using a future FHIRPath operator that hasn't been implemented yet.
 */
public class UnsupportedOperatorException extends AnalysisException {
    @Nonnull
    private final String operatorSymbol;

    /**
     * Constructs an unsupported operator exception.
     *
     * @param operatorSymbol    the operator symbol that is not supported
     * @param expressionContext the FHIRPath expression that caused the error
     */
    public UnsupportedOperatorException(
            @Nonnull String operatorSymbol,
            @Nullable String expressionContext
    ) {
        super(formatMessage(operatorSymbol), expressionContext);
        this.operatorSymbol = operatorSymbol;
    }

    private static String formatMessage(String operatorSymbol) {
        return String.format(
                "Operator '%s' is not supported or not yet implemented",
                operatorSymbol
        );
    }

    @Nonnull
    public String getOperatorSymbol() {
        return operatorSymbol;
    }
}
