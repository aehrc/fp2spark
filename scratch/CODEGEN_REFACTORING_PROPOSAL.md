# Code Generator Refactoring Proposal

**Date**: 2025-01-22
**Status**: PROPOSAL
**Prerequisites**: Read `CODEGEN_ARCHITECTURE_ANALYSIS.md` first

---

## Table of Contents

1. [Vision](#vision)
2. [Proposed Architecture](#proposed-architecture)
3. [Core Abstractions](#core-abstractions)
4. [Implementation Examples](#implementation-examples)
5. [Migration Strategy](#migration-strategy)
6. [Benefits & Trade-offs](#benefits--trade-offs)

---

## Vision

**Goal**: Transform code generator from monolithic dispatcher to composable, extensible architecture.

### Current State (618-line switch-based dispatcher)
```
Operation "add" → evaluateAdd() → switch(type) → inline logic/wrapper stub
```

### Target State (registry-based strategy pattern)
```
Operation "add" → OperationImplementationRegistry
                   ↓
              TypeHandler (Quantity, DateTime, Integer...)
                   ↓
              ImplementationStrategy (Direct, Complex, UDF)
                   ↓
              Column generation
```

### Design Principles

1. **Open/Closed**: Add operations/types without modifying existing code
2. **Single Responsibility**: Each class has one clear purpose
3. **Dependency Inversion**: Depend on abstractions, not implementations
4. **Explicit Strategy**: Clear choice between direct mapping/complex expression/UDF

---

## Proposed Architecture

### Layer Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│ SparkCodeGenerator (Simplified)                                 │
│                                                                  │
│  visitOperation(op) {                                            │
│    registry.getImplementation(op.name(), op.getType())          │
│      .generate(args, context)                                    │
│  }                                                               │
└────────────────────────┬────────────────────────────────────────┘
                         │
                         ↓
┌─────────────────────────────────────────────────────────────────┐
│ OperationImplementationRegistry                                 │
│                                                                  │
│  Map<OperationKey, OperationImplementation>                     │
│    OperationKey = (operationName, typeGroup)                    │
│                                                                  │
│  getImplementation(name, type): OperationImplementation         │
│  register(key, impl): void                                       │
└────────────────────────┬────────────────────────────────────────┘
                         │
                         ↓
┌─────────────────────────────────────────────────────────────────┐
│ OperationImplementation (Strategy Interface)                   │
│                                                                  │
│  interface OperationImplementation {                            │
│    Column generate(List<Column> args, CodeGenContext ctx);     │
│  }                                                               │
│                                                                  │
│  Implementations:                                                │
│    - DirectMapping: upper(arg) → just call Spark function      │
│    - ComplexExpression: substring → multi-step logic           │
│    - UdfInvocation: quantityAdd → call registered UDF          │
│    - TypeHandlerDelegate: delegate to type-specific handler    │
└────────────────────────┬────────────────────────────────────────┘
                         │
                         ↓
┌─────────────────────────────────────────────────────────────────┐
│ TypeHandler (Per Complex Type)                                 │
│                                                                  │
│  interface TypeHandler {                                        │
│    Column handleOperation(String opName, List<Column> args,    │
│                           CodeGenContext ctx);                  │
│  }                                                               │
│                                                                  │
│  Implementations:                                                │
│    - QuantityHandler: arithmetic, comparison, abs              │
│    - DateTimeHandler: comparison, arithmetic with Quantity     │
│    - DateHandler: comparison                                    │
│    - TimeHandler: comparison                                    │
└─────────────────────────────────────────────────────────────────┘
```

### Component Responsibilities

| Component | Responsibility | Lines (Est.) |
|-----------|---------------|--------------|
| SparkCodeGenerator | IR traversal, coordination | ~200 |
| OperationImplementationRegistry | Map operations to implementations | ~100 |
| OperationImplementation (interface + 4 impls) | Define implementation strategies | ~150 |
| TypeHandler (interface + 4 impls) | Type-specific operation logic | ~400 |
| UdfRegistry | UDF registration and discovery | ~100 |
| **Total** | | **~950** |

**Current**: 618 lines (monolithic) + 200 lines (type wrappers) = 818 lines
**Proposed**: ~950 lines (modular, extensible)

---

## Core Abstractions

### 1. OperationImplementation (Strategy Interface)

```java
package com.example.fhirpath.codegen.spark.strategy;

import org.apache.spark.sql.Column;
import jakarta.annotation.Nonnull;
import java.util.List;

/**
 * Strategy for generating Spark Column expressions for an operation.
 *
 * Different strategies handle different implementation patterns:
 * - DirectMapping: 1:1 mapping to Spark function
 * - ComplexExpression: Multi-step inline logic
 * - UdfInvocation: Call user-defined function
 * - TypeHandlerDelegate: Delegate to type-specific handler
 */
@FunctionalInterface
public interface OperationImplementation {
    /**
     * Generate Spark Column for this operation.
     *
     * @param args Evaluated argument columns
     * @param context Code generation context (includes type info, UDF registry, etc.)
     * @return Generated column expression
     */
    @Nonnull
    Column generate(@Nonnull List<Column> args, @Nonnull CodeGenContext context);
}
```

### 2. CodeGenContext (Shared State)

```java
package com.example.fhirpath.codegen.spark;

import com.example.fhirpath.typing.Type;
import org.apache.spark.sql.Column;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * Context passed to operation implementations during code generation.
 *
 * Provides access to:
 * - Type information from IR
 * - UDF registry
 * - Current $this binding (for lambdas)
 * - Result type from resolved signature
 */
public record CodeGenContext(
    @Nonnull Type resultType,
    @Nonnull List<Type> argTypes,
    @Nullable Column thisColumn,
    @Nonnull UdfRegistry udfRegistry,
    @Nonnull SparkCodeGenerator codeGenerator  // For recursive IR evaluation
) {
    /**
     * Create context with $this binding for lambda evaluation.
     */
    public CodeGenContext withThisColumn(@Nonnull Column thisColumn) {
        return new CodeGenContext(resultType, argTypes, thisColumn,
                                  udfRegistry, codeGenerator);
    }
}
```

### 3. Strategy Implementations

#### DirectMapping (Simple 1:1 Spark Functions)

```java
package com.example.fhirpath.codegen.spark.strategy;

import org.apache.spark.sql.Column;
import jakarta.annotation.Nonnull;
import java.util.List;
import java.util.function.Function;

/**
 * Strategy for operations that map directly to Spark SQL functions.
 *
 * Examples:
 * - upper(str) → upper(column)
 * - abs(int) → abs(column)
 * - left + right → left.plus(right)
 */
public record DirectMapping(
    @Nonnull Function<List<Column>, Column> sparkFunction
) implements OperationImplementation {

    @Override
    @Nonnull
    public Column generate(@Nonnull List<Column> args, @Nonnull CodeGenContext context) {
        return sparkFunction.apply(args);
    }

    // Factory methods for common patterns

    public static DirectMapping unary(Function<Column, Column> fn) {
        return new DirectMapping(args -> fn.apply(args.get(0)));
    }

    public static DirectMapping binary(BiFunction<Column, Column, Column> fn) {
        return new DirectMapping(args -> fn.apply(args.get(0), args.get(1)));
    }

    public static DirectMapping ternary(TriFunction<Column, Column, Column, Column> fn) {
        return new DirectMapping(args -> fn.apply(args.get(0), args.get(1), args.get(2)));
    }
}

// Usage examples:
DirectMapping.unary(functions::upper)                    // upper
DirectMapping.unary(functions::abs)                      // abs (numeric)
DirectMapping.binary(Column::plus)                       // + (numeric)
DirectMapping.binary(functions::concat)                  // + (string)
DirectMapping.ternary(functions::regexp_replace)         // replace
```

#### ComplexExpression (Multi-step Inline Logic)

```java
package com.example.fhirpath.codegen.spark.strategy;

import org.apache.spark.sql.Column;
import jakarta.annotation.Nonnull;
import java.util.List;

/**
 * Strategy for operations requiring complex multi-step expressions.
 *
 * Use when operation needs:
 * - Null handling beyond simple propagation
 * - Conditional logic
 * - Bounds checking
 * - Multiple Spark function calls
 *
 * Example: substring(str, pos, len) with null handling + bounds checking
 */
@FunctionalInterface
public interface ComplexExpression extends OperationImplementation {
    // Just an alias for OperationImplementation with semantic meaning

    /**
     * Create from lambda for inline complex logic.
     */
    static ComplexExpression of(BiFunction<List<Column>, CodeGenContext, Column> generator) {
        return generator::apply;
    }
}

// Usage example: substring implementation
ComplexExpression.of((args, ctx) -> {
    Column target = args.get(0);
    Column pos = args.get(1).plus(lit(1));  // 0-based → 1-based
    Column len = coalesce(args.get(2), lit(Integer.MAX_VALUE));

    // Null propagation + bounds checking
    Column nullCondition = target.isNull()
        .or(pos.isNull())
        .or(pos.leq(0))
        .or(pos.gt(length(target)));

    return when(not(nullCondition), substr(target, pos, len));
})
```

#### TypeHandlerDelegate (Delegate to Type-Specific Handler)

```java
package com.example.fhirpath.codegen.spark.strategy;

import com.example.fhirpath.codegen.spark.handler.TypeHandler;
import org.apache.spark.sql.Column;
import jakarta.annotation.Nonnull;
import java.util.List;

/**
 * Strategy that delegates to type-specific handlers.
 *
 * Used for operations where behavior varies significantly by type,
 * especially complex types like Quantity, DateTime.
 *
 * Example:
 * - "add" on INTEGER → DirectMapping
 * - "add" on QUANTITY → TypeHandlerDelegate → QuantityHandler
 */
public record TypeHandlerDelegate(
    @Nonnull TypeHandler handler,
    @Nonnull String operationName
) implements OperationImplementation {

    @Override
    @Nonnull
    public Column generate(@Nonnull List<Column> args, @Nonnull CodeGenContext context) {
        return handler.handleOperation(operationName, args, context);
    }
}
```

#### UdfInvocation (Call Registered UDF)

```java
package com.example.fhirpath.codegen.spark.strategy;

import org.apache.spark.sql.Column;
import org.apache.spark.sql.functions;
import jakarta.annotation.Nonnull;
import java.util.List;

/**
 * Strategy for operations implemented as Spark UDFs.
 *
 * UDF approach benefits:
 * - Complex logic in Java (not SQL)
 * - Unit testable
 * - Reusable across queries
 * - Spark optimization (caching, etc.)
 *
 * Example: Quantity unit conversion, DateTime precision handling
 */
public record UdfInvocation(
    @Nonnull String udfName
) implements OperationImplementation {

    @Override
    @Nonnull
    public Column generate(@Nonnull List<Column> args, @Nonnull CodeGenContext context) {
        // Verify UDF is registered
        if (!context.udfRegistry().isRegistered(udfName)) {
            throw new IllegalStateException(
                "UDF not registered: " + udfName +
                ". Register with UdfRegistry before code generation."
            );
        }

        // Call UDF with arguments
        return functions.call_function(udfName, args.toArray(new Column[0]));
    }

    /**
     * Create with automatic UDF registration.
     */
    public static UdfInvocation withRegistration(String udfName,
                                                   UserDefinedFunction udf,
                                                   CodeGenContext context) {
        context.udfRegistry().register(udfName, udf);
        return new UdfInvocation(udfName);
    }
}
```

### 4. TypeHandler (Type-Specific Operation Logic)

```java
package com.example.fhirpath.codegen.spark.handler;

import org.apache.spark.sql.Column;
import jakarta.annotation.Nonnull;
import java.util.List;

/**
 * Handler for type-specific operation implementations.
 *
 * Each complex type (Quantity, DateTime, Date, Time) has a handler
 * implementing all supported operations for that type.
 *
 * Provides:
 * - Cohesive type logic in one place
 * - Easy to test type-specific behavior
 * - Clear extension point for new types
 */
public interface TypeHandler {
    /**
     * Handle operation for this type.
     *
     * @param operationName FHIRPath operation (e.g., "add", "gt")
     * @param args Evaluated argument columns
     * @param context Code generation context
     * @return Generated column expression
     * @throws UnsupportedOperationException if operation not supported for type
     */
    @Nonnull
    Column handleOperation(@Nonnull String operationName,
                          @Nonnull List<Column> args,
                          @Nonnull CodeGenContext context);

    /**
     * Check if this handler supports the operation.
     */
    default boolean supportsOperation(@Nonnull String operationName) {
        // Default: try to handle, throw if unsupported
        return true;
    }
}
```

#### Example: QuantityHandler

```java
package com.example.fhirpath.codegen.spark.handler;

import com.example.fhirpath.codegen.spark.CodeGenContext;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.functions;
import jakarta.annotation.Nonnull;
import java.util.List;

import static org.apache.spark.sql.functions.*;

/**
 * Type handler for FHIRPath Quantity operations.
 *
 * Quantity is a struct: {value: Decimal, unit: String, system: String, code: String}
 *
 * Operations:
 * - Arithmetic: add, subtract, multiply, divide (with unit conversion)
 * - Comparison: gt, lt, geq, leq (with unit conversion)
 * - Math: abs, ceiling, floor, truncate
 */
public class QuantityHandler implements TypeHandler {

    @Override
    @Nonnull
    public Column handleOperation(@Nonnull String operationName,
                                  @Nonnull List<Column> args,
                                  @Nonnull CodeGenContext context) {
        return switch (operationName) {
            case "add" -> add(args.get(0), args.get(1), context);
            case "sub" -> subtract(args.get(0), args.get(1), context);
            case "multiply" -> multiply(args.get(0), args.get(1), context);
            case "divide" -> divide(args.get(0), args.get(1), context);

            case "gt" -> greaterThan(args.get(0), args.get(1), context);
            case "lt" -> lessThan(args.get(0), args.get(1), context);
            case "geq" -> greaterEqual(args.get(0), args.get(1), context);
            case "leq" -> lessEqual(args.get(0), args.get(1), context);

            case "abs" -> abs(args.get(0));
            case "ceiling" -> ceiling(args.get(0));
            case "floor" -> floor(args.get(0));

            default -> throw new UnsupportedOperationException(
                "Quantity does not support operation: " + operationName
            );
        };
    }

    // ========== Arithmetic ==========

    private Column add(Column left, Column right, CodeGenContext context) {
        // Strategy: Use UDF for proper unit conversion
        return context.udfRegistry().isRegistered("quantityAdd")
            ? functions.call_function("quantityAdd", left, right)
            : simpleAdd(left, right);  // Fallback: same units only
    }

    private Column simpleAdd(Column left, Column right) {
        // Simplified: assumes same units, no conversion
        return struct(
            left.getField("value").plus(right.getField("value")).alias("value"),
            left.getField("unit").alias("unit"),
            left.getField("system").alias("system"),
            left.getField("code").alias("code")
        );
    }

    private Column subtract(Column left, Column right, CodeGenContext context) {
        return context.udfRegistry().isRegistered("quantitySubtract")
            ? functions.call_function("quantitySubtract", left, right)
            : simpleSubtract(left, right);
    }

    private Column simpleSubtract(Column left, Column right) {
        return struct(
            left.getField("value").minus(right.getField("value")).alias("value"),
            left.getField("unit").alias("unit"),
            left.getField("system").alias("system"),
            left.getField("code").alias("code")
        );
    }

    private Column multiply(Column left, Column right, CodeGenContext context) {
        // Quantity * Quantity → complex unit multiplication (m * m = m²)
        // For now: simplified scalar multiplication
        return struct(
            left.getField("value").multiply(right.getField("value")).alias("value"),
            concat(left.getField("unit"), lit("*"), right.getField("unit")).alias("unit"),
            left.getField("system").alias("system"),
            left.getField("code").alias("code")
        );
    }

    private Column divide(Column left, Column right, CodeGenContext context) {
        return struct(
            left.getField("value").divide(right.getField("value")).alias("value"),
            concat(left.getField("unit"), lit("/"), right.getField("unit")).alias("unit"),
            left.getField("system").alias("system"),
            left.getField("code").alias("code")
        );
    }

    // ========== Comparison ==========

    private Column greaterThan(Column left, Column right, CodeGenContext context) {
        // Comparison requires unit conversion
        return context.udfRegistry().isRegistered("quantityCompare")
            ? functions.call_function("quantityCompare", left, right).gt(lit(0))
            : simpleGreaterThan(left, right);
    }

    private Column simpleGreaterThan(Column left, Column right) {
        // Simplified: assumes same units
        return left.getField("value").gt(right.getField("value"));
    }

    private Column lessThan(Column left, Column right, CodeGenContext context) {
        return context.udfRegistry().isRegistered("quantityCompare")
            ? functions.call_function("quantityCompare", left, right).lt(lit(0))
            : left.getField("value").lt(right.getField("value"));
    }

    private Column greaterEqual(Column left, Column right, CodeGenContext context) {
        return context.udfRegistry().isRegistered("quantityCompare")
            ? functions.call_function("quantityCompare", left, right).geq(lit(0))
            : left.getField("value").geq(right.getField("value"));
    }

    private Column lessEqual(Column left, Column right, CodeGenContext context) {
        return context.udfRegistry().isRegistered("quantityCompare")
            ? functions.call_function("quantityCompare", left, right).leq(lit(0))
            : left.getField("value").leq(right.getField("value"));
    }

    // ========== Math Functions ==========

    private Column abs(Column quantity) {
        return struct(
            functions.abs(quantity.getField("value")).alias("value"),
            quantity.getField("unit").alias("unit"),
            quantity.getField("system").alias("system"),
            quantity.getField("code").alias("code")
        );
    }

    private Column ceiling(Column quantity) {
        return struct(
            functions.ceil(quantity.getField("value")).alias("value"),
            quantity.getField("unit").alias("unit"),
            quantity.getField("system").alias("system"),
            quantity.getField("code").alias("code")
        );
    }

    private Column floor(Column quantity) {
        return struct(
            functions.floor(quantity.getField("value")).alias("value"),
            quantity.getField("unit").alias("unit"),
            quantity.getField("system").alias("system"),
            quantity.getField("code").alias("code")
        );
    }
}
```

### 5. UdfRegistry (UDF Management)

```java
package com.example.fhirpath.codegen.spark;

import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.api.java.UDF2;
import org.apache.spark.sql.types.DataType;
import jakarta.annotation.Nonnull;

import java.util.HashMap;
import java.util.Map;

/**
 * Registry for Spark UDFs used in code generation.
 *
 * Manages UDF lifecycle:
 * 1. Registration with Spark
 * 2. Discovery by name
 * 3. Verification before use
 *
 * UDFs can be registered:
 * - At startup (pre-registered UDFs)
 * - Dynamically during code generation
 * - Per SparkSession (session-specific UDFs)
 */
public class UdfRegistry {
    private final SparkSession spark;
    private final Map<String, UdfMetadata> registeredUdfs = new HashMap<>();

    public UdfRegistry(@Nonnull SparkSession spark) {
        this.spark = spark;
    }

    /**
     * Register a UDF with Spark and track it.
     */
    public <T, R> void register(@Nonnull String name,
                               @Nonnull UDF2<T, R> function,
                               @Nonnull DataType returnType) {
        spark.udf().register(name, function, returnType);
        registeredUdfs.put(name, new UdfMetadata(name, returnType));
    }

    /**
     * Check if UDF is registered.
     */
    public boolean isRegistered(@Nonnull String name) {
        return registeredUdfs.containsKey(name);
    }

    /**
     * Get UDF return type.
     */
    public DataType getReturnType(@Nonnull String name) {
        UdfMetadata metadata = registeredUdfs.get(name);
        if (metadata == null) {
            throw new IllegalArgumentException("UDF not registered: " + name);
        }
        return metadata.returnType();
    }

    /**
     * Register standard FHIRPath UDFs.
     */
    public void registerStandardUdfs() {
        // Quantity operations
        register("quantityAdd", new QuantityAddUdf(), QuantityType.INSTANCE);
        register("quantitySubtract", new QuantitySubtractUdf(), QuantityType.INSTANCE);
        register("quantityCompare", new QuantityCompareUdf(), DataTypes.IntegerType);

        // DateTime operations
        register("dateTimeCompare", new DateTimeCompareUdf(), DataTypes.IntegerType);
        register("dateTimeAdd", new DateTimeAddUdf(), DateTimeType.INSTANCE);

        // ... more UDFs
    }

    private record UdfMetadata(String name, DataType returnType) {}
}
```

### 6. OperationImplementationRegistry

```java
package com.example.fhirpath.codegen.spark;

import com.example.fhirpath.codegen.spark.strategy.*;
import com.example.fhirpath.codegen.spark.handler.*;
import com.example.fhirpath.typing.PrimitiveType;
import com.example.fhirpath.typing.Type;
import jakarta.annotation.Nonnull;

import java.util.HashMap;
import java.util.Map;

import static org.apache.spark.sql.functions.*;

/**
 * Registry mapping (operation, type) to implementation strategy.
 *
 * Key structure: OperationKey(operationName, typeGroup)
 * Value: OperationImplementation strategy
 *
 * Type resolution priority:
 * 1. Exact match (e.g., "add" + INTEGER)
 * 2. Type group match (e.g., "add" + NUMERIC)
 * 3. ANY match (e.g., "count" + ANY)
 *
 * Example entries:
 * - ("upper", STRING) → DirectMapping(functions::upper)
 * - ("add", INTEGER) → DirectMapping(Column::plus)
 * - ("add", QUANTITY) → TypeHandlerDelegate(QuantityHandler, "add")
 * - ("substring", STRING) → ComplexExpression(substring logic)
 */
public class OperationImplementationRegistry {

    private final Map<OperationKey, OperationImplementation> implementations = new HashMap<>();
    private final Map<PrimitiveType, TypeHandler> typeHandlers = new HashMap<>();

    public OperationImplementationRegistry() {
        registerTypeHandlers();
        registerOperations();
    }

    /**
     * Get implementation for operation on given type.
     *
     * @throws IllegalArgumentException if no implementation found
     */
    @Nonnull
    public OperationImplementation getImplementation(@Nonnull String operationName,
                                                     @Nonnull Type type) {
        // Try exact type match
        OperationKey exactKey = new OperationKey(operationName, type);
        OperationImplementation impl = implementations.get(exactKey);
        if (impl != null) {
            return impl;
        }

        // Try type group matches (NUMERIC, COMPARABLE, etc.)
        // ... (fallback logic)

        throw new IllegalArgumentException(
            "No implementation for operation: " + operationName + " on type: " + type
        );
    }

    /**
     * Register operation implementation for specific type.
     */
    public void register(@Nonnull String operationName,
                        @Nonnull Type type,
                        @Nonnull OperationImplementation implementation) {
        implementations.put(new OperationKey(operationName, type), implementation);
    }

    private void registerTypeHandlers() {
        typeHandlers.put(PrimitiveType.QUANTITY, new QuantityHandler());
        typeHandlers.put(PrimitiveType.DATE_TIME, new DateTimeHandler());
        typeHandlers.put(PrimitiveType.DATE, new DateHandler());
        typeHandlers.put(PrimitiveType.TIME, new TimeHandler());
    }

    private void registerOperations() {
        // STRING operations - all direct mappings
        register("upper", PrimitiveType.STRING, DirectMapping.unary(::upper));
        register("lower", PrimitiveType.STRING, DirectMapping.unary(::lower));
        register("length", PrimitiveType.STRING, DirectMapping.unary(::length));
        register("startsWith", PrimitiveType.STRING,
                DirectMapping.binary(Column::startsWith));
        register("endsWith", PrimitiveType.STRING,
                DirectMapping.binary(Column::endsWith));
        register("contains", PrimitiveType.STRING,
                DirectMapping.binary(Column::contains));
        register("replace", PrimitiveType.STRING,
                DirectMapping.ternary(::regexp_replace));

        // STRING substring - complex expression
        register("substring", PrimitiveType.STRING,
                ComplexExpression.of(this::generateSubstring));

        // NUMERIC operations - direct mappings
        register("add", PrimitiveType.INTEGER, DirectMapping.binary(Column::plus));
        register("add", PrimitiveType.DECIMAL, DirectMapping.binary(Column::plus));
        register("sub", PrimitiveType.INTEGER, DirectMapping.binary(Column::minus));
        register("sub", PrimitiveType.DECIMAL, DirectMapping.binary(Column::minus));
        register("multiply", PrimitiveType.INTEGER, DirectMapping.binary(Column::multiply));
        register("multiply", PrimitiveType.DECIMAL, DirectMapping.binary(Column::multiply));
        register("divide", PrimitiveType.INTEGER, DirectMapping.binary(Column::divide));
        register("divide", PrimitiveType.DECIMAL, DirectMapping.binary(Column::divide));
        register("mod", PrimitiveType.INTEGER, DirectMapping.binary(Column::mod));
        register("mod", PrimitiveType.DECIMAL, DirectMapping.binary(Column::mod));

        register("abs", PrimitiveType.INTEGER, DirectMapping.unary(::abs));
        register("abs", PrimitiveType.DECIMAL, DirectMapping.unary(::abs));
        register("ceiling", PrimitiveType.DECIMAL, DirectMapping.unary(::ceil));
        register("floor", PrimitiveType.DECIMAL, DirectMapping.unary(::floor));
        register("sqrt", PrimitiveType.DECIMAL, DirectMapping.unary(::sqrt));
        register("exp", PrimitiveType.DECIMAL, DirectMapping.unary(::exp));
        register("ln", PrimitiveType.DECIMAL, DirectMapping.unary(::log));

        // QUANTITY operations - delegate to handler
        TypeHandler quantityHandler = typeHandlers.get(PrimitiveType.QUANTITY);
        register("add", PrimitiveType.QUANTITY,
                new TypeHandlerDelegate(quantityHandler, "add"));
        register("sub", PrimitiveType.QUANTITY,
                new TypeHandlerDelegate(quantityHandler, "sub"));
        register("multiply", PrimitiveType.QUANTITY,
                new TypeHandlerDelegate(quantityHandler, "multiply"));
        register("divide", PrimitiveType.QUANTITY,
                new TypeHandlerDelegate(quantityHandler, "divide"));
        register("gt", PrimitiveType.QUANTITY,
                new TypeHandlerDelegate(quantityHandler, "gt"));
        register("lt", PrimitiveType.QUANTITY,
                new TypeHandlerDelegate(quantityHandler, "lt"));
        register("abs", PrimitiveType.QUANTITY,
                new TypeHandlerDelegate(quantityHandler, "abs"));

        // DATETIME operations - delegate to handler
        TypeHandler dateTimeHandler = typeHandlers.get(PrimitiveType.DATE_TIME);
        register("add", PrimitiveType.DATE_TIME,
                new TypeHandlerDelegate(dateTimeHandler, "add"));
        register("sub", PrimitiveType.DATE_TIME,
                new TypeHandlerDelegate(dateTimeHandler, "sub"));
        register("gt", PrimitiveType.DATE_TIME,
                new TypeHandlerDelegate(dateTimeHandler, "gt"));
        register("lt", PrimitiveType.DATE_TIME,
                new TypeHandlerDelegate(dateTimeHandler, "lt"));

        // ... more registrations
    }

    private Column generateSubstring(List<Column> args, CodeGenContext context) {
        Column target = args.get(0);
        Column pos = args.get(1).plus(lit(1));  // 0-based → 1-based
        Column len = coalesce(args.get(2), lit(Integer.MAX_VALUE));

        Column nullCondition = target.isNull()
            .or(pos.isNull())
            .or(pos.leq(0))
            .or(pos.gt(length(target)));

        return when(not(nullCondition), substr(target, pos, len));
    }

    private record OperationKey(String operationName, Type type) {}
}
```

### 7. Refactored SparkCodeGenerator

```java
package com.example.fhirpath.codegen.spark;

import com.example.fhirpath.ir.*;
import com.example.fhirpath.typing.Type;
import org.apache.spark.sql.Column;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.List;

/**
 * Simplified SparkCodeGenerator using registry pattern.
 *
 * Responsibilities:
 * 1. Traverse IR tree
 * 2. Coordinate code generation
 * 3. Delegate operation implementation to registry
 *
 * Removed:
 * - 56-line switch statement
 * - 15+ evaluate methods
 * - Type-specific logic (moved to handlers)
 */
public class SparkCodeGenerator implements IRNodeVisitor<Column> {

    @Nullable
    private final Column thisColumn;

    private final OperationImplementationRegistry implementationRegistry;
    private final UdfRegistry udfRegistry;

    public SparkCodeGenerator(@Nonnull SparkSession spark) {
        this(null, new OperationImplementationRegistry(), new UdfRegistry(spark));
    }

    private SparkCodeGenerator(@Nullable Column thisColumn,
                              @Nonnull OperationImplementationRegistry implRegistry,
                              @Nonnull UdfRegistry udfRegistry) {
        this.thisColumn = thisColumn;
        this.implementationRegistry = implRegistry;
        this.udfRegistry = udfRegistry;
    }

    @Nonnull
    private SparkCodeGenerator withThisColumn(@Nonnull Column thisColumn) {
        return new SparkCodeGenerator(thisColumn, implementationRegistry, udfRegistry);
    }

    // ========== Operation Handling (Simplified!) ==========

    @Override
    @Nonnull
    public Column visitOperation(@Nonnull Operation op) {
        // Evaluate arguments (except Lambdas)
        List<Column> argColumns = op.args().stream()
            .map(arg -> arg instanceof Lambda ? null : arg.accept(this))
            .toList();

        // Get implementation from registry
        OperationImplementation implementation =
            implementationRegistry.getImplementation(op.name(), op.getType());

        // Create context
        CodeGenContext context = new CodeGenContext(
            op.getType(),
            op.args().stream().map(IRNode::getType).toList(),
            thisColumn,
            udfRegistry,
            this
        );

        // Delegate to implementation
        return implementation.generate(argColumns, context);
    }

    // ========== Infrastructure Nodes (Unchanged) ==========

    @Override
    @Nonnull
    public Column visitLiteral(@Nonnull Literal lit) {
        if (lit.type() == Types.NULL || lit.value() == null) {
            return lit(null);
        }
        DataType sparkType = toSparkDataType(lit.getShape());
        return lit(lit.value()).cast(sparkType);
    }

    @Override
    @Nonnull
    public Column visitTraversal(@Nonnull Traversal trav) {
        Column target = trav.target().accept(this);
        Column result = target.getField(trav.fieldSpec().getName());

        if (!trav.target().isSingular()) {
            result = functions.filter(result, Column::isNotNull);
            if (!trav.fieldSpec().isSingular()) {
                result = functions.flatten(result);
            }
        }
        return result;
    }

    // ... other visit methods (Cast, Resource, Union, Equals, etc.) unchanged
}
```

---

## Implementation Examples

### Example 1: Adding a New Operation (`round`)

**Before** (Current Architecture):
```java
// Step 1: Update OperationRegistry (operation/OperationRegistry.java)
register("round", Signatures.binaryFunc(DECIMAL, INTEGER, DECIMAL))

// Step 2: Add to switch statement (SparkCodeGenerator.java)
case "round" -> evaluateRound(args, resultType);

// Step 3: Implement evaluation method (SparkCodeGenerator.java)
private Column evaluateRound(List<Column> args, Type resultType) {
    return round(args.get(0), args.get(1));
}
```
**Changes**: 3 locations

**After** (Proposed Architecture):
```java
// Step 1: Update OperationRegistry (operation/OperationRegistry.java)
register("round", Signatures.binaryFunc(DECIMAL, INTEGER, DECIMAL))

// Step 2: Register implementation (OperationImplementationRegistry.java)
register("round", PrimitiveType.DECIMAL,
        DirectMapping.binary(functions::round));
```
**Changes**: 2 locations (operation signature + implementation)

### Example 2: Adding a New Type (`Duration`)

**Before** (Current Architecture):
```java
// Step 1: Add Duration to PrimitiveType enum
DURATION

// Step 2: Update ALL type switches in SparkCodeGenerator
// evaluateAdd:
case DURATION -> duration(left).plus(duration(right));

// evaluateGreaterThan:
case DURATION -> duration(left).gt(duration(right));

// ... 10+ more methods

// Step 3: Create Duration wrapper
public record Duration(Column target) {
    public Column plus(Duration d) { throw UnsupportedOp; }
    public Column gt(Duration d) { throw UnsupportedOp; }
    // ... more stubs
}
```
**Changes**: 15+ locations

**After** (Proposed Architecture):
```java
// Step 1: Add Duration to PrimitiveType enum
DURATION

// Step 2: Create DurationHandler (single class)
public class DurationHandler implements TypeHandler {
    @Override
    public Column handleOperation(String opName, List<Column> args,
                                  CodeGenContext ctx) {
        return switch (opName) {
            case "add" -> add(args.get(0), args.get(1));
            case "gt" -> greaterThan(args.get(0), args.get(1));
            // ... all operations in one place
        };
    }

    private Column add(Column left, Column right) { /* impl */ }
    private Column greaterThan(Column left, Column right) { /* impl */ }
}

// Step 3: Register handler (OperationImplementationRegistry.java)
typeHandlers.put(PrimitiveType.DURATION, new DurationHandler());

// Auto-register all operations
for (String op : List.of("add", "sub", "gt", "lt", ...)) {
    register(op, PrimitiveType.DURATION,
            new TypeHandlerDelegate(durationHandler, op));
}
```
**Changes**: 2 locations (handler + registration)

### Example 3: Implementing Quantity Addition with UDF

**Before** (Current Architecture):
```java
// No UDF infrastructure - must inline everything

private Column evaluateAdd(List<Column> args, Type resultType) {
    return switch ((PrimitiveType) resultType) {
        case QUANTITY -> {
            // 50+ lines of unit conversion logic inlined here
            Column leftValue = args.get(0).getField("value");
            Column leftUnit = args.get(0).getField("unit");
            // ... complex UCUM conversion ...
            yield functions.struct(...);
        }
        // ...
    };
}
```

**After** (Proposed Architecture):
```java
// Step 1: Implement UDF
public class QuantityAddUdf implements UDF2<Row, Row, Row> {
    @Override
    public Row call(Row left, Row right) {
        // Clean Java code with unit conversion library
        Quantity leftQty = Quantity.fromRow(left);
        Quantity rightQty = Quantity.fromRow(right);
        Quantity result = leftQty.add(rightQty);  // Handles unit conversion
        return result.toRow();
    }
}

// Step 2: Register UDF
udfRegistry.register("quantityAdd", new QuantityAddUdf(), QuantityType.INSTANCE);

// Step 3: Use in handler
private Column add(Column left, Column right, CodeGenContext ctx) {
    return ctx.udfRegistry().isRegistered("quantityAdd")
        ? functions.call_function("quantityAdd", left, right)
        : simpleAdd(left, right);  // Fallback
}
```

**Benefits**:
- ✅ Testable UDF in isolation
- ✅ Reusable across queries
- ✅ Clear separation of concerns
- ✅ Gradual migration (fallback to simple version)

---

## Migration Strategy

### Phase 1: Foundation (No Breaking Changes)

**Goal**: Introduce new abstractions alongside existing code.

**Tasks**:
1. Create new packages:
   - `com.example.fhirpath.codegen.spark.strategy`
   - `com.example.fhirpath.codegen.spark.handler`
2. Implement core interfaces:
   - `OperationImplementation`
   - `CodeGenContext`
   - `TypeHandler`
3. Implement strategy classes:
   - `DirectMapping`
   - `ComplexExpression`
   - `TypeHandlerDelegate`
   - `UdfInvocation`
4. Implement `UdfRegistry`
5. Implement `OperationImplementationRegistry`

**Estimated effort**: 8-10 hours

### Phase 2: Type Handlers (Incremental)

**Goal**: Migrate complex type logic from switch statements to handlers.

**Tasks**:
1. Implement `QuantityHandler` (highest priority - most operations stubbed)
2. Implement `DateTimeHandler`
3. Implement `DateHandler`
4. Implement `TimeHandler`
5. Register handlers in `OperationImplementationRegistry`

**Approach**: Implement one handler at a time, test thoroughly before next.

**Estimated effort**: 12-16 hours

### Phase 3: SparkCodeGenerator Refactoring (Breaking Change)

**Goal**: Replace switch statement with registry delegation.

**Tasks**:
1. Add `OperationImplementationRegistry` field to `SparkCodeGenerator`
2. Refactor `visitOperation`:
   - Remove `evaluateOperation` switch
   - Delegate to registry
3. Migrate simple operations to `DirectMapping`:
   - String: upper, lower, replace, etc.
   - Numeric: abs, ceiling, floor, etc.
   - Boolean: and, or, xor, not
4. Migrate complex operations to `ComplexExpression`:
   - substring
   - where
   - iif
5. Delete old evaluate methods (15+ methods)

**Rollback plan**: Keep old code in separate branch until new implementation validated.

**Estimated effort**: 6-8 hours

### Phase 4: UDF Implementation (Optional)

**Goal**: Implement Quantity operations with proper unit conversion.

**Tasks**:
1. Integrate UCUM library (e.g., `org.fhir:ucum`)
2. Implement UDFs:
   - `QuantityAddUdf`
   - `QuantitySubtractUdf`
   - `QuantityCompareUdf`
   - `QuantityMultiplyUdf`
   - `QuantityDivideUdf`
3. Register UDFs in `UdfRegistry.registerStandardUdfs()`
4. Update `QuantityHandler` to use UDFs

**Estimated effort**: 16-20 hours (includes UCUM integration)

### Phase 5: Validation & Cleanup

**Goal**: Ensure new architecture works correctly.

**Tasks**:
1. Run full test suite
2. Performance benchmarks (compare old vs new)
3. Delete old code:
   - Old type wrapper files (Quantity.java, DateTime.java, etc.)
   - Old evaluate methods
4. Update documentation

**Estimated effort**: 4-6 hours

**Total estimated effort**: 46-60 hours

---

## Benefits & Trade-offs

### Benefits

#### 1. Extensibility (Open/Closed Principle)

**Before**:
- New operation → modify switch + implement method + update type switches
- New type → update 15+ evaluate methods

**After**:
- New operation → register implementation (1 line)
- New type → create handler + register (1 class + 1 registration block)

#### 2. Maintainability (Single Responsibility)

**Before**:
- 618-line class with multiple concerns
- Type logic scattered across 15+ methods

**After**:
- ~200-line coordinator (SparkCodeGenerator)
- ~100-line type handler per type (cohesive, focused)

#### 3. Testability

**Before**:
- Must test entire SparkCodeGenerator
- Hard to isolate type-specific logic

**After**:
- Test each handler independently
- Test strategies in isolation
- Test registry lookup separately

Example:
```java
@Test
void testQuantityAddition() {
    QuantityHandler handler = new QuantityHandler();
    Column left = /* create quantity column */;
    Column right = /* create quantity column */;
    CodeGenContext ctx = /* mock context */;

    Column result = handler.handleOperation("add",
                                            List.of(left, right), ctx);

    // Assert result structure
}
```

#### 4. Clarity (Explicit Strategy)

**Before**:
- Ad-hoc decisions scattered in code
- No clear pattern for choosing approach

**After**:
- Explicit strategies: DirectMapping, ComplexExpression, UdfInvocation
- Clear criteria documented in implementation

#### 5. UDF Support

**Before**:
- No infrastructure
- Complex logic must be inlined (unreadable)

**After**:
- Full UDF lifecycle management
- Clean, testable Java code for complex operations
- Performance optimization via Spark UDF

### Trade-offs

#### 1. Increased Abstraction

**Cost**: More classes/interfaces to understand
**Mitigation**: Clear documentation, naming conventions, examples

#### 2. Indirection

**Cost**: Registry lookup adds indirection
**Performance**: Negligible (happens once per operation during code generation, not query execution)
**Mitigation**: Benchmark to verify

#### 3. Initial Migration Effort

**Cost**: 46-60 hours estimated
**Mitigation**: Phased approach, gradual migration, keep old code until validated

#### 4. Learning Curve

**Cost**: New developers must learn registry pattern
**Mitigation**: Comprehensive documentation, clear examples, code comments

---

## Metrics

### Before vs After Comparison

| Metric | Before | After | Change |
|--------|--------|-------|--------|
| SparkCodeGenerator LOC | 618 | ~200 | -68% |
| Cyclomatic Complexity (evaluateOperation) | 35 | 0 (deleted) | -100% |
| Type Switches | 15+ methods | 0 | -100% |
| Type Handler LOC | 0 | ~400 (4 handlers) | +400 |
| Registry LOC | 0 | ~200 | +200 |
| **Total LOC** | **818** | **~950** | **+16%** |
| Classes | 5 | 14 | +9 |
| Extensibility Score | 2/10 | 9/10 | +350% |
| Testability Score | 3/10 | 9/10 | +200% |
| Type Coverage (operations working) | 44% | 90% (target) | +104% |

### SOLID Compliance

| Principle | Before | After |
|-----------|--------|-------|
| SRP | ⚠️ (3/5) | ✅ (5/5) |
| OCP | ❌ (1/5) | ✅ (5/5) |
| LSP | ✅ (5/5) | ✅ (5/5) |
| ISP | ⚠️ (4/5) | ✅ (5/5) |
| DIP | ⚠️ (3/5) | ✅ (5/5) |
| **Average** | **3.2/5** | **5.0/5** |

---

## Decision Points

### Decision 1: Implement All Phases or Subset?

**Option A: Full Implementation** (Phases 1-5)
- ✅ Complete feature parity
- ✅ UDF support for complex types
- ❌ 46-60 hours effort

**Option B: Core Refactoring Only** (Phases 1-3)
- ✅ Major architectural improvement
- ✅ Extensibility achieved
- ⚠️ Quantity operations still stubbed (can implement later)
- ✅ 26-34 hours effort

**Recommendation**: Start with Phases 1-3, implement Phase 4 when Quantity operations needed.

### Decision 2: Gradual Migration or Big Bang?

**Option A: Gradual** (Recommended)
- Keep old code until new validated
- Migrate operation-by-operation
- Lower risk

**Option B: Big Bang**
- Replace everything at once
- Faster completion
- Higher risk

**Recommendation**: Gradual migration per Phase 3 plan.

### Decision 3: UDF Library Choice

For Quantity unit conversion:

**Option A: Apache UCUM**
- ✅ Standard compliance
- ✅ Well-tested
- ❌ Heavy dependency

**Option B: Simple Conversion Table**
- ✅ Lightweight
- ❌ Limited coverage
- ⚠️ May not cover all UCUM cases

**Recommendation**: Start with Option B, upgrade to Option A if needed.

---

## Next Actions

1. **Review this proposal** with team
2. **Decision on scope**: Full implementation (Phases 1-5) or core only (Phases 1-3)?
3. **Create tickets** for chosen phases
4. **Begin Phase 1**: Foundation (non-breaking changes)
5. **Proof of concept**: Implement QuantityHandler for one operation (e.g., `abs`)
6. **Validate approach**: Review PoC, adjust if needed
7. **Continue with remaining phases**

**Estimated timeline**:
- Phase 1: 1-2 weeks
- Phase 2: 2-3 weeks
- Phase 3: 1 week
- **Total (Phases 1-3)**: **4-6 weeks** (part-time work)

---

## Appendix: Additional Type Handlers

### DateTimeHandler (Stub)

```java
public class DateTimeHandler implements TypeHandler {
    @Override
    public Column handleOperation(String opName, List<Column> args, CodeGenContext ctx) {
        return switch (opName) {
            case "gt" -> greaterThan(args.get(0), args.get(1), ctx);
            case "lt" -> lessThan(args.get(0), args.get(1), ctx);
            case "add" -> addQuantity(args.get(0), args.get(1), ctx);
            case "sub" -> subtractQuantity(args.get(0), args.get(1), ctx);
            default -> throw new UnsupportedOperationException(
                "DateTime does not support: " + opName);
        };
    }

    private Column greaterThan(Column left, Column right, CodeGenContext ctx) {
        // Simplified: direct timestamp comparison
        // Full implementation: handle precision (year/month/day/hour/etc.)
        return left.gt(right);
    }

    // ... more operations
}
```

### DateHandler & TimeHandler

Similar pattern to DateTimeHandler, focused on comparison operations.

---

**End of Proposal**
