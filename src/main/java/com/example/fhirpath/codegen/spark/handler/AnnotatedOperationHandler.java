package com.example.fhirpath.codegen.spark.handler;

import com.example.fhirpath.typing.Type;
import jakarta.annotation.Nonnull;

/**
 * Base class for all operation handlers using annotation-based dispatch.
 * <p>
 * Handlers are stateful command objects created per invocation with:
 * <ul>
 *   <li>operation - The operation name</li>
 *   <li>dispatchType - Type used for handler selection</li>
 *   <li>context - Code generation context</li>
 * </ul>
 * <p>
 * Subclasses define methods annotated with @Operation that perform code generation.
 */
public abstract class AnnotatedOperationHandler {

    protected final String operation;
    protected final Type dispatchType;
    protected final CodeGenContext context;

    /**
     * Creates a handler for a specific operation invocation.
     *
     * @param operation The operation name
     * @param dispatchType The type determining this handler's selection
     * @param context The code generation context
     */
    protected AnnotatedOperationHandler(
            @Nonnull final String operation,
            @Nonnull final Type dispatchType,
            @Nonnull final CodeGenContext context) {
        this.operation = operation;
        this.dispatchType = dispatchType;
        this.context = context;
    }
}
