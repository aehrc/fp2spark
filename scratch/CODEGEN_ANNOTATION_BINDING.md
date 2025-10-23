# Annotation-Based Operation Binding for Type Handlers

**Date**: 2025-01-22
**Extension**: Builds on `CODEGEN_REFACTORING_PROPOSAL.md`
**Purpose**: Eliminate manual switch statements in TypeHandlers using annotation-driven method binding

---

## Table of Contents

1. [Motivation](#motivation)
2. [Design Overview](#design-overview)
3. [Core Abstractions](#core-abstractions)
4. [Implementation](#implementation)
5. [Usage Examples](#usage-examples)
6. [Advanced Features](#advanced-features)
7. [Performance Considerations](#performance-considerations)

---

## Motivation

### Problem: Manual Dispatch in Every Handler

**Current Proposal** (from CODEGEN_REFACTORING_PROPOSAL.md):
```java
public class QuantityHandler implements TypeHandler {
    @Override
    public Column handleOperation(String opName, List<Column> args, CodeGenContext ctx) {
        return switch (opName) {
            case "add" -> add(args.get(0), args.get(1), ctx);
            case "sub" -> subtract(args.get(0), args.get(1), ctx);
            case "multiply" -> multiply(args.get(0), args.get(1), ctx);
            case "divide" -> divide(args.get(0), args.get(1), ctx);
            case "gt" -> greaterThan(args.get(0), args.get(1), ctx);
            case "lt" -> lessThan(args.get(0), args.get(1), ctx);
            case "abs" -> abs(args.get(0));
            // ... 10+ more cases
            default -> throw new UnsupportedOperationException(...);
        };
    }

    private Column add(Column left, Column right, CodeGenContext ctx) { /* impl */ }
    private Column subtract(Column left, Column right, CodeGenContext ctx) { /* impl */ }
    // ... more methods
}
```

**Issues**:
- ❌ Repetitive switch statement in **every handler**
- ❌ Manual method name → operation name mapping
- ❌ Easy to forget updating switch when adding methods
- ❌ No compile-time verification that all operations are handled

### Solution: Annotation-Driven Binding

**With Annotations**:
```java
public class QuantityHandler extends AnnotatedTypeHandler {

    @Operation("add")
    public Column add(Column left, Column right, CodeGenContext ctx) {
        // Unit conversion logic
        return ctx.udfRegistry().isRegistered("quantityAdd")
            ? functions.call_function("quantityAdd", left, right)
            : simpleAdd(left, right);
    }

    @Operation("sub")
    public Column subtract(Column left, Column right, CodeGenContext ctx) {
        // Implementation
    }

    @Operation("gt")
    public Column greaterThan(Column left, Column right, CodeGenContext ctx) {
        // Implementation
    }

    // No switch statement needed!
    // Automatic discovery and binding via reflection
}
```

**Benefits**:
- ✅ No manual switch statement
- ✅ Declarative operation mapping
- ✅ Compile-time method signature checking
- ✅ Automatic discovery of supported operations
- ✅ Clear, readable code

---

## Design Overview

### Architecture

```
┌──────────────────────────────────────────────────────────┐
│ AnnotatedTypeHandler (Abstract Base Class)              │
│                                                          │
│  - Scans for @Operation annotations at construction     │
│  - Builds Map<String, Method> registry                  │
│  - Implements handleOperation() with reflection dispatch│
│  - Provides utility methods for argument extraction     │
└────────────────┬─────────────────────────────────────────┘
                 │
                 ↓ extends
┌──────────────────────────────────────────────────────────┐
│ QuantityHandler                                          │
│                                                          │
│  @Operation("add")                                       │
│  public Column add(Column left, Column right, ...) {...} │
│                                                          │
│  @Operation("gt")                                        │
│  public Column greaterThan(Column left, ...) {...}      │
│                                                          │
│  // No handleOperation() override needed!                │
└──────────────────────────────────────────────────────────┘
```

### Method Signature Patterns

**Supported Patterns** (matched automatically):

```java
// Pattern 1: Unary operation
@Operation("abs")
public Column abs(Column arg);

@Operation("abs")
public Column abs(Column arg, CodeGenContext ctx);

// Pattern 2: Binary operation
@Operation("add")
public Column add(Column left, Column right);

@Operation("add")
public Column add(Column left, Column right, CodeGenContext ctx);

// Pattern 3: Ternary operation
@Operation("substring")
public Column substring(Column str, Column pos, Column len);

// Pattern 4: Variable arity (uses List)
@Operation("concat")
public Column concat(List<Column> args, CodeGenContext ctx);
```

**Automatic Argument Extraction**:
- Base class extracts arguments from `List<Column>`
- Invokes method with appropriate parameters
- Handles optional `CodeGenContext` parameter

---

## Core Abstractions

### 1. @Operation Annotation

```java
package com.example.fhirpath.codegen.spark.handler;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as a handler for a specific FHIRPath operation.
 *
 * Method signatures:
 * - Column methodName(Column arg)
 * - Column methodName(Column arg, CodeGenContext ctx)
 * - Column methodName(Column left, Column right)
 * - Column methodName(Column left, Column right, CodeGenContext ctx)
 * - Column methodName(Column a, Column b, Column c)
 * - Column methodName(List<Column> args, CodeGenContext ctx)
 *
 * Examples:
 * <pre>
 * {@literal @}Operation("abs")
 * public Column abs(Column value) {
 *     return struct(
 *         functions.abs(value.getField("value")).alias("value"),
 *         value.getField("unit").alias("unit")
 *     );
 * }
 *
 * {@literal @}Operation("add")
 * public Column add(Column left, Column right, CodeGenContext ctx) {
 *     return ctx.udfRegistry().isRegistered("quantityAdd")
 *         ? functions.call_function("quantityAdd", left, right)
 *         : simpleAdd(left, right);
 * }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Operation {
    /**
     * FHIRPath operation name (e.g., "add", "gt", "abs").
     */
    String value();

    /**
     * Optional description for documentation/debugging.
     */
    String description() default "";
}
```

### 2. AnnotatedTypeHandler (Abstract Base)

```java
package com.example.fhirpath.codegen.spark.handler;

import com.example.fhirpath.codegen.spark.CodeGenContext;
import org.apache.spark.sql.Column;
import jakarta.annotation.Nonnull;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;

/**
 * Abstract base class for type handlers using annotation-based operation binding.
 *
 * Subclasses declare operation handlers using @Operation annotation:
 * <pre>
 * public class QuantityHandler extends AnnotatedTypeHandler {
 *     {@literal @}Operation("add")
 *     public Column add(Column left, Column right, CodeGenContext ctx) {
 *         // implementation
 *     }
 * }
 * </pre>
 *
 * The base class:
 * 1. Scans subclass for @Operation methods at construction
 * 2. Builds operation registry (Map<String, Method>)
 * 3. Implements handleOperation() to dispatch via reflection
 * 4. Validates method signatures
 */
public abstract class AnnotatedTypeHandler implements TypeHandler {

    private final Map<String, OperationMethod> operationRegistry;

    protected AnnotatedTypeHandler() {
        this.operationRegistry = scanOperations();
    }

    /**
     * Scan for @Operation annotated methods and build registry.
     */
    private Map<String, OperationMethod> scanOperations() {
        Map<String, OperationMethod> registry = new HashMap<>();

        for (Method method : this.getClass().getDeclaredMethods()) {
            Operation annotation = method.getAnnotation(Operation.class);
            if (annotation != null) {
                String operationName = annotation.value();

                // Validate method signature
                validateMethodSignature(method, operationName);

                // Make accessible for invocation
                method.setAccessible(true);

                // Determine method pattern and create wrapper
                OperationMethod opMethod = createOperationMethod(method);

                // Register
                registry.put(operationName, opMethod);
            }
        }

        return Collections.unmodifiableMap(registry);
    }

    /**
     * Validate that method has correct signature for operation handler.
     */
    private void validateMethodSignature(Method method, String operationName) {
        // Return type must be Column
        if (!Column.class.equals(method.getReturnType())) {
            throw new IllegalStateException(
                String.format("@Operation method must return Column: %s.%s() for operation '%s'",
                    this.getClass().getSimpleName(), method.getName(), operationName)
            );
        }

        // Parameters must be valid pattern
        Parameter[] params = method.getParameters();
        if (params.length == 0) {
            throw new IllegalStateException(
                String.format("@Operation method must have at least one parameter: %s.%s()",
                    this.getClass().getSimpleName(), method.getName())
            );
        }

        // Validate parameter types
        for (int i = 0; i < params.length; i++) {
            Class<?> paramType = params[i].getType();
            boolean isValid = paramType.equals(Column.class)
                || paramType.equals(CodeGenContext.class)
                || (paramType.equals(List.class) && i == 0); // List<Column> only as first param

            if (!isValid) {
                throw new IllegalStateException(
                    String.format("Invalid parameter type at position %d in %s.%s(): %s. " +
                        "Must be Column, List<Column> (first param only), or CodeGenContext",
                        i, this.getClass().getSimpleName(), method.getName(), paramType.getSimpleName())
                );
            }
        }
    }

    /**
     * Create OperationMethod wrapper based on method signature pattern.
     */
    private OperationMethod createOperationMethod(Method method) {
        Parameter[] params = method.getParameters();

        // Detect if last parameter is CodeGenContext
        boolean hasContext = params.length > 0
            && params[params.length - 1].getType().equals(CodeGenContext.class);

        // Detect if first parameter is List<Column>
        boolean hasListArgs = params.length > 0
            && params[0].getType().equals(List.class);

        if (hasListArgs) {
            // Pattern: (List<Column> args, [CodeGenContext ctx])
            return new VariadicOperationMethod(method, hasContext);
        } else {
            // Pattern: (Column arg1, [Column arg2, ...], [CodeGenContext ctx])
            int argCount = hasContext ? params.length - 1 : params.length;
            return new FixedArityOperationMethod(method, argCount, hasContext);
        }
    }

    /**
     * Dispatch to appropriate handler method based on operation name.
     */
    @Override
    @Nonnull
    public Column handleOperation(@Nonnull String operationName,
                                  @Nonnull List<Column> args,
                                  @Nonnull CodeGenContext context) {
        OperationMethod opMethod = operationRegistry.get(operationName);

        if (opMethod == null) {
            throw new UnsupportedOperationException(
                String.format("%s does not support operation: %s",
                    this.getClass().getSimpleName(), operationName)
            );
        }

        try {
            return opMethod.invoke(this, args, context);
        } catch (Exception e) {
            throw new RuntimeException(
                String.format("Error invoking operation handler %s.%s",
                    this.getClass().getSimpleName(), operationName),
                e
            );
        }
    }

    /**
     * Check if this handler supports the operation.
     */
    @Override
    public boolean supportsOperation(@Nonnull String operationName) {
        return operationRegistry.containsKey(operationName);
    }

    /**
     * Get all supported operations.
     */
    public Set<String> getSupportedOperations() {
        return operationRegistry.keySet();
    }

    /**
     * Wrapper for operation handler method invocation.
     */
    private interface OperationMethod {
        Column invoke(AnnotatedTypeHandler handler, List<Column> args, CodeGenContext ctx)
            throws Exception;
    }

    /**
     * Handler for fixed-arity methods: (Column, Column, ..., [CodeGenContext])
     */
    private static class FixedArityOperationMethod implements OperationMethod {
        private final Method method;
        private final int argCount;
        private final boolean hasContext;

        FixedArityOperationMethod(Method method, int argCount, boolean hasContext) {
            this.method = method;
            this.argCount = argCount;
            this.hasContext = hasContext;
        }

        @Override
        public Column invoke(AnnotatedTypeHandler handler, List<Column> args, CodeGenContext ctx)
            throws Exception {
            // Validate argument count
            if (args.size() != argCount) {
                throw new IllegalArgumentException(
                    String.format("Expected %d arguments, got %d for %s",
                        argCount, args.size(), method.getName())
                );
            }

            // Build invocation arguments
            Object[] invokeArgs = hasContext
                ? buildArgsWithContext(args, ctx)
                : args.toArray();

            return (Column) method.invoke(handler, invokeArgs);
        }

        private Object[] buildArgsWithContext(List<Column> args, CodeGenContext ctx) {
            Object[] result = new Object[args.size() + 1];
            for (int i = 0; i < args.size(); i++) {
                result[i] = args.get(i);
            }
            result[args.size()] = ctx;
            return result;
        }
    }

    /**
     * Handler for variadic methods: (List<Column> args, [CodeGenContext ctx])
     */
    private static class VariadicOperationMethod implements OperationMethod {
        private final Method method;
        private final boolean hasContext;

        VariadicOperationMethod(Method method, boolean hasContext) {
            this.method = method;
            this.hasContext = hasContext;
        }

        @Override
        public Column invoke(AnnotatedTypeHandler handler, List<Column> args, CodeGenContext ctx)
            throws Exception {
            Object[] invokeArgs = hasContext
                ? new Object[]{args, ctx}
                : new Object[]{args};

            return (Column) method.invoke(handler, invokeArgs);
        }
    }
}
```

### 3. Enhanced TypeHandler Interface

```java
package com.example.fhirpath.codegen.spark.handler;

import org.apache.spark.sql.Column;
import jakarta.annotation.Nonnull;
import java.util.List;
import java.util.Set;

/**
 * Handler for type-specific operation implementations.
 *
 * Two implementation approaches:
 * 1. Manual: Override handleOperation() with switch statement
 * 2. Annotation-based: Extend AnnotatedTypeHandler, use @Operation
 */
public interface TypeHandler {

    @Nonnull
    Column handleOperation(@Nonnull String operationName,
                          @Nonnull List<Column> args,
                          @Nonnull CodeGenContext context);

    default boolean supportsOperation(@Nonnull String operationName) {
        return true; // Default: try to handle, throw if unsupported
    }

    /**
     * Get supported operations (for validation/documentation).
     * Only implemented by AnnotatedTypeHandler automatically.
     */
    default Set<String> getSupportedOperations() {
        return Set.of(); // Manual handlers can override if needed
    }
}
```

---

## Implementation

### Complete Example: QuantityHandler

```java
package com.example.fhirpath.codegen.spark.handler;

import com.example.fhirpath.codegen.spark.CodeGenContext;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.functions;
import jakarta.annotation.Nonnull;

import static org.apache.spark.sql.functions.*;

/**
 * Type handler for FHIRPath Quantity operations.
 *
 * Quantity: {value: Decimal, unit: String, system: String, code: String}
 *
 * Uses annotation-based binding - no manual switch statement needed!
 */
public class QuantityHandler extends AnnotatedTypeHandler {

    // ========== Arithmetic Operations ==========

    @Operation("add")
    public Column add(Column left, Column right, CodeGenContext ctx) {
        // Prefer UDF for proper unit conversion
        return ctx.udfRegistry().isRegistered("quantityAdd")
            ? functions.call_function("quantityAdd", left, right)
            : simpleAdd(left, right);
    }

    @Operation("sub")
    public Column subtract(Column left, Column right, CodeGenContext ctx) {
        return ctx.udfRegistry().isRegistered("quantitySubtract")
            ? functions.call_function("quantitySubtract", left, right)
            : simpleSubtract(left, right);
    }

    @Operation("multiply")
    public Column multiply(Column left, Column right) {
        // Simplified: scalar multiplication (Quantity * Quantity → complex unit math)
        return struct(
            left.getField("value").multiply(right.getField("value")).alias("value"),
            concat(left.getField("unit"), lit("*"), right.getField("unit")).alias("unit"),
            left.getField("system").alias("system"),
            left.getField("code").alias("code")
        );
    }

    @Operation("divide")
    public Column divide(Column left, Column right) {
        return struct(
            left.getField("value").divide(right.getField("value")).alias("value"),
            concat(left.getField("unit"), lit("/"), right.getField("unit")).alias("unit"),
            left.getField("system").alias("system"),
            left.getField("code").alias("code")
        );
    }

    @Operation("mod")
    public Column modulo(Column left, Column right) {
        // Only if units match (validated earlier)
        return struct(
            left.getField("value").mod(right.getField("value")).alias("value"),
            left.getField("unit").alias("unit"),
            left.getField("system").alias("system"),
            left.getField("code").alias("code")
        );
    }

    // ========== Comparison Operations ==========

    @Operation("gt")
    public Column greaterThan(Column left, Column right, CodeGenContext ctx) {
        return ctx.udfRegistry().isRegistered("quantityCompare")
            ? functions.call_function("quantityCompare", left, right).gt(lit(0))
            : simpleGreaterThan(left, right);
    }

    @Operation("lt")
    public Column lessThan(Column left, Column right, CodeGenContext ctx) {
        return ctx.udfRegistry().isRegistered("quantityCompare")
            ? functions.call_function("quantityCompare", left, right).lt(lit(0))
            : simpleLessThan(left, right);
    }

    @Operation("geq")
    public Column greaterEqual(Column left, Column right, CodeGenContext ctx) {
        return ctx.udfRegistry().isRegistered("quantityCompare")
            ? functions.call_function("quantityCompare", left, right).geq(lit(0))
            : left.getField("value").geq(right.getField("value"));
    }

    @Operation("leq")
    public Column lessEqual(Column left, Column right, CodeGenContext ctx) {
        return ctx.udfRegistry().isRegistered("quantityCompare")
            ? functions.call_function("quantityCompare", left, right).leq(lit(0))
            : left.getField("value").leq(right.getField("value"));
    }

    // ========== Math Functions ==========

    @Operation("abs")
    public Column abs(Column quantity) {
        return struct(
            functions.abs(quantity.getField("value")).alias("value"),
            quantity.getField("unit").alias("unit"),
            quantity.getField("system").alias("system"),
            quantity.getField("code").alias("code")
        );
    }

    @Operation("ceiling")
    public Column ceiling(Column quantity) {
        return struct(
            functions.ceil(quantity.getField("value")).alias("value"),
            quantity.getField("unit").alias("unit"),
            quantity.getField("system").alias("system"),
            quantity.getField("code").alias("code")
        );
    }

    @Operation("floor")
    public Column floor(Column quantity) {
        return struct(
            functions.floor(quantity.getField("value")).alias("value"),
            quantity.getField("unit").alias("unit"),
            quantity.getField("system").alias("system"),
            quantity.getField("code").alias("code")
        );
    }

    @Operation("truncate")
    public Column truncate(Column quantity) {
        Column value = quantity.getField("value");
        Column truncatedValue = when(value.geq(lit(0)), functions.floor(value))
            .otherwise(functions.ceil(value));

        return struct(
            truncatedValue.alias("value"),
            quantity.getField("unit").alias("unit"),
            quantity.getField("system").alias("system"),
            quantity.getField("code").alias("code")
        );
    }

    // ========== Helper Methods (Not Operation Handlers) ==========

    private Column simpleAdd(Column left, Column right) {
        // Fallback: assumes same units (no conversion)
        return struct(
            left.getField("value").plus(right.getField("value")).alias("value"),
            left.getField("unit").alias("unit"),
            left.getField("system").alias("system"),
            left.getField("code").alias("code")
        );
    }

    private Column simpleSubtract(Column left, Column right) {
        return struct(
            left.getField("value").minus(right.getField("value")).alias("value"),
            left.getField("unit").alias("unit"),
            left.getField("system").alias("system"),
            left.getField("code").alias("code")
        );
    }

    private Column simpleGreaterThan(Column left, Column right) {
        // Assumes same units
        return left.getField("value").gt(right.getField("value"));
    }

    private Column simpleLessThan(Column left, Column right) {
        return left.getField("value").lt(right.getField("value"));
    }
}
```

### DateTimeHandler Example

```java
package com.example.fhirpath.codegen.spark.handler;

import com.example.fhirpath.codegen.spark.CodeGenContext;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.functions;

import static org.apache.spark.sql.functions.*;

/**
 * Handler for DateTime operations.
 *
 * DateTime values are timestamps with optional precision metadata.
 * Comparisons and arithmetic depend on precision.
 */
public class DateTimeHandler extends AnnotatedTypeHandler {

    @Operation("gt")
    public Column greaterThan(Column left, Column right, CodeGenContext ctx) {
        // Simplified: direct timestamp comparison
        // Full: handle precision (year/month/day/hour/minute/second/millisecond)
        return ctx.udfRegistry().isRegistered("dateTimeCompare")
            ? functions.call_function("dateTimeCompare", left, right).gt(lit(0))
            : left.gt(right);
    }

    @Operation("lt")
    public Column lessThan(Column left, Column right, CodeGenContext ctx) {
        return ctx.udfRegistry().isRegistered("dateTimeCompare")
            ? functions.call_function("dateTimeCompare", left, right).lt(lit(0))
            : left.lt(right);
    }

    @Operation("geq")
    public Column greaterEqual(Column left, Column right, CodeGenContext ctx) {
        return ctx.udfRegistry().isRegistered("dateTimeCompare")
            ? functions.call_function("dateTimeCompare", left, right).geq(lit(0))
            : left.geq(right);
    }

    @Operation("leq")
    public Column lessEqual(Column left, Column right, CodeGenContext ctx) {
        return ctx.udfRegistry().isRegistered("dateTimeCompare")
            ? functions.call_function("dateTimeCompare", left, right).leq(lit(0))
            : left.leq(right);
    }

    @Operation("add")
    public Column addQuantity(Column dateTime, Column quantity, CodeGenContext ctx) {
        // Add Quantity (duration) to DateTime
        // Requires handling quantity unit (days, hours, etc.) and DateTime precision
        return ctx.udfRegistry().isRegistered("dateTimeAdd")
            ? functions.call_function("dateTimeAdd", dateTime, quantity)
            : simpleAdd(dateTime, quantity);
    }

    @Operation("sub")
    public Column subtractQuantity(Column dateTime, Column quantity, CodeGenContext ctx) {
        return ctx.udfRegistry().isRegistered("dateTimeSubtract")
            ? functions.call_function("dateTimeSubtract", dateTime, quantity)
            : simpleSubtract(dateTime, quantity);
    }

    private Column simpleAdd(Column dateTime, Column quantity) {
        // Simplified: assumes quantity is in seconds
        return dateTime.plus(quantity.getField("value"));
    }

    private Column simpleSubtract(Column dateTime, Column quantity) {
        return dateTime.minus(quantity.getField("value"));
    }
}
```

---

## Usage Examples

### Example 1: Handler Registration

```java
// OperationImplementationRegistry.java

private void registerTypeHandlers() {
    // Handlers automatically discover their @Operation methods
    QuantityHandler quantityHandler = new QuantityHandler();
    DateTimeHandler dateTimeHandler = new DateTimeHandler();
    DateHandler dateHandler = new DateHandler();
    TimeHandler timeHandler = new TimeHandler();

    typeHandlers.put(PrimitiveType.QUANTITY, quantityHandler);
    typeHandlers.put(PrimitiveType.DATE_TIME, dateTimeHandler);
    typeHandlers.put(PrimitiveType.DATE, dateHandler);
    typeHandlers.put(PrimitiveType.TIME, timeHandler);

    // Auto-register all operations supported by each handler
    registerHandlerOperations(PrimitiveType.QUANTITY, quantityHandler);
    registerHandlerOperations(PrimitiveType.DATE_TIME, dateTimeHandler);
    registerHandlerOperations(PrimitiveType.DATE, dateHandler);
    registerHandlerOperations(PrimitiveType.TIME, timeHandler);
}

/**
 * Auto-register all operations from annotated handler.
 */
private void registerHandlerOperations(PrimitiveType type, TypeHandler handler) {
    if (handler instanceof AnnotatedTypeHandler annotatedHandler) {
        // Get all supported operations from annotation scan
        for (String operationName : annotatedHandler.getSupportedOperations()) {
            register(operationName, type,
                new TypeHandlerDelegate(handler, operationName));
        }
    }
}
```

### Example 2: Validation & Debugging

```java
// Validate that all required operations are implemented
@Test
void testQuantityHandlerCompleteness() {
    QuantityHandler handler = new QuantityHandler();

    Set<String> requiredOperations = Set.of(
        "add", "sub", "multiply", "divide",
        "gt", "lt", "geq", "leq",
        "abs", "ceiling", "floor"
    );

    Set<String> supportedOperations = handler.getSupportedOperations();

    // Verify all required operations are supported
    for (String op : requiredOperations) {
        assertTrue(supportedOperations.contains(op),
            "Missing operation: " + op);
    }
}

// List all operations at runtime for debugging
@Test
void listSupportedOperations() {
    QuantityHandler handler = new QuantityHandler();

    System.out.println("QuantityHandler supports:");
    handler.getSupportedOperations().stream()
        .sorted()
        .forEach(op -> System.out.println("  - " + op));
}

// Output:
//   QuantityHandler supports:
//     - abs
//     - add
//     - ceiling
//     - divide
//     - floor
//     - geq
//     - gt
//     - leq
//     - lt
//     - mod
//     - multiply
//     - sub
//     - truncate
```

### Example 3: Documentation Generation

```java
/**
 * Generate documentation for all type handlers.
 */
public class HandlerDocGenerator {
    public static void main(String[] args) {
        Map<PrimitiveType, TypeHandler> handlers = Map.of(
            PrimitiveType.QUANTITY, new QuantityHandler(),
            PrimitiveType.DATE_TIME, new DateTimeHandler(),
            PrimitiveType.DATE, new DateHandler(),
            PrimitiveType.TIME, new TimeHandler()
        );

        for (var entry : handlers.entrySet()) {
            PrimitiveType type = entry.getKey();
            TypeHandler handler = entry.getValue();

            if (handler instanceof AnnotatedTypeHandler annotated) {
                System.out.println("## " + type);
                System.out.println();
                System.out.println("Supported operations:");
                annotated.getSupportedOperations().stream()
                    .sorted()
                    .forEach(op -> System.out.println("- `" + op + "`"));
                System.out.println();
            }
        }
    }
}

// Output:
// ## QUANTITY
//
// Supported operations:
// - `abs`
// - `add`
// - `ceiling`
// - `divide`
// - `floor`
// - `geq`
// - `gt`
// - `leq`
// - `lt`
// - `mod`
// - `multiply`
// - `sub`
// - `truncate`
//
// ## DATE_TIME
//
// Supported operations:
// - `add`
// - `geq`
// - `gt`
// - `leq`
// - `lt`
// - `sub`
```

---

## Advanced Features

### Feature 1: Method Signature Flexibility

**Multiple Patterns Supported**:

```java
public class FlexibleHandler extends AnnotatedTypeHandler {

    // Pattern 1: No context (for simple operations)
    @Operation("negate")
    public Column negate(Column value) {
        return value.multiply(lit(-1));
    }

    // Pattern 2: With context (for UDF access)
    @Operation("add")
    public Column add(Column left, Column right, CodeGenContext ctx) {
        return ctx.udfRegistry().isRegistered("customAdd")
            ? functions.call_function("customAdd", left, right)
            : left.plus(right);
    }

    // Pattern 3: Ternary operation
    @Operation("between")
    public Column between(Column value, Column min, Column max) {
        return value.geq(min).and(value.leq(max));
    }

    // Pattern 4: Variadic (for operations with variable arity)
    @Operation("concat")
    public Column concat(List<Column> args, CodeGenContext ctx) {
        return args.stream()
            .reduce(Column::plus)
            .orElse(lit(""));
    }
}
```

### Feature 2: Enhanced Validation

```java
/**
 * Enhanced base class with additional validation.
 */
public abstract class ValidatedTypeHandler extends AnnotatedTypeHandler {

    @Override
    protected void validateMethodSignature(Method method, String operationName) {
        super.validateMethodSignature(method, operationName);

        // Additional validation: method must be public
        if (!Modifier.isPublic(method.getModifiers())) {
            throw new IllegalStateException(
                String.format("@Operation method must be public: %s.%s",
                    getClass().getSimpleName(), method.getName())
            );
        }

        // Validation: no primitive types (use Column)
        for (Parameter param : method.getParameters()) {
            if (param.getType().isPrimitive()) {
                throw new IllegalStateException(
                    String.format("@Operation method cannot use primitive types: %s.%s",
                        getClass().getSimpleName(), method.getName())
                );
            }
        }
    }
}
```

### Feature 3: Operation Metadata

**Enhanced Annotation**:
```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Operation {
    String value();

    String description() default "";

    /**
     * Minimum arguments (for variadic operations).
     */
    int minArgs() default -1;

    /**
     * Maximum arguments (for variadic operations).
     */
    int maxArgs() default -1;

    /**
     * Whether this operation requires UDF support.
     */
    boolean requiresUdf() default false;

    /**
     * UDF names this operation depends on (for validation).
     */
    String[] udfDependencies() default {};
}

// Usage:
@Operation(
    value = "add",
    description = "Add two Quantities with unit conversion",
    requiresUdf = false,
    udfDependencies = {"quantityAdd"}  // Optional UDF
)
public Column add(Column left, Column right, CodeGenContext ctx) {
    // Implementation
}
```

**Validation at Handler Construction**:
```java
protected AnnotatedTypeHandler() {
    this.operationRegistry = scanOperations();
    validateUdfDependencies();  // Check all UDF dependencies
}

private void validateUdfDependencies() {
    for (var entry : operationRegistry.entrySet()) {
        Operation annotation = entry.getValue().getAnnotation();
        if (annotation.requiresUdf() && annotation.udfDependencies().length == 0) {
            throw new IllegalStateException(
                String.format("Operation '%s' requires UDF but no dependencies declared",
                    entry.getKey())
            );
        }
    }
}
```

### Feature 4: Caching for Performance

```java
/**
 * Cached operation registry to avoid repeated reflection scans.
 */
public abstract class CachedAnnotatedTypeHandler extends AnnotatedTypeHandler {

    private static final Map<Class<?>, Map<String, OperationMethod>> REGISTRY_CACHE
        = new ConcurrentHashMap<>();

    @Override
    protected Map<String, OperationMethod> scanOperations() {
        // Check cache first
        return REGISTRY_CACHE.computeIfAbsent(
            this.getClass(),
            clazz -> super.scanOperations()
        );
    }
}
```

---

## Performance Considerations

### Reflection Overhead

**Cost**: Reflection-based method invocation

**Measurement**:
```java
// Benchmark: Annotation dispatch vs Manual dispatch
@Benchmark
public Column benchmarkAnnotationDispatch() {
    QuantityHandler handler = new QuantityHandler();  // Uses annotations
    return handler.handleOperation("add", args, context);
}

@Benchmark
public Column benchmarkManualDispatch() {
    ManualQuantityHandler handler = new ManualQuantityHandler();  // Uses switch
    return handler.handleOperation("add", args, context);
}
```

**Expected Results**:
- Annotation dispatch: ~50-100ns per operation
- Manual dispatch: ~5-10ns per operation
- **Difference**: ~40-90ns (negligible in SQL generation context)

**Analysis**:
- ✅ Code generation is NOT in query execution path
- ✅ Happens once per query compilation
- ✅ Typical query has 5-50 operations → 250-5000ns total overhead
- ✅ Completely dwarfed by Spark query planning (milliseconds)

**Conclusion**: Reflection overhead is acceptable.

### Optimization: Method Handle Caching

**For ultra-performance**, use MethodHandles instead of reflection:

```java
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;

private static class OptimizedOperationMethod implements OperationMethod {
    private final MethodHandle handle;
    private final int argCount;
    private final boolean hasContext;

    OptimizedOperationMethod(Method method, int argCount, boolean hasContext)
        throws IllegalAccessException {
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        this.handle = lookup.unreflect(method);
        this.argCount = argCount;
        this.hasContext = hasContext;
    }

    @Override
    public Column invoke(AnnotatedTypeHandler handler, List<Column> args, CodeGenContext ctx)
        throws Throwable {
        // MethodHandle invocation is ~2x faster than reflection
        Object[] invokeArgs = buildArgs(handler, args, ctx);
        return (Column) handle.invokeWithArguments(invokeArgs);
    }
}
```

**Performance**:
- Reflection: ~50-100ns
- MethodHandle: ~25-50ns
- Direct call: ~5-10ns

**Recommendation**: Start with reflection (simpler), optimize to MethodHandles if profiling shows bottleneck.

---

## Comparison: Before vs After

### Before: Manual Switch

```java
public class QuantityHandler implements TypeHandler {
    @Override
    public Column handleOperation(String opName, List<Column> args, CodeGenContext ctx) {
        return switch (opName) {
            case "add" -> add(args.get(0), args.get(1), ctx);
            case "sub" -> subtract(args.get(0), args.get(1), ctx);
            case "multiply" -> multiply(args.get(0), args.get(1), ctx);
            case "divide" -> divide(args.get(0), args.get(1), ctx);
            case "mod" -> modulo(args.get(0), args.get(1), ctx);
            case "gt" -> greaterThan(args.get(0), args.get(1), ctx);
            case "lt" -> lessThan(args.get(0), args.get(1), ctx);
            case "geq" -> greaterEqual(args.get(0), args.get(1), ctx);
            case "leq" -> lessEqual(args.get(0), args.get(1), ctx);
            case "abs" -> abs(args.get(0));
            case "ceiling" -> ceiling(args.get(0));
            case "floor" -> floor(args.get(0));
            case "truncate" -> truncate(args.get(0));
            default -> throw new UnsupportedOperationException(
                "Quantity does not support operation: " + opName
            );
        };
    }

    private Column add(Column left, Column right, CodeGenContext ctx) { /* impl */ }
    // ... 12 more methods
}
```

**Issues**:
- ❌ 14-line switch statement repeated in every handler
- ❌ Manual argument extraction (`args.get(0)`, `args.get(1)`)
- ❌ Easy to forget updating switch when adding method
- ❌ No compile-time verification of completeness

### After: Annotation-Based

```java
public class QuantityHandler extends AnnotatedTypeHandler {

    @Operation("add")
    public Column add(Column left, Column right, CodeGenContext ctx) { /* impl */ }

    @Operation("sub")
    public Column subtract(Column left, Column right, CodeGenContext ctx) { /* impl */ }

    @Operation("multiply")
    public Column multiply(Column left, Column right) { /* impl */ }

    @Operation("divide")
    public Column divide(Column left, Column right) { /* impl */ }

    @Operation("mod")
    public Column modulo(Column left, Column right) { /* impl */ }

    @Operation("gt")
    public Column greaterThan(Column left, Column right, CodeGenContext ctx) { /* impl */ }

    @Operation("lt")
    public Column lessThan(Column left, Column right, CodeGenContext ctx) { /* impl */ }

    @Operation("geq")
    public Column greaterEqual(Column left, Column right, CodeGenContext ctx) { /* impl */ }

    @Operation("leq")
    public Column lessEqual(Column left, Column right, CodeGenContext ctx) { /* impl */ }

    @Operation("abs")
    public Column abs(Column quantity) { /* impl */ }

    @Operation("ceiling")
    public Column ceiling(Column quantity) { /* impl */ }

    @Operation("floor")
    public Column floor(Column quantity) { /* impl */ }

    @Operation("truncate")
    public Column truncate(Column quantity) { /* impl */ }

    // No switch statement needed!
    // Automatic discovery, argument extraction, and dispatch
}
```

**Benefits**:
- ✅ No manual switch statement (14 lines → 0 lines)
- ✅ Declarative operation mapping
- ✅ Type-safe method signatures
- ✅ Automatic argument extraction
- ✅ Automatic discovery via `getSupportedOperations()`

---

## Migration Path

### Step 1: Introduce Abstractions (No Breaking Changes)

1. Add `@Operation` annotation
2. Add `AnnotatedTypeHandler` base class
3. Keep existing `TypeHandler` interface unchanged

### Step 2: Migrate One Handler (Proof of Concept)

1. Create `QuantityHandler extends AnnotatedTypeHandler`
2. Annotate methods with `@Operation`
3. Delete manual `handleOperation()` switch
4. Test thoroughly
5. Compare performance (benchmark)

### Step 3: Migrate Remaining Handlers

1. `DateTimeHandler`
2. `DateHandler`
3. `TimeHandler`

### Step 4: Enhance (Optional)

1. Add operation metadata (description, UDF dependencies)
2. Add validation (UDF dependency checking)
3. Add MethodHandle optimization if needed

**Estimated Effort**:
- Step 1: 2-3 hours
- Step 2: 2-3 hours (includes testing)
- Step 3: 3-4 hours
- **Total**: 7-10 hours

---

## Conclusion

**Annotation-based binding provides**:
- ✅ Cleaner, more declarative code
- ✅ Automatic operation discovery
- ✅ Type-safe method signatures
- ✅ Easier testing and validation
- ✅ Better documentation generation
- ✅ Negligible performance overhead

**Recommendation**: Adopt annotation-based binding for all type handlers.

**Next Steps**:
1. Review this design
2. Implement `@Operation` and `AnnotatedTypeHandler`
3. Migrate `QuantityHandler` as proof of concept
4. Validate performance and correctness
5. Migrate remaining handlers
