package com.example.fhirpath.analyzer;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * Base exception for errors detected during FHIRPath expression analysis.
 *
 * <p>Analysis exceptions are thrown when the type checker or analyzer
 * detects semantic errors that violate FHIRPath specification constraints,
 * such as cardinality mismatches or type incompatibilities.
 *
 * <p>These are compile-time errors in the sense that they are detected
 * during the analysis phase before code generation, not during execution.
 */
public class AnalysisException extends RuntimeException {
    @Nullable
    private final String expressionContext;

    /**
     * Constructs an analysis exception with a message and expression context.
     *
     * @param message          the error message
     * @param expressionContext the FHIRPath expression that caused the error
     */
    public AnalysisException(@Nonnull String message, @Nullable String expressionContext) {
        super(message);
        this.expressionContext = expressionContext;
    }

    /**
     * Constructs an analysis exception with a message, expression context, and cause.
     *
     * @param message          the error message
     * @param expressionContext the FHIRPath expression that caused the error
     * @param cause            the underlying cause
     */
    public AnalysisException(
            @Nonnull String message,
            @Nullable String expressionContext,
            @Nullable Throwable cause
    ) {
        super(message, cause);
        this.expressionContext = expressionContext;
    }

    /**
     * Returns the FHIRPath expression that caused this error, if available.
     */
    @Nullable
    public String getExpressionContext() {
        return expressionContext;
    }

    @Override
    public String getMessage() {
        String baseMessage = super.getMessage();
        if (expressionContext != null) {
            return baseMessage + "\n  Expression: " + expressionContext;
        }
        return baseMessage;
    }
}
