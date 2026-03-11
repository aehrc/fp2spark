package com.example.fhirpath.codegen.spark;

import com.example.fhirpath.codegen.spark.ops.*;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.apache.spark.sql.Column;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BinaryOperator;
import java.util.function.UnaryOperator;

/**
 * Registry mapping operation names to their Spark code generation functions.
 *
 * <p>Provides convenience methods for common patterns (binary, unary) and
 * full-control registration for complex cases. All operations are pure functions.
 */
public final class SparkOperationRegistry {

    private final Map<String, SparkOperationDef> operations = new HashMap<>();

    /**
     * Register a simple binary operation (two Column args, ignores type/generator).
     */
    public void binary(@Nonnull String name, @Nonnull BinaryOperator<Column> fn) {
        operations.put(name, (args, argNodes, resultType, gen) ->
                fn.apply(args.get(0), args.get(1)));
    }

    /**
     * Register a simple unary operation (one Column arg, ignores type/generator).
     */
    public void unary(@Nonnull String name, @Nonnull UnaryOperator<Column> fn) {
        operations.put(name, (args, argNodes, resultType, gen) ->
                fn.apply(args.get(0)));
    }

    /**
     * Register an operation with full control over arguments, types, and generator.
     */
    public void register(@Nonnull String name, @Nonnull SparkOperationDef def) {
        operations.put(name, def);
    }

    /**
     * Look up an operation by name.
     *
     * @return the operation definition, or null if not registered
     */
    @Nullable
    public SparkOperationDef get(@Nonnull String name) {
        return operations.get(name);
    }

    /**
     * Creates the standard registry with all built-in operations.
     */
    @Nonnull
    public static SparkOperationRegistry standard() {
        SparkOperationRegistry registry = new SparkOperationRegistry();
        BooleanOps.register(registry);
        ArithmeticOps.register(registry);
        ComparisonOps.register(registry);
        CollectionOps.register(registry);
        FilteringOps.register(registry);
        return registry;
    }
}
