package com.example.fhirpath.codegen.spark.handler;

import com.example.fhirpath.ir.IRNode;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.apache.spark.sql.Column;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

/**
 * Binds handler method invocations using reflection and automatic boxing/unboxing.
 * <p>
 * Finds {@link Operation @Operation} annotated methods on handler instances and invokes them
 * with appropriate type conversions between Spark Column and domain wrapper types.
 */
public final class InvocationBinder {

    private InvocationBinder() {
        // Static utility class
    }

    /**
     * Invokes the handler method for the specified operation.
     *
     * @param handler The handler instance
     * @param operation The operation name
     * @param columnArgs The Column arguments
     * @param irArgs The corresponding IR nodes (for type information)
     * @return The result Column
     * @throws IllegalStateException if method not found or invocation fails
     */
    @Nonnull
    public static Column invoke(
            @Nonnull final AnnotatedOperationHandler handler,
            @Nonnull final String operation,
            @Nonnull final List<Column> columnArgs,
            @Nonnull final List<IRNode> irArgs) {

        // Find the @Operation annotated method
        final Method method = findOperationMethod(handler.getClass(), operation);
        if (method == null) {
            throw new IllegalStateException(
                    "No @Operation(\"" + operation + "\") method found on " + handler.getClass().getSimpleName());
        }

        // Box arguments if needed
        final Object[] boxedArgs = boxArguments(method, columnArgs, irArgs);

        // Invoke the method
        try {
            final Object result = method.invoke(handler, boxedArgs);

            // Unbox result if needed
            return unboxResult(result);

        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException(
                    "Failed to invoke " + method.getName() + " on " + handler.getClass().getSimpleName(), e);
        }
    }

    /**
     * Finds the method annotated with @Operation for the specified operation.
     *
     * @param handlerClass The handler class
     * @param operation The operation name
     * @return The method, or null if not found
     */
    @Nullable
    private static Method findOperationMethod(@Nonnull final Class<?> handlerClass, @Nonnull final String operation) {
        return Arrays.stream(handlerClass.getDeclaredMethods())
                .filter(m -> {
                    final Operation annotation = m.getAnnotation(Operation.class);
                    return annotation != null && annotation.value().equals(operation);
                })
                .findFirst()
                .orElse(null);
    }

    /**
     * Boxes Column arguments to domain wrapper types based on method parameter types.
     *
     * @param method the handler method
     * @param columnArgs the Column arguments
     * @param irArgs the IR nodes (for type information)
     * @return boxed arguments ready for invocation
     * @throws IllegalStateException if argument count does not match parameter count
     */
    @Nonnull
    private static Object[] boxArguments(
            @Nonnull final Method method,
            @Nonnull final List<Column> columnArgs,
            @Nonnull final List<IRNode> irArgs) {

        final Class<?>[] paramTypes = method.getParameterTypes();

        if (columnArgs.size() != paramTypes.length) {
            throw new IllegalStateException(String.format(
                    "Arity mismatch invoking %s: expected %d arguments, got %d",
                    method.getName(), paramTypes.length, columnArgs.size()));
        }

        final Object[] boxedArgs = new Object[paramTypes.length];

        for (int i = 0; i < paramTypes.length; i++) {
            boxedArgs[i] = columnArgs.get(i);
        }

        return boxedArgs;
    }

    /**
     * Unboxes the result from a handler method to a Spark Column.
     *
     * @param result the result from handler method
     * @return the result Column
     * @throws IllegalStateException if result is not a supported type
     */
    @Nonnull
    private static Column unboxResult(@Nonnull final Object result) {
        if (result instanceof Column column) {
            return column;
        }

        throw new IllegalStateException(
                "Unexpected result type from handler method: " + result.getClass().getSimpleName());
    }
}
