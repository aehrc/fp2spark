# Annotation-Based Factory Methods for Operation Mappings

**Date**: 2025-01-22
**Extension**: Builds on `CODEGEN_ANNOTATION_BINDING.md`
**Purpose**: Support declarative operation mappings via annotated factory methods

---

## Table of Contents

1. [Motivation](#motivation)
2. [Design Overview](#design-overview)
3. [Core Abstractions](#core-abstractions)
4. [Implementation](#implementation)
5. [Usage Examples](#usage-examples)
6. [Complete Handler Examples](#complete-handler-examples)

---

## Motivation

### Problem: Two Patterns, One Declaration Style

Type handlers have **two types of operations**:

**1. Complex Operations** - Need full method implementation:
```java
@Operation("add")
public Column add(Column left, Column right, CodeGenContext ctx) {
    // Complex logic: UDF selection, unit conversion, etc.
    return ctx.udfRegistry().isRegistered("quantityAdd")
        ? functions.call_function("quantityAdd", left, right)
        : simpleAdd(left, right);
}
```

**2. Simple Mappings** - Just wrap Spark function or build simple struct:
```java
@Operation("negate")
public Column negate(Column value) {
    return struct(
        value.getField("value").multiply(lit(-1)).alias("value"),
        value.getField("unit").alias("unit"),
        value.getField("system").alias("system"),
        value.getField("code").alias("code")
    );
}
```

### Issue: Verbose for Simple Operations

For simple operations, the full method declaration is verbose:
- Requires separate method per operation
- Repetitive parameter declarations
- Scatters simple mappings across the class

**Example**: 5 simple Quantity math operations
```java
@Operation("negate")
public Column negate(Column value) {
    return struct(value.getField("value").multiply(lit(-1)).alias("value"), ...);
}

@Operation("sign")
public Column sign(Column value) {
    Column v = value.getField("value");
    return when(v.gt(0), lit(1)).when(v.lt(0), lit(-1)).otherwise(lit(0));
}

@Operation("round")
public Column round(Column value) {
    return struct(functions.round(value.getField("value")).alias("value"), ...);
}

// ... more similar operations
```

**Total**: ~60 lines for 5 simple operations

### Solution: Declarative Factory Methods

**Proposed**:
```java
@OperationMappings
public Stream<NamedMapping> simpleMathOperations() {
    return Stream.of(
        direct("negate", value ->
            struct(value.getField("value").multiply(lit(-1)).alias("value"),
                   value.getField("unit").alias("unit"),
                   value.getField("system").alias("system"),
                   value.getField("code").alias("code"))
        ),

        direct("sign", value -> {
            Column v = value.getField("value");
            return when(v.gt(0), lit(1)).when(v.lt(0), lit(-1)).otherwise(lit(0));
        }),

        direct("round", value ->
            struct(functions.round(value.getField("value")).alias("value"),
                   value.getField("unit").alias("unit"),
                   value.getField("system").alias("system"),
                   value.getField("code").alias("code"))
        )
    );
}
```

**Benefits**:
- ✅ Group related operations together
- ✅ More concise (lambdas vs full methods)
- ✅ Clear separation: factory methods for simple, @Operation for complex
- ✅ Can generate mappings programmatically (e.g., loop over operations)

---

## Design Overview

### Architecture

```
AnnotatedTypeHandler scans for:

1. @Operation methods → OperationMethod wrappers
   ↓
   Method invocation with reflection

2. @OperationMappings methods → Stream<NamedMapping>
   ↓
   OperationImplementation instances (DirectMapping, etc.)

Both registered in: Map<String, OperationImplementation>
```

### Supported Mapping Types

```java
@OperationMappings
public Stream<NamedMapping> mappings() {
    return Stream.of(
        // 1. DirectMapping - simple Spark function
        direct("upper", str -> upper(str)),

        // 2. DirectMapping with binary operation
        direct("concat", (left, right) -> concat(left, right)),

        // 3. ComplexExpression - multi-step logic
        complex("substring", (args, ctx) -> {
            // Complex implementation
        }),

        // 4. UDF invocation
        udf("customOp", "myUdfName"),

        // 5. Programmatic generation
        ...generateComparisonOps()
    );
}
```

---

## Core Abstractions

### 1. @OperationMappings Annotation

```java
package com.example.fhirpath.codegen.spark.handler;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as a factory for operation mappings.
 *
 * Method signature must be:
 *   Stream<NamedMapping> methodName()
 *
 * The method should return a stream of operation mappings, each
 * pairing an operation name with its implementation strategy.
 *
 * Example:
 * <pre>
 * {@literal @}OperationMappings
 * public Stream<NamedMapping> stringOperations() {
 *     return Stream.of(
 *         direct("upper", str -> upper(str)),
 *         direct("lower", str -> lower(str))
 *     );
 * }
 * </pre>
 *
 * Benefits:
 * - Declare multiple related operations together
 * - More concise than individual @Operation methods
 * - Can generate mappings programmatically
 * - Clear separation: factory for simple, @Operation for complex
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OperationMappings {
    /**
     * Optional description for documentation.
     */
    String value() default "";
}
```

### 2. NamedMapping Record

```java
package com.example.fhirpath.codegen.spark.handler;

import com.example.fhirpath.codegen.spark.strategy.OperationImplementation;
import jakarta.annotation.Nonnull;

/**
 * Pairs an operation name with its implementation strategy.
 *
 * Used by @OperationMappings factory methods to declare multiple
 * operations in a declarative style.
 *
 * Example:
 * <pre>
 * NamedMapping.direct("upper", str -> upper(str))
 * NamedMapping.udf("quantityAdd", "quantityAdd")
 * </pre>
 */
public record NamedMapping(
    @Nonnull String operationName,
    @Nonnull OperationImplementation implementation
) {
    // Factory methods for convenience (see MappingBuilder below)
}
```

### 3. MappingBuilder Utility

```java
package com.example.fhirpath.codegen.spark.handler;

import com.example.fhirpath.codegen.spark.CodeGenContext;
import com.example.fhirpath.codegen.spark.strategy.*;
import org.apache.spark.sql.Column;
import jakarta.annotation.Nonnull;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Utility class providing factory methods for NamedMapping creation.
 *
 * Import statically for concise mapping declarations:
 * <pre>
 * import static com.example.fhirpath.codegen.spark.handler.MappingBuilder.*;
 *
 * {@literal @}OperationMappings
 * public Stream<NamedMapping> ops() {
 *     return Stream.of(
 *         direct("upper", str -> upper(str)),
 *         binary("add", (a, b) -> a.plus(b))
 *     );
 * }
 * </pre>
 */
public final class MappingBuilder {

    private MappingBuilder() {} // Utility class

    // ========== DirectMapping Factories ==========

    /**
     * Create unary operation: (Column) -> Column
     */
    @Nonnull
    public static NamedMapping direct(@Nonnull String name,
                                     @Nonnull Function<Column, Column> fn) {
        return new NamedMapping(name, DirectMapping.unary(fn));
    }

    /**
     * Alias for direct() - more explicit name.
     */
    @Nonnull
    public static NamedMapping unary(@Nonnull String name,
                                    @Nonnull Function<Column, Column> fn) {
        return direct(name, fn);
    }

    /**
     * Create binary operation: (Column, Column) -> Column
     */
    @Nonnull
    public static NamedMapping binary(@Nonnull String name,
                                     @Nonnull BiFunction<Column, Column, Column> fn) {
        return new NamedMapping(name, DirectMapping.binary(fn));
    }

    /**
     * Create ternary operation: (Column, Column, Column) -> Column
     */
    @Nonnull
    public static NamedMapping ternary(@Nonnull String name,
                                      @Nonnull TriFunction<Column, Column, Column, Column> fn) {
        return new NamedMapping(name, DirectMapping.ternary(fn));
    }

    /**
     * Create direct mapping with custom function.
     */
    @Nonnull
    public static NamedMapping direct(@Nonnull String name,
                                     @Nonnull Function<List<Column>, Column> fn) {
        return new NamedMapping(name, new DirectMapping(fn));
    }

    // ========== ComplexExpression Factories ==========

    /**
     * Create complex expression: (List<Column>, CodeGenContext) -> Column
     */
    @Nonnull
    public static NamedMapping complex(@Nonnull String name,
                                      @Nonnull BiFunction<List<Column>, CodeGenContext, Column> fn) {
        return new NamedMapping(name, ComplexExpression.of(fn));
    }

    /**
     * Create complex unary expression with context access.
     */
    @Nonnull
    public static NamedMapping complexUnary(@Nonnull String name,
                                           @Nonnull BiFunction<Column, CodeGenContext, Column> fn) {
        return new NamedMapping(name,
            ComplexExpression.of((args, ctx) -> fn.apply(args.get(0), ctx)));
    }

    /**
     * Create complex binary expression with context access.
     */
    @Nonnull
    public static NamedMapping complexBinary(@Nonnull String name,
                                            @Nonnull TriFunction<Column, Column, CodeGenContext, Column> fn) {
        return new NamedMapping(name,
            ComplexExpression.of((args, ctx) -> fn.apply(args.get(0), args.get(1), ctx)));
    }

    // ========== UDF Factories ==========

    /**
     * Create UDF invocation mapping.
     */
    @Nonnull
    public static NamedMapping udf(@Nonnull String operationName,
                                  @Nonnull String udfName) {
        return new NamedMapping(operationName, new UdfInvocation(udfName));
    }

    /**
     * Create UDF invocation where operation name matches UDF name.
     */
    @Nonnull
    public static NamedMapping udf(@Nonnull String name) {
        return udf(name, name);
    }

    // ========== Programmatic Generation ==========

    /**
     * Generate multiple mappings with same pattern.
     *
     * Example:
     * <pre>
     * generate(
     *     List.of("abs", "ceiling", "floor"),
     *     opName -> unary(opName, value -> applyToValue(value, opName))
     * )
     * </pre>
     */
    @Nonnull
    public static Stream<NamedMapping> generate(@Nonnull List<String> operations,
                                               @Nonnull Function<String, NamedMapping> mappingFn) {
        return operations.stream().map(mappingFn);
    }
}
```

### 4. Extended AnnotatedTypeHandler

```java
package com.example.fhirpath.codegen.spark.handler;

import jakarta.annotation.Nonnull;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Stream;

/**
 * Extended to support both @Operation methods and @OperationMappings factories.
 */
public abstract class AnnotatedTypeHandler implements TypeHandler {

    private final Map<String, OperationImplementation> operationRegistry;

    protected AnnotatedTypeHandler() {
        this.operationRegistry = buildRegistry();
    }

    /**
     * Build registry from both @Operation methods and @OperationMappings factories.
     */
    private Map<String, OperationImplementation> buildRegistry() {
        Map<String, OperationImplementation> registry = new HashMap<>();

        // Scan for @Operation methods (existing logic)
        registry.putAll(scanOperationMethods());

        // NEW: Scan for @OperationMappings factory methods
        registry.putAll(scanMappingFactories());

        return Collections.unmodifiableMap(registry);
    }

    /**
     * Scan for @Operation annotated methods (existing).
     */
    private Map<String, OperationImplementation> scanOperationMethods() {
        Map<String, OperationImplementation> registry = new HashMap<>();

        for (Method method : this.getClass().getDeclaredMethods()) {
            Operation annotation = method.getAnnotation(Operation.class);
            if (annotation != null) {
                String operationName = annotation.value();
                validateMethodSignature(method, operationName);
                method.setAccessible(true);
                OperationMethod opMethod = createOperationMethod(method);
                registry.put(operationName, opMethod);
            }
        }

        return registry;
    }

    /**
     * NEW: Scan for @OperationMappings factory methods.
     */
    private Map<String, OperationImplementation> scanMappingFactories() {
        Map<String, OperationImplementation> registry = new HashMap<>();

        for (Method method : this.getClass().getDeclaredMethods()) {
            OperationMappings annotation = method.getAnnotation(OperationMappings.class);
            if (annotation != null) {
                validateMappingFactorySignature(method);
                method.setAccessible(true);

                try {
                    // Invoke factory method to get Stream<NamedMapping>
                    @SuppressWarnings("unchecked")
                    Stream<NamedMapping> mappings = (Stream<NamedMapping>) method.invoke(this);

                    // Register each mapping
                    mappings.forEach(mapping -> {
                        if (registry.containsKey(mapping.operationName())) {
                            throw new IllegalStateException(
                                String.format("Duplicate operation: %s in %s",
                                    mapping.operationName(), this.getClass().getSimpleName())
                            );
                        }
                        registry.put(mapping.operationName(), mapping.implementation());
                    });

                } catch (Exception e) {
                    throw new RuntimeException(
                        String.format("Error invoking @OperationMappings method: %s.%s",
                            this.getClass().getSimpleName(), method.getName()),
                        e
                    );
                }
            }
        }

        return registry;
    }

    /**
     * Validate @OperationMappings method signature.
     */
    private void validateMappingFactorySignature(Method method) {
        // Must return Stream<NamedMapping>
        if (!Stream.class.equals(method.getReturnType())) {
            throw new IllegalStateException(
                String.format("@OperationMappings method must return Stream<NamedMapping>: %s.%s",
                    this.getClass().getSimpleName(), method.getName())
            );
        }

        // Must have no parameters
        if (method.getParameterCount() != 0) {
            throw new IllegalStateException(
                String.format("@OperationMappings method must have no parameters: %s.%s",
                    this.getClass().getSimpleName(), method.getName())
            );
        }
    }

    // ... rest of existing implementation (handleOperation, etc.)
}
```

---

## Usage Examples

### Example 1: Simple String Operations

```java
import static com.example.fhirpath.codegen.spark.handler.MappingBuilder.*;
import static org.apache.spark.sql.functions.*;

public class StringHandler extends AnnotatedTypeHandler {

    @OperationMappings
    public Stream<NamedMapping> basicStringOps() {
        return Stream.of(
            // Unary operations
            direct("upper", str -> upper(str)),
            direct("lower", str -> lower(str)),
            direct("trim", str -> trim(str)),

            // Binary operations
            binary("concat", (left, right) -> concat(left, right)),
            binary("startsWith", (str, prefix) -> str.startsWith(prefix)),
            binary("endsWith", (str, suffix) -> str.endsWith(suffix)),
            binary("contains", (str, substr) -> str.contains(substr)),

            // Ternary operations
            ternary("replace", (str, pattern, replacement) ->
                regexp_replace(str, pattern, replacement))
        );
    }

    // Complex operation still uses @Operation
    @Operation("substring")
    public Column substring(Column str, Column pos, Column len) {
        // Complex null handling + bounds checking
        Column adjustedPos = pos.plus(lit(1)); // 0-based → 1-based
        Column safeLen = coalesce(len, lit(Integer.MAX_VALUE));

        Column nullCondition = str.isNull()
            .or(pos.isNull())
            .or(pos.leq(0))
            .or(pos.gt(length(str)));

        return when(not(nullCondition), substr(str, adjustedPos, safeLen));
    }

    @Operation("matches")
    public Column matches(Column str, Column pattern) {
        // Uses dynamic pattern (column, not literal)
        return functions.call_function("regexp_like", str, pattern);
    }
}
```

### Example 2: Numeric Operations

```java
import static com.example.fhirpath.codegen.spark.handler.MappingBuilder.*;
import static org.apache.spark.sql.functions.*;

public class IntegerHandler extends AnnotatedTypeHandler {

    @OperationMappings
    public Stream<NamedMapping> arithmeticOps() {
        return Stream.of(
            binary("add", (a, b) -> a.plus(b)),
            binary("sub", (a, b) -> a.minus(b)),
            binary("multiply", (a, b) -> a.multiply(b)),
            binary("divide", (a, b) -> a.divide(b)),
            binary("mod", (a, b) -> a.mod(b))
        );
    }

    @OperationMappings
    public Stream<NamedMapping> comparisonOps() {
        return Stream.of(
            binary("gt", (a, b) -> a.gt(b)),
            binary("lt", (a, b) -> a.lt(b)),
            binary("geq", (a, b) -> a.geq(b)),
            binary("leq", (a, b) -> a.leq(b))
        );
    }

    @OperationMappings
    public Stream<NamedMapping> mathOps() {
        return Stream.of(
            direct("abs", n -> abs(n)),
            direct("ceiling", n -> ceil(n)),
            direct("floor", n -> floor(n)),
            direct("sqrt", n -> sqrt(n)),
            direct("exp", n -> exp(n)),
            direct("ln", n -> log(n))
        );
    }

    @Operation("truncate")
    public Column truncate(Column n) {
        // Truncate towards zero (different from floor/ceiling)
        return when(n.geq(lit(0)), floor(n)).otherwise(ceil(n));
    }
}
```

### Example 3: Programmatic Generation

```java
import static com.example.fhirpath.codegen.spark.handler.MappingBuilder.*;

public class QuantityHandler extends AnnotatedTypeHandler {

    @OperationMappings
    public Stream<NamedMapping> quantityMathOps() {
        // Generate multiple operations that apply function to quantity.value field
        return MappingBuilder.generate(
            List.of("abs", "ceiling", "floor"),
            opName -> unary(opName, quantity ->
                applyToQuantityValue(quantity, v -> {
                    return switch (opName) {
                        case "abs" -> abs(v);
                        case "ceiling" -> ceil(v);
                        case "floor" -> floor(v);
                        default -> throw new IllegalStateException();
                    };
                })
            )
        );
    }

    @OperationMappings
    public Stream<NamedMapping> comparisonOps() {
        // All comparison operations have same pattern with UDF fallback
        return Stream.of("gt", "lt", "geq", "leq").map(opName ->
            complexBinary(opName, (left, right, ctx) ->
                ctx.udfRegistry().isRegistered("quantityCompare")
                    ? compareViaUdf(left, right, opName)
                    : compareSimple(left, right, opName)
            )
        );
    }

    // Complex operations with custom logic
    @Operation("add")
    public Column add(Column left, Column right, CodeGenContext ctx) {
        return ctx.udfRegistry().isRegistered("quantityAdd")
            ? functions.call_function("quantityAdd", left, right)
            : simpleAdd(left, right);
    }

    @Operation("multiply")
    public Column multiply(Column left, Column right) {
        // Complex: unit multiplication (m * m = m²)
        return struct(
            left.getField("value").multiply(right.getField("value")).alias("value"),
            concat(left.getField("unit"), lit("*"), right.getField("unit")).alias("unit"),
            left.getField("system").alias("system"),
            left.getField("code").alias("code")
        );
    }

    // Helper methods
    private Column applyToQuantityValue(Column quantity, Function<Column, Column> fn) {
        return struct(
            fn.apply(quantity.getField("value")).alias("value"),
            quantity.getField("unit").alias("unit"),
            quantity.getField("system").alias("system"),
            quantity.getField("code").alias("code")
        );
    }

    private Column compareViaUdf(Column left, Column right, String op) {
        Column result = functions.call_function("quantityCompare", left, right);
        return switch (op) {
            case "gt" -> result.gt(lit(0));
            case "lt" -> result.lt(lit(0));
            case "geq" -> result.geq(lit(0));
            case "leq" -> result.leq(lit(0));
            default -> throw new IllegalArgumentException("Unknown op: " + op);
        };
    }

    private Column compareSimple(Column left, Column right, String op) {
        Column leftVal = left.getField("value");
        Column rightVal = right.getField("value");
        return switch (op) {
            case "gt" -> leftVal.gt(rightVal);
            case "lt" -> leftVal.lt(rightVal);
            case "geq" -> leftVal.geq(rightVal);
            case "leq" -> leftVal.leq(rightVal);
            default -> throw new IllegalArgumentException("Unknown op: " + op);
        };
    }

    private Column simpleAdd(Column left, Column right) {
        return struct(
            left.getField("value").plus(right.getField("value")).alias("value"),
            left.getField("unit").alias("unit"),
            left.getField("system").alias("system"),
            left.getField("code").alias("code")
        );
    }
}
```

### Example 4: Mixed Approaches

```java
import static com.example.fhirpath.codegen.spark.handler.MappingBuilder.*;

public class DateTimeHandler extends AnnotatedTypeHandler {

    // Simple operations via factory
    @OperationMappings
    public Stream<NamedMapping> simpleComparisons() {
        return Stream.of(
            // When no UDF needed, just direct comparison
            binary("gt", (a, b) -> a.gt(b)),
            binary("lt", (a, b) -> a.lt(b)),
            binary("geq", (a, b) -> a.geq(b)),
            binary("leq", (a, b) -> a.leq(b))
        );
    }

    // Complex operation requiring UDF and precision handling
    @Operation("add")
    public Column addQuantity(Column dateTime, Column quantity, CodeGenContext ctx) {
        // Adding duration to datetime requires:
        // 1. Converting quantity unit to time unit (days, hours, etc.)
        // 2. Handling datetime precision
        // 3. Calendar arithmetic

        if (ctx.udfRegistry().isRegistered("dateTimeAdd")) {
            return functions.call_function("dateTimeAdd", dateTime, quantity);
        }

        // Fallback: simple addition (assumes quantity in seconds)
        return dateTime.plus(quantity.getField("value"));
    }

    @Operation("sub")
    public Column subtractQuantity(Column dateTime, Column quantity, CodeGenContext ctx) {
        if (ctx.udfRegistry().isRegistered("dateTimeSubtract")) {
            return functions.call_function("dateTimeSubtract", dateTime, quantity);
        }

        return dateTime.minus(quantity.getField("value"));
    }

    // Even simpler: UDF-based operations
    @OperationMappings
    public Stream<NamedMapping> udfOperations() {
        return Stream.of(
            udf("toYear", "extractYear"),
            udf("toMonth", "extractMonth"),
            udf("toDay", "extractDay")
        );
    }
}
```

---

## Complete Handler Examples

### Example: Comprehensive QuantityHandler

```java
package com.example.fhirpath.codegen.spark.handler;

import com.example.fhirpath.codegen.spark.CodeGenContext;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.functions;
import jakarta.annotation.Nonnull;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

import static com.example.fhirpath.codegen.spark.handler.MappingBuilder.*;
import static org.apache.spark.sql.functions.*;

/**
 * Complete Quantity handler using mixed approach:
 * - @OperationMappings for simple operations
 * - @Operation for complex operations
 */
public class QuantityHandler extends AnnotatedTypeHandler {

    // ========== Simple Math Operations (Declarative) ==========

    @OperationMappings("Basic math operations applied to quantity value")
    public Stream<NamedMapping> basicMathOps() {
        return Stream.of(
            unary("abs", this::applyAbs),
            unary("ceiling", this::applyCeiling),
            unary("floor", this::applyFloor),
            unary("truncate", this::applyTruncate),
            unary("negate", this::applyNegate)
        );
    }

    @OperationMappings("Comparison operations with UDF fallback")
    public Stream<NamedMapping> comparisonOps() {
        return Stream.of("gt", "lt", "geq", "leq").map(opName ->
            complexBinary(opName, (left, right, ctx) ->
                ctx.udfRegistry().isRegistered("quantityCompare")
                    ? compareViaUdf(left, right, opName, ctx)
                    : compareSimple(left, right, opName)
            )
        );
    }

    // ========== Complex Arithmetic (Full Methods) ==========

    @Operation("add")
    public Column add(Column left, Column right, CodeGenContext ctx) {
        // Addition requires unit conversion
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
    public Column multiply(Column left, Column right, CodeGenContext ctx) {
        // Unit multiplication: kg * m = kg⋅m
        if (ctx.udfRegistry().isRegistered("quantityMultiply")) {
            return functions.call_function("quantityMultiply", left, right);
        }

        // Fallback: simple concatenation
        return struct(
            left.getField("value").multiply(right.getField("value")).alias("value"),
            concat(left.getField("unit"), lit("*"), right.getField("unit")).alias("unit"),
            left.getField("system").alias("system"),
            left.getField("code").alias("code")
        );
    }

    @Operation("divide")
    public Column divide(Column left, Column right, CodeGenContext ctx) {
        // Unit division: kg / m = kg/m
        if (ctx.udfRegistry().isRegistered("quantityDivide")) {
            return functions.call_function("quantityDivide", left, right);
        }

        return struct(
            left.getField("value").divide(right.getField("value")).alias("value"),
            concat(left.getField("unit"), lit("/"), right.getField("unit")).alias("unit"),
            left.getField("system").alias("system"),
            left.getField("code").alias("code")
        );
    }

    // ========== Helper Methods ==========

    private Column applyAbs(Column quantity) {
        return applyToValue(quantity, functions::abs);
    }

    private Column applyCeiling(Column quantity) {
        return applyToValue(quantity, functions::ceil);
    }

    private Column applyFloor(Column quantity) {
        return applyToValue(quantity, functions::floor);
    }

    private Column applyTruncate(Column quantity) {
        return applyToValue(quantity, v ->
            when(v.geq(lit(0)), floor(v)).otherwise(ceil(v))
        );
    }

    private Column applyNegate(Column quantity) {
        return applyToValue(quantity, v -> v.multiply(lit(-1)));
    }

    /**
     * Apply function to quantity value, preserving unit/system/code.
     */
    private Column applyToValue(Column quantity, Function<Column, Column> valueFn) {
        return struct(
            valueFn.apply(quantity.getField("value")).alias("value"),
            quantity.getField("unit").alias("unit"),
            quantity.getField("system").alias("system"),
            quantity.getField("code").alias("code")
        );
    }

    private Column compareViaUdf(Column left, Column right, String op, CodeGenContext ctx) {
        Column result = functions.call_function("quantityCompare", left, right);
        return switch (op) {
            case "gt" -> result.gt(lit(0));
            case "lt" -> result.lt(lit(0));
            case "geq" -> result.geq(lit(0));
            case "leq" -> result.leq(lit(0));
            default -> throw new IllegalArgumentException("Unknown comparison: " + op);
        };
    }

    private Column compareSimple(Column left, Column right, String op) {
        // Assumes same units (no conversion)
        Column leftVal = left.getField("value");
        Column rightVal = right.getField("value");

        return switch (op) {
            case "gt" -> leftVal.gt(rightVal);
            case "lt" -> leftVal.lt(rightVal);
            case "geq" -> leftVal.geq(rightVal);
            case "leq" -> leftVal.leq(rightVal);
            default -> throw new IllegalArgumentException("Unknown comparison: " + op);
        };
    }

    private Column simpleAdd(Column left, Column right) {
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
}
```

**Summary**:
- 5 simple operations → `@OperationMappings` (15 lines)
- 4 comparison operations → `@OperationMappings` with programmatic generation (5 lines)
- 4 complex arithmetic → `@Operation` methods (60 lines)
- **Total**: ~120 lines (vs ~180 with all @Operation)

---

## Benefits & Trade-offs

### Benefits

#### 1. **Conciseness**
```java
// Before: 4 separate methods (~40 lines)
@Operation("abs")
public Column abs(Column q) { return applyToValue(q, functions::abs); }

@Operation("ceiling")
public Column ceiling(Column q) { return applyToValue(q, functions::ceil); }

@Operation("floor")
public Column floor(Column q) { return applyToValue(q, functions::floor); }

@Operation("negate")
public Column negate(Column q) { return applyToValue(q, v -> v.multiply(lit(-1))); }

// After: 1 factory method (~8 lines)
@OperationMappings
public Stream<NamedMapping> mathOps() {
    return Stream.of(
        unary("abs", q -> applyToValue(q, functions::abs)),
        unary("ceiling", q -> applyToValue(q, functions::ceil)),
        unary("floor", q -> applyToValue(q, functions::floor)),
        unary("negate", q -> applyToValue(q, v -> v.multiply(lit(-1))))
    );
}
```

#### 2. **Logical Grouping**
Related operations declared together:
```java
@OperationMappings("Arithmetic operations")
public Stream<NamedMapping> arithmetic() { ... }

@OperationMappings("Comparison operations")
public Stream<NamedMapping> comparisons() { ... }

@OperationMappings("Math functions")
public Stream<NamedMapping> math() { ... }
```

#### 3. **Programmatic Generation**
```java
@OperationMappings
public Stream<NamedMapping> allComparisons() {
    return Stream.of("gt", "lt", "geq", "leq", "eq", "neq")
        .map(op -> binary(op, (a, b) -> compare(a, b, op)));
}
```

#### 4. **Clear Separation of Concerns**
- **Factory methods** → Simple, declarative mappings
- **@Operation methods** → Complex logic requiring full method

#### 5. **Still Type-Safe**
```java
// Compile error if lambda has wrong signature
unary("bad", (a, b) -> a.plus(b))  // ❌ Compile error: Function<Column,Column> expected
binary("good", (a, b) -> a.plus(b)) // ✅ Correct
```

### Trade-offs

#### 1. **Two Ways to Do Things**
- **Mitigation**: Clear guidelines
  - Simple operations (1-2 lines) → `@OperationMappings`
  - Complex operations (UDF selection, multi-step logic) → `@Operation`

#### 2. **Factory Method Invocation Cost**
- **Cost**: Method invoked once at handler construction
- **Impact**: Negligible (one-time initialization)

#### 3. **Slightly More Complex Base Class**
- **Cost**: `AnnotatedTypeHandler` scans two annotation types
- **Impact**: ~50 additional lines in base class
- **Benefit**: Unlimited handlers benefit from this

---

## Comparison

### Code Size Comparison: QuantityHandler

| Approach | LOC | Methods | Factories |
|----------|-----|---------|-----------|
| All @Operation | ~220 | 15 | 0 |
| Mixed (proposed) | ~150 | 6 | 3 |
| **Reduction** | **-32%** | **-60%** | **+3** |

### Readability Score (subjective)

| Aspect | All @Operation | Mixed Approach |
|--------|----------------|----------------|
| Simple ops | 3/5 (verbose) | 5/5 (concise) |
| Complex ops | 5/5 (clear) | 5/5 (unchanged) |
| Grouping | 2/5 (scattered) | 5/5 (logical) |
| **Overall** | **3.3/5** | **5/5** |

---

## Migration Path

### Phase 1: Add @OperationMappings Support
1. Create `@OperationMappings` annotation
2. Create `NamedMapping` record
3. Create `MappingBuilder` utility
4. Extend `AnnotatedTypeHandler.buildRegistry()` to scan factory methods
5. Add validation for factory method signatures

**Effort**: 3-4 hours

### Phase 2: Refactor Existing Handlers
1. Identify simple operations (candidates for factory methods)
2. Extract to `@OperationMappings` methods
3. Keep complex operations as `@Operation` methods
4. Test thoroughly

**Effort**: 2-3 hours per handler

### Phase 3: Validate & Document
1. Verify all operations still work
2. Update documentation with examples
3. Create guidelines for when to use each approach

**Effort**: 2-3 hours

**Total**: 10-15 hours

---

## Recommendations

### When to Use @OperationMappings

✅ **Use for**:
- Simple 1-liner operations
- Operations with identical patterns (comparisons, math functions)
- Programmatically generated operations
- Grouping related operations

### When to Use @Operation

✅ **Use for**:
- Complex multi-step logic
- Operations requiring extensive comments
- Operations with conditional UDF selection
- Operations that benefit from named helper methods

### Example Decision Matrix

| Operation | Complexity | Choice | Reason |
|-----------|-----------|--------|--------|
| abs | Simple | Factory | 1-liner, just wraps Spark function |
| add | Complex | @Operation | UDF selection, unit conversion |
| gt | Medium | Factory | Pattern repeats for all comparisons |
| substring | Complex | @Operation | Multi-step logic, null handling |
| negate | Simple | Factory | 1-liner transform |

---

## Conclusion

**@OperationMappings provides**:
- ✅ 30-40% code reduction for simple operations
- ✅ Better logical organization
- ✅ Programmatic generation capabilities
- ✅ Maintains type safety
- ✅ Clear separation: simple vs complex

**Recommended adoption**:
- Use both `@Operation` and `@OperationMappings`
- Factory methods for simple operations
- Full methods for complex logic
- Clear guidelines prevent confusion

**Next Steps**:
1. Implement `@OperationMappings` infrastructure
2. Refactor QuantityHandler as proof of concept
3. Apply pattern to remaining handlers
