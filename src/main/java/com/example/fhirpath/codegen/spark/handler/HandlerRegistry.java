package com.example.fhirpath.codegen.spark.handler;

import com.example.fhirpath.typing.Type;
import com.example.fhirpath.typing.Types;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Registry for operation handlers.
 * <p>
 * Stores factory functions that create handler instances for specific operations.
 * Handlers are created per invocation as stateful command objects.
 * <p>
 * Registry organization:
 * <ul>
 *   <li>Operation-based handlers - for heavily overloaded operations (comparison, arithmetic)</li>
 *   <li>Type-based handlers - for type-specific operations (string ops, quantity ops)</li>
 *   <li>Generic handlers - for polymorphic operations (collection ops)</li>
 * </ul>
 */
public final class HandlerRegistry {

    @Nonnull
    private final Map<String, TriFunction<String, Type, CodeGenContext, AnnotatedOperationHandler>> operationHandlers;

    private HandlerRegistry(
            @Nonnull final Map<String, TriFunction<String, Type, CodeGenContext, AnnotatedOperationHandler>> handlers) {
        this.operationHandlers = Map.copyOf(handlers);
    }

    /**
     * Creates the standard handler registry with all built-in operations.
     *
     * @return a registry with all standard FHIRPath operation handlers
     */
    @Nonnull
    public static HandlerRegistry standard() {
        final Map<String, TriFunction<String, Type, CodeGenContext, AnnotatedOperationHandler>> handlers =
                new HashMap<>();

        handlers.put("and", BooleanOperationHandler::new);
        handlers.put("or", BooleanOperationHandler::new);
        handlers.put("xor", BooleanOperationHandler::new);
        handlers.put("implies", BooleanOperationHandler::new);
        handlers.put("not", BooleanOperationHandler::new);

        return new HandlerRegistry(handlers);
    }

    /**
     * Creates a handler for the specified operation and dispatch type.
     *
     * @param operation The operation name
     * @param dispatchType The type determining handler selection
     * @param context The code generation context
     * @return A handler instance, or null if no handler registered for this operation
     */
    @Nullable
    public AnnotatedOperationHandler createHandler(
            @Nonnull final String operation,
            @Nonnull final Type dispatchType,
            @Nonnull final CodeGenContext context) {

        // Try operation-based handlers first
        final TriFunction<String, Type, CodeGenContext, AnnotatedOperationHandler> factory =
                operationHandlers.get(operation);

        if (factory != null) {
            return factory.apply(operation, dispatchType, context);
        }

        // No handler found
        return null;
    }

    /**
     * Checks if a handler is registered for the given operation.
     *
     * @param operation The operation name
     * @return true if a handler exists for this operation
     */
    public boolean hasHandler(@Nonnull final String operation) {
        return operationHandlers.containsKey(operation);
    }
}
