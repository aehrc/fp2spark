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

    // Factory function: (operation, dispatchType, context) -> Handler
    private final Map<String, TriFunction<String, Type, CodeGenContext, AnnotatedOperationHandler>> operationHandlers;

    private HandlerRegistry() {
        this.operationHandlers = new HashMap<>();
    }

    /**
     * Creates the standard handler registry with all built-in operations.
     *
     * @return A registry with all standard FHIRPath operation handlers
     */
    @Nonnull
    public static HandlerRegistry standard() {
        final HandlerRegistry registry = new HandlerRegistry();

        // Register boolean operation handler for all boolean operations
        registry.registerOperationHandler("and", BooleanOperationHandler::new);
        registry.registerOperationHandler("or", BooleanOperationHandler::new);
        registry.registerOperationHandler("xor", BooleanOperationHandler::new);
        registry.registerOperationHandler("implies", BooleanOperationHandler::new);
        registry.registerOperationHandler("not", BooleanOperationHandler::new);

        return registry;
    }

    /**
     * Registers an operation-based handler factory.
     *
     * @param operation The operation name
     * @param factory Factory function to create handler instances
     */
    private void registerOperationHandler(
            @Nonnull final String operation,
            @Nonnull final TriFunction<String, Type, CodeGenContext, AnnotatedOperationHandler> factory) {
        operationHandlers.put(operation, factory);
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
