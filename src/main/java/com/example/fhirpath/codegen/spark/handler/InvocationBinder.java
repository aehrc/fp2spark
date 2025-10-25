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
 * Finds @Operation annotated methods on handler instances and invokes them with
 * appropriate type conversions:
 * <ul>
 *   <li>Column to domain wrappers (Collection, Quantity, LambdaExpression)</li>
 *   <li>Domain wrappers back to Column</li>
 * </ul>
 * <p>
 * Currently supports direct Column-to-Column invocation for boolean operators.
 * Future: Add boxing/unboxing for Collection, Quantity, LambdaExpression.
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
     * Boxes Column arguments to domain wrapper types if needed.
     * <p>
     * Currently supports:
     * <ul>
     *   <li>Column -> Column (pass through for boolean operators)</li>
     * </ul>
     * <p>
     * Future extensions:
     * <ul>
     *   <li>Column -> Collection (for collection operations)</li>
     *   <li>Column -> Quantity (for quantity operations)</li>
     *   <li>IR Lambda -> LambdaExpression (for higher-order functions)</li>
     * </ul>
     *
     * @param method The handler method
     * @param columnArgs The Column arguments
     * @param irArgs The IR nodes (for type information)
     * @return Boxed arguments ready for invocation
     */
    @Nonnull
    private static Object[] boxArguments(
            @Nonnull final Method method,
            @Nonnull final List<Column> columnArgs,
            @Nonnull final List<IRNode> irArgs) {

        final Class<?>[] paramTypes = method.getParameterTypes();
        final Object[] boxedArgs = new Object[paramTypes.length];

        for (int i = 0; i < paramTypes.length; i++) {
            final Class<?> paramType = paramTypes[i];
            final Column columnArg = columnArgs.get(i);

            // For now, all parameters are Column type (boolean operators)
            // Future: Check paramType and box accordingly
            // - If paramType == Collection.class -> new Collection(columnArg, isSingular)
            // - If paramType == Quantity.class -> new Quantity(columnArg)
            // - If paramType == LambdaExpression.class -> box IR Lambda
            boxedArgs[i] = columnArg;
        }

        return boxedArgs;
    }

    /**
     * Unboxes the result from a handler method.
     * <p>
     * Currently supports:
     * <ul>
     *   <li>Column -> Column (pass through for boolean operators)</li>
     * </ul>
     * <p>
     * Future extensions:
     * <ul>
     *   <li>Collection -> Column (call toColumn())</li>
     *   <li>Quantity -> Column (call toColumn())</li>
     * </ul>
     *
     * @param result The result from handler method
     * @return The result Column
     */
    @Nonnull
    private static Column unboxResult(@Nonnull final Object result) {
        // For now, all results are Column type (boolean operators)
        // Future: Check result type and unbox accordingly
        // - If result instanceof Collection -> ((Collection) result).toColumn()
        // - If result instanceof Quantity -> ((Quantity) result).toColumn()

        if (result instanceof Column column) {
            return column;
        }

        throw new IllegalStateException(
                "Unexpected result type from handler method: " + result.getClass().getSimpleName());
    }
}
