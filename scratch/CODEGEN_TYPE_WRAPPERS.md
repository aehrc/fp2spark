# Type Wrapper Classes: Design Rationale and Usage

**Date**: 2025-01-22
**Extension**: Builds on refactoring proposals
**Purpose**: Determine when type wrappers add value vs unnecessary overhead

---

## Table of Contents

1. [Current State Analysis](#current-state-analysis)
2. [Use Cases for Type Wrappers](#use-cases-for-type-wrappers)
3. [Anti-Patterns & Overhead](#anti-patterns--overhead)
4. [Recommended Approach](#recommended-approach)
5. [Implementation Examples](#implementation-examples)
6. [Complete Wrapper Designs](#complete-wrapper-designs)

---

## Current State Analysis

### Existing Wrappers

**Current implementation** (mostly stubs):

```java
// Quantity.java (~80 lines, 94% stubs)
public record Quantity(@Nonnull Column target) {

    public Column value() { return target.getField("value"); }
    public Column unit() { return target.getField("unit"); }
    public Column system() { return target.getField("system"); }
    public Column code() { return target.getField("code"); }

    // Only implementation
    @Nonnull
    public Column abs() {
        return functions.struct(
            functions.abs(value()).alias("value"),
            unit().alias("unit"),
            system().alias("system"),
            code().alias("code")
        );
    }

    // All stubs
    public Column plus(Quantity q) { throw new UnsupportedOperationException(); }
    public Column gt(Quantity q) { throw new UnsupportedOperationException(); }
    // ... 14 more stubs
}

// DateTime.java (~40 lines, 100% stubs)
public record DateTime(@Nonnull Column target) {
    public Column gt(DateTime dt) { throw new UnsupportedOperationException(); }
    public Column plus(Quantity q) { throw new UnsupportedOperationException(); }
    // ... all stubs
}
```

### Problem: Are These Useful?

In the **refactored architecture** with TypeHandlers:
- Operations implemented in `QuantityHandler`
- Do we still need `Quantity` wrapper class?
- Or is it just extra overhead?

---

## Use Cases for Type Wrappers

### Use Case 1: Field Access Abstraction ✅ HIGH VALUE

**Problem**: Complex types have structured fields requiring repetitive access

**Without wrapper**:
```java
// In QuantityHandler methods - repeated field access
public Column abs(Column quantity) {
    return struct(
        functions.abs(quantity.getField("value")).alias("value"),
        quantity.getField("unit").alias("unit"),
        quantity.getField("system").alias("system"),
        quantity.getField("code").alias("code")
    );
}

public Column ceiling(Column quantity) {
    return struct(
        functions.ceil(quantity.getField("value")).alias("value"),
        quantity.getField("unit").alias("unit"),    // Repetition!
        quantity.getField("system").alias("system"), // Repetition!
        quantity.getField("code").alias("code")      // Repetition!
    );
}

// Easy to make mistakes:
quantity.getField("vale")  // Typo! Runtime error
```

**With wrapper**:
```java
public Column abs(Column quantity) {
    return Quantity.of(quantity).mapValue(functions::abs).toColumn();
}

public Column ceiling(Column quantity) {
    return Quantity.of(quantity).mapValue(functions::ceil).toColumn();
}

// Type-safe field access:
Quantity q = Quantity.of(column);
Column value = q.value();   // Safe!
Column unit = q.unit();     // Safe!
// q.vale()  // Compile error!
```

**Benefits**:
- ✅ Type-safe field access
- ✅ Eliminates repetitive `getField()` calls
- ✅ Prevents typos (compile-time vs runtime errors)
- ✅ Single source of truth for field names

### Use Case 2: Struct Building ✅ HIGH VALUE

**Problem**: Creating struct columns requires verbose, error-prone code

**Without wrapper**:
```java
// Building Quantity struct - 4 lines every time
private Column createQuantity(Column value, Column unit, Column system, Column code) {
    return functions.struct(
        value.alias("value"),
        unit.alias("unit"),
        system.alias("system"),
        code.alias("code")
    );
}

// Used everywhere:
public Column abs(Column quantity) {
    Column absValue = functions.abs(quantity.getField("value"));
    return createQuantity(absValue,
                         quantity.getField("unit"),
                         quantity.getField("system"),
                         quantity.getField("code"));
}

// Easy to make mistakes:
return functions.struct(
    value.alias("value"),
    unit.alias("unt"),      // Typo! Wrong field name
    system.alias("system"),
    code.alias("code")
);
```

**With wrapper**:
```java
public Column abs(Column quantity) {
    Quantity q = Quantity.of(quantity);
    return Quantity.build(
        functions.abs(q.value()),
        q.unit(),
        q.system(),
        q.code()
    );
}

// Or even better with mapValue:
public Column abs(Column quantity) {
    return Quantity.of(quantity).mapValue(functions::abs).toColumn();
}
```

**Benefits**:
- ✅ Consistent struct creation
- ✅ Correct field naming guaranteed
- ✅ Reduces boilerplate from 4 lines to 1
- ✅ Centralized validation

### Use Case 3: Common Transformations ✅ MEDIUM-HIGH VALUE

**Problem**: Many operations apply function to single field, preserve others

**Without wrapper**:
```java
// Pattern repeated 10+ times in QuantityHandler
private Column applyToValue(Column quantity, Function<Column, Column> fn) {
    return functions.struct(
        fn.apply(quantity.getField("value")).alias("value"),
        quantity.getField("unit").alias("unit"),
        quantity.getField("system").alias("system"),
        quantity.getField("code").alias("code")
    );
}

// Usage:
public Column abs(Column q) { return applyToValue(q, functions::abs); }
public Column ceiling(Column q) { return applyToValue(q, functions::ceil); }
public Column floor(Column q) { return applyToValue(q, functions::floor); }
public Column negate(Column q) { return applyToValue(q, v -> v.multiply(lit(-1))); }
```

**With wrapper**:
```java
public class Quantity {
    public Quantity mapValue(Function<Column, Column> fn) {
        return new Quantity(
            Quantity.build(
                fn.apply(value()),
                unit(),
                system(),
                code()
            )
        );
    }
}

// Usage - much cleaner:
public Column abs(Column q) {
    return Quantity.of(q).mapValue(functions::abs).toColumn();
}

public Column ceiling(Column q) {
    return Quantity.of(q).mapValue(functions::ceil).toColumn();
}

public Column negate(Column q) {
    return Quantity.of(q).mapValue(v -> v.multiply(lit(-1))).toColumn();
}
```

**Benefits**:
- ✅ Eliminates helper method boilerplate
- ✅ Functional programming style
- ✅ Reusable across all value transformations
- ✅ Clear intent: "transform the value, preserve metadata"

### Use Case 4: Type Safety ✅ MEDIUM VALUE

**Problem**: Column is generic, easy to mix up types

**Without wrapper**:
```java
public Column compareQuantities(Column left, Column right) {
    // Both are just Column - could accidentally pass DateTime!
    // No compile-time check
    return left.getField("value").gt(right.getField("value"));
}

// Accidental misuse:
Column datetime = ...;
Column quantity = ...;
compareQuantities(datetime, quantity);  // Compiles! Runtime error
```

**With wrapper**:
```java
public Column compareQuantities(Quantity left, Quantity right) {
    // Type-safe: must be Quantity
    return left.value().gt(right.value());
}

// Misuse caught at compile time:
DateTime datetime = DateTime.of(...);
Quantity quantity = Quantity.of(...);
compareQuantities(datetime, quantity);  // ❌ Compile error!
```

**Benefits**:
- ✅ Compile-time type checking
- ✅ Better IDE support (autocomplete, refactoring)
- ✅ Self-documenting code
- ✅ Prevents runtime errors

### Use Case 5: Null Handling & Validation ✅ MEDIUM VALUE

**Problem**: Complex types may have invariants or common null patterns

**Without wrapper**:
```java
// Repeated null handling in multiple operations
public Column add(Column left, Column right) {
    // Handle null Quantities
    Column leftValue = when(left.isNull(), lit(null))
        .otherwise(left.getField("value"));
    Column rightValue = when(right.isNull(), lit(null))
        .otherwise(right.getField("value"));
    // ... complex logic
}

// Validation scattered across operations
private void validateQuantityNotNull(Column q) {
    // Each operation re-implements
}
```

**With wrapper**:
```java
public class Quantity {
    public static Quantity ofNullable(Column column) {
        // Centralized null handling
        return column == null ? NULL_QUANTITY : new Quantity(column);
    }

    public boolean isNull() {
        return target.isNull();
    }

    public Column valueOrDefault(Object defaultValue) {
        return when(target.isNull(), lit(defaultValue))
            .otherwise(value());
    }
}

// Usage:
public Column add(Column left, Column right) {
    Quantity leftQ = Quantity.ofNullable(left);
    Quantity rightQ = Quantity.ofNullable(right);

    if (leftQ.isNull() || rightQ.isNull()) {
        return lit(null);
    }

    return Quantity.build(
        leftQ.value().plus(rightQ.value()),
        leftQ.unit(),
        leftQ.system(),
        leftQ.code()
    );
}
```

**Benefits**:
- ✅ Centralized null handling
- ✅ Consistent validation
- ✅ Reduces duplicate code
- ✅ Easier to maintain

### Use Case 6: Fluent API (Optional) ⚠️ LOW-MEDIUM VALUE

**Problem**: Method chaining can improve readability

**Traditional**:
```java
Column result = add(multiply(abs(quantity), scalar), offset);
```

**With fluent wrapper**:
```java
Quantity result = Quantity.of(quantity)
    .abs()
    .multiply(scalar)
    .add(offset);
Column column = result.toColumn();
```

**Benefits**:
- ⚠️ More readable for some developers
- ⚠️ Better for complex transformations
- ❌ But: adds complexity, not idiomatic for Spark
- ❌ Mixing Column and Quantity types can be confusing

**Recommendation**: Skip fluent API, use wrappers as utilities only.

---

## Anti-Patterns & Overhead

### Anti-Pattern 1: Operation Methods in Wrappers ❌

**Problem**: Duplicating handler logic in wrapper

```java
// ❌ BAD: Wrapper contains operation logic
public class Quantity {
    public Column plus(Quantity other) {
        // UDF selection logic duplicated from QuantityHandler!
        return ctx.udfRegistry().isRegistered("quantityAdd")
            ? functions.call_function("quantityAdd", this.target, other.target)
            : simplePlus(other);
    }

    public Column gt(Quantity other) {
        // Comparison logic duplicated!
    }
}

// QuantityHandler now delegates to wrapper
public class QuantityHandler {
    @Operation("add")
    public Column add(Column left, Column right, CodeGenContext ctx) {
        return Quantity.of(left).plus(Quantity.of(right));  // Just delegates!
    }
}
```

**Issues**:
- ❌ Two sources of truth (wrapper + handler)
- ❌ Wrapper needs CodeGenContext (breaks encapsulation)
- ❌ Harder to test
- ❌ Unclear ownership

**Better approach**: Wrappers are **utilities**, handlers contain **logic**

### Anti-Pattern 2: Wrapper Factory Overload ❌

**Problem**: Too many factory methods, unclear which to use

```java
// ❌ BAD: Too many ways to create
public class Quantity {
    public static Quantity of(Column column) { ... }
    public static Quantity from(Column column) { ... }
    public static Quantity wrap(Column column) { ... }
    public static Quantity create(Column value, Column unit) { ... }
    public static Quantity build(Column value, Column unit, Column system, Column code) { ... }
    // Which one to use???
}
```

**Better approach**: Minimal, clear API

```java
// ✅ GOOD: Clear, minimal API
public class Quantity {
    // Wrap existing column
    public static Quantity of(Column column) { ... }

    // Build new struct
    public static Column build(Column value, Column unit, Column system, Column code) { ... }
}
```

### Anti-Pattern 3: Wrapper Explosion ❌

**Problem**: Creating wrapper for every type

```java
// ❌ BAD: Wrappers for simple types
public class IntegerWrapper {
    private final Column target;

    public Column abs() { return functions.abs(target); }
    public Column negate() { return target.multiply(lit(-1)); }
}

// Unnecessary! Integer operations are already simple
```

**Better approach**: Only wrap **complex structured types**

---

## Recommended Approach

### Rule 1: Wrap Complex Structured Types Only

**Wrap these**:
- ✅ **Quantity** - 4 fields (value, unit, system, code)
- ✅ **CodeableConcept** - structured (coding[], text)
- ✅ **Identifier** - structured (system, value, use, type)
- ✅ **Period** - structured (start, end)

**Don't wrap these**:
- ❌ **Integer, Decimal, String, Boolean** - primitives, no structure
- ❌ **Date, Time, DateTime** - single timestamp value (unless complex precision metadata)

### Rule 2: Wrappers are Utilities, Not Operation Containers

**Wrappers provide**:
- ✅ Field access: `quantity.value()`, `quantity.unit()`
- ✅ Struct building: `Quantity.build(value, unit, system, code)`
- ✅ Common transformations: `quantity.mapValue(fn)`
- ✅ Null handling: `Quantity.ofNullable(column)`

**Handlers provide**:
- ✅ Operation logic: `add()`, `multiply()`, `gt()`
- ✅ UDF selection
- ✅ Complex algorithms

### Rule 3: Immutable, Functional Style

**Wrappers should be**:
- ✅ Immutable (records)
- ✅ Pure functions (no side effects)
- ✅ Composable (functional transformations)
- ❌ No mutable state
- ❌ No external dependencies (like UdfRegistry)

### Rule 4: Minimal API Surface

**Each wrapper should have**:
- 1 primary factory: `of(Column)`
- 1 struct builder: `build(...fields)`
- Field accessors: `value()`, `unit()`, etc.
- 1-3 common transformations: `mapValue(fn)`, `mapField(name, fn)`
- 1 converter: `toColumn()`

**Avoid**:
- ❌ Multiple factory methods (confusing)
- ❌ Operation methods (belongs in handler)
- ❌ Fluent operation chains (too complex)

---

## Implementation Examples

### Example 1: Quantity Wrapper (Recommended Design)

```java
package com.example.fhirpath.codegen.spark.wrapper;

import org.apache.spark.sql.Column;
import org.apache.spark.sql.functions;
import jakarta.annotation.Nonnull;

import java.util.function.Function;

/**
 * Wrapper for FHIRPath Quantity struct: {value, unit, system, code}.
 *
 * Provides type-safe field access and common transformations.
 * Does NOT contain operation logic - that belongs in QuantityHandler.
 *
 * Usage:
 * <pre>
 * // Field access
 * Quantity q = Quantity.of(column);
 * Column value = q.value();
 * Column unit = q.unit();
 *
 * // Struct building
 * Column newQuantity = Quantity.build(value, unit, system, code);
 *
 * // Common transformations
 * Column absQuantity = Quantity.of(column)
 *     .mapValue(functions::abs)
 *     .toColumn();
 * </pre>
 */
public record Quantity(@Nonnull Column target) {

    // ========== Factory Methods ==========

    /**
     * Wrap existing Quantity column.
     */
    @Nonnull
    public static Quantity of(@Nonnull Column column) {
        return new Quantity(column);
    }

    /**
     * Build new Quantity struct from components.
     */
    @Nonnull
    public static Column build(@Nonnull Column value,
                              @Nonnull Column unit,
                              @Nonnull Column system,
                              @Nonnull Column code) {
        return functions.struct(
            value.alias("value"),
            unit.alias("unit"),
            system.alias("system"),
            code.alias("code")
        );
    }

    // ========== Field Accessors ==========

    @Nonnull
    public Column value() {
        return target.getField("value");
    }

    @Nonnull
    public Column unit() {
        return target.getField("unit");
    }

    @Nonnull
    public Column system() {
        return target.getField("system");
    }

    @Nonnull
    public Column code() {
        return target.getField("code");
    }

    // ========== Common Transformations ==========

    /**
     * Apply transformation to value field, preserve metadata.
     *
     * Common pattern for: abs, ceiling, floor, negate, etc.
     */
    @Nonnull
    public Quantity mapValue(@Nonnull Function<Column, Column> fn) {
        return new Quantity(
            build(
                fn.apply(value()),
                unit(),
                system(),
                code()
            )
        );
    }

    /**
     * Apply transformation to specific field.
     */
    @Nonnull
    public Quantity mapField(@Nonnull String fieldName,
                            @Nonnull Function<Column, Column> fn) {
        return switch (fieldName) {
            case "value" -> mapValue(fn);
            case "unit" -> new Quantity(build(value(), fn.apply(unit()), system(), code()));
            case "system" -> new Quantity(build(value(), unit(), fn.apply(system()), code()));
            case "code" -> new Quantity(build(value(), unit(), system(), fn.apply(code())));
            default -> throw new IllegalArgumentException("Unknown field: " + fieldName);
        };
    }

    /**
     * Convert back to Column (for final result).
     */
    @Nonnull
    public Column toColumn() {
        return target;
    }

    /**
     * Combine two quantities with binary operation on values.
     *
     * Assumes same units (validation done in handler).
     */
    @Nonnull
    public Quantity combineValues(@Nonnull Quantity other,
                                  @Nonnull BiFunction<Column, Column, Column> fn) {
        return new Quantity(
            build(
                fn.apply(this.value(), other.value()),
                this.unit(),  // Preserve left unit
                this.system(),
                this.code()
            )
        );
    }
}
```

**Usage in QuantityHandler**:

```java
public class QuantityHandler extends AnnotatedTypeHandler {

    @OperationMappings("Math operations using wrapper")
    public Stream<NamedMapping> mathOps() {
        return Stream.of(
            unary("abs", q -> Quantity.of(q).mapValue(functions::abs).toColumn()),
            unary("ceiling", q -> Quantity.of(q).mapValue(functions::ceil).toColumn()),
            unary("floor", q -> Quantity.of(q).mapValue(functions::floor).toColumn()),
            unary("negate", q -> Quantity.of(q).mapValue(v -> v.multiply(lit(-1))).toColumn())
        );
    }

    @Operation("add")
    public Column add(Column left, Column right, CodeGenContext ctx) {
        // Use wrapper for field access, but operation logic stays here
        if (ctx.udfRegistry().isRegistered("quantityAdd")) {
            return functions.call_function("quantityAdd", left, right);
        }

        // Simple addition using wrapper
        Quantity leftQ = Quantity.of(left);
        Quantity rightQ = Quantity.of(right);

        return Quantity.build(
            leftQ.value().plus(rightQ.value()),
            leftQ.unit(),
            leftQ.system(),
            leftQ.code()
        );
    }
}
```

**Benefits**:
- ✅ Clean separation: wrapper = utilities, handler = logic
- ✅ Reduces boilerplate by ~70%
- ✅ Type-safe field access
- ✅ Reusable transformations

### Example 2: Period Wrapper (Structured Type)

```java
package com.example.fhirpath.codegen.spark.wrapper;

import org.apache.spark.sql.Column;
import org.apache.spark.sql.functions;
import jakarta.annotation.Nonnull;

/**
 * Wrapper for FHIR Period: {start, end}.
 */
public record Period(@Nonnull Column target) {

    @Nonnull
    public static Period of(@Nonnull Column column) {
        return new Period(column);
    }

    @Nonnull
    public static Column build(@Nonnull Column start, @Nonnull Column end) {
        return functions.struct(
            start.alias("start"),
            end.alias("end")
        );
    }

    @Nonnull
    public Column start() {
        return target.getField("start");
    }

    @Nonnull
    public Column end() {
        return target.getField("end");
    }

    @Nonnull
    public Column toColumn() {
        return target;
    }
}
```

### Example 3: No Wrapper for Simple Types

```java
// ❌ DON'T DO THIS - unnecessary wrapper for primitives

public class IntegerWrapper {
    private final Column target;

    public Column abs() { return functions.abs(target); }
    // Adds no value - just use functions.abs(column) directly!
}

// ✅ INSTEAD: Just use Column directly in IntegerHandler

public class IntegerHandler extends AnnotatedTypeHandler {
    @OperationMappings
    public Stream<NamedMapping> mathOps() {
        return Stream.of(
            unary("abs", col -> functions.abs(col)),  // Direct!
            unary("ceiling", col -> functions.ceil(col))
        );
    }
}
```

---

## Complete Wrapper Designs

### Quantity (Full Implementation)

```java
package com.example.fhirpath.codegen.spark.wrapper;

import org.apache.spark.sql.Column;
import org.apache.spark.sql.functions;
import jakarta.annotation.Nonnull;

import java.util.function.BiFunction;
import java.util.function.Function;

import static org.apache.spark.sql.functions.*;

/**
 * Utility wrapper for FHIRPath Quantity: {value, unit, system, code}.
 *
 * Design principles:
 * - Immutable (record)
 * - Pure functions (no side effects)
 * - No operation logic (belongs in QuantityHandler)
 * - Minimal API (factory, accessors, transformations)
 */
public record Quantity(@Nonnull Column target) {

    // ========== Constants ==========

    private static final String FIELD_VALUE = "value";
    private static final String FIELD_UNIT = "unit";
    private static final String FIELD_SYSTEM = "system";
    private static final String FIELD_CODE = "code";

    // ========== Factory Methods ==========

    /**
     * Wrap existing Quantity column.
     */
    @Nonnull
    public static Quantity of(@Nonnull Column column) {
        return new Quantity(column);
    }

    /**
     * Build new Quantity struct.
     *
     * @param value Numeric value
     * @param unit Unit of measure (e.g., "kg", "m")
     * @param system Coding system (e.g., UCUM)
     * @param code Unit code in system
     */
    @Nonnull
    public static Column build(@Nonnull Column value,
                              @Nonnull Column unit,
                              @Nonnull Column system,
                              @Nonnull Column code) {
        return functions.struct(
            value.alias(FIELD_VALUE),
            unit.alias(FIELD_UNIT),
            system.alias(FIELD_SYSTEM),
            code.alias(FIELD_CODE)
        );
    }

    /**
     * Build Quantity with just value and unit (system/code null).
     */
    @Nonnull
    public static Column build(@Nonnull Column value, @Nonnull Column unit) {
        return build(value, unit, lit(null), lit(null));
    }

    // ========== Field Accessors ==========

    @Nonnull
    public Column value() {
        return target.getField(FIELD_VALUE);
    }

    @Nonnull
    public Column unit() {
        return target.getField(FIELD_UNIT);
    }

    @Nonnull
    public Column system() {
        return target.getField(FIELD_SYSTEM);
    }

    @Nonnull
    public Column code() {
        return target.getField(FIELD_CODE);
    }

    // ========== Transformations ==========

    /**
     * Apply function to value, preserve metadata.
     *
     * Example: abs(Quantity) -> Quantity with abs(value)
     */
    @Nonnull
    public Quantity mapValue(@Nonnull Function<Column, Column> fn) {
        return new Quantity(
            build(fn.apply(value()), unit(), system(), code())
        );
    }

    /**
     * Combine two quantities with binary operation on values.
     * Preserves metadata from left quantity.
     *
     * Example: Quantity(5, "kg") + Quantity(3, "kg") -> Quantity(8, "kg")
     *
     * Note: Assumes compatible units (validation in handler).
     */
    @Nonnull
    public Quantity combineValues(@Nonnull Quantity other,
                                  @Nonnull BiFunction<Column, Column, Column> fn) {
        return new Quantity(
            build(
                fn.apply(this.value(), other.value()),
                this.unit(),
                this.system(),
                this.code()
            )
        );
    }

    /**
     * Replace value while preserving metadata.
     */
    @Nonnull
    public Quantity withValue(@Nonnull Column newValue) {
        return new Quantity(
            build(newValue, unit(), system(), code())
        );
    }

    /**
     * Replace unit while preserving value and other metadata.
     */
    @Nonnull
    public Quantity withUnit(@Nonnull Column newUnit) {
        return new Quantity(
            build(value(), newUnit, system(), code())
        );
    }

    /**
     * Convert to Column (unwrap).
     */
    @Nonnull
    public Column toColumn() {
        return target;
    }

    // ========== Utilities ==========

    /**
     * Check if quantity is null.
     */
    @Nonnull
    public Column isNull() {
        return target.isNull();
    }

    /**
     * Check if quantity is not null.
     */
    @Nonnull
    public Column isNotNull() {
        return target.isNotNull();
    }

    /**
     * Null-safe value extraction.
     */
    @Nonnull
    public Column valueOrDefault(@Nonnull Object defaultValue) {
        return when(target.isNotNull(), value())
            .otherwise(lit(defaultValue));
    }
}
```

### Usage Examples

```java
// Example 1: Simple value transformation
Column absQuantity = Quantity.of(quantityColumn)
    .mapValue(functions::abs)
    .toColumn();

// Example 2: Building new quantity
Column newQuantity = Quantity.build(
    lit(5.0),
    lit("kg"),
    lit("http://unitsofmeasure.org"),
    lit("kg")
);

// Example 3: Combining quantities
Quantity left = Quantity.of(leftColumn);
Quantity right = Quantity.of(rightColumn);
Column sum = left.combineValues(right, (a, b) -> a.plus(b)).toColumn();

// Example 4: Field access
Quantity q = Quantity.of(column);
Column value = q.value();
Column unit = q.unit();

// Example 5: Null handling
Column safeValue = Quantity.of(maybeNullColumn)
    .valueOrDefault(0.0);
```

---

## Comparison: With vs Without Wrappers

### Scenario: Implement 5 Math Operations

**Without Wrapper** (~60 lines):
```java
public class QuantityHandler extends AnnotatedTypeHandler {

    @Operation("abs")
    public Column abs(Column quantity) {
        return functions.struct(
            functions.abs(quantity.getField("value")).alias("value"),
            quantity.getField("unit").alias("unit"),
            quantity.getField("system").alias("system"),
            quantity.getField("code").alias("code")
        );
    }

    @Operation("ceiling")
    public Column ceiling(Column quantity) {
        return functions.struct(
            functions.ceil(quantity.getField("value")).alias("value"),
            quantity.getField("unit").alias("unit"),
            quantity.getField("system").alias("system"),
            quantity.getField("code").alias("code")
        );
    }

    @Operation("floor")
    public Column floor(Column quantity) {
        return functions.struct(
            functions.floor(quantity.getField("value")).alias("value"),
            quantity.getField("unit").alias("unit"),
            quantity.getField("system").alias("system"),
            quantity.getField("code").alias("code")
        );
    }

    @Operation("negate")
    public Column negate(Column quantity) {
        Column value = quantity.getField("value").multiply(lit(-1));
        return functions.struct(
            value.alias("value"),
            quantity.getField("unit").alias("unit"),
            quantity.getField("system").alias("system"),
            quantity.getField("code").alias("code")
        );
    }

    @Operation("truncate")
    public Column truncate(Column quantity) {
        Column value = quantity.getField("value");
        Column truncated = when(value.geq(lit(0)), floor(value))
            .otherwise(ceil(value));
        return functions.struct(
            truncated.alias("value"),
            quantity.getField("unit").alias("unit"),
            quantity.getField("system").alias("system"),
            quantity.getField("code").alias("code")
        );
    }
}
```

**With Wrapper** (~15 lines):
```java
public class QuantityHandler extends AnnotatedTypeHandler {

    @OperationMappings
    public Stream<NamedMapping> mathOps() {
        return Stream.of(
            unary("abs", q ->
                Quantity.of(q).mapValue(functions::abs).toColumn()),

            unary("ceiling", q ->
                Quantity.of(q).mapValue(functions::ceil).toColumn()),

            unary("floor", q ->
                Quantity.of(q).mapValue(functions::floor).toColumn()),

            unary("negate", q ->
                Quantity.of(q).mapValue(v -> v.multiply(lit(-1))).toColumn()),

            unary("truncate", q ->
                Quantity.of(q).mapValue(v ->
                    when(v.geq(lit(0)), floor(v)).otherwise(ceil(v))
                ).toColumn())
        );
    }
}
```

**Reduction**: 60 lines → 15 lines (**75% reduction**)

---

## Recommendations

### Recommendation 1: Implement Wrappers for Complex Types Only

**Do create wrappers for**:
- ✅ Quantity (4 fields)
- ✅ CodeableConcept (structured)
- ✅ Identifier (structured)
- ✅ Period (structured)
- ✅ Any FHIR type with 3+ fields

**Don't create wrappers for**:
- ❌ Integer, Decimal, String, Boolean
- ❌ Date, Time, DateTime (unless complex precision)

### Recommendation 2: Keep Wrappers Simple

**Include**:
- ✅ Factory: `of(Column)`
- ✅ Builder: `build(...fields)`
- ✅ Field accessors
- ✅ 1-2 common transformations: `mapValue()`, `combineValues()`
- ✅ Converter: `toColumn()`

**Exclude**:
- ❌ Operation methods (`add()`, `multiply()`)
- ❌ UDF dependencies
- ❌ CodeGenContext dependencies
- ❌ Complex fluent APIs

### Recommendation 3: Clear Ownership

**Wrapper owns**:
- Field access abstraction
- Struct building
- Common transformations (mapValue, etc.)

**Handler owns**:
- Operation logic
- UDF selection
- Complex algorithms

### Recommendation 4: Package Organization

```
com.example.fhirpath.codegen.spark/
  ├── handler/
  │   ├── QuantityHandler.java       (operation logic)
  │   ├── IntegerHandler.java
  │   └── ...
  │
  └── wrapper/                        (utilities)
      ├── Quantity.java               (field access, struct building)
      ├── CodeableConcept.java
      ├── Identifier.java
      └── Period.java
```

---

## Conclusion

**Type wrappers add significant value for complex structured types** when designed as:
- ✅ Immutable utilities
- ✅ Field access abstractions
- ✅ Struct builders
- ✅ Common transformation helpers

**Don't use wrappers as**:
- ❌ Operation containers (use handlers)
- ❌ Fluent API builders (too complex)
- ❌ Wrappers for primitives (unnecessary)

**Recommended implementation**:
1. Create wrapper for Quantity (highest value)
2. Measure code reduction and readability improvement
3. Apply pattern to other complex types if beneficial
4. Keep simple types (Integer, String, etc.) unwrapped

**Expected benefits**:
- 70-80% code reduction for operations on complex types
- Type-safe field access
- Consistent struct building
- Better maintainability
