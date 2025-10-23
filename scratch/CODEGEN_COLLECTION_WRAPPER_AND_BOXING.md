# Collection Wrapper and Automatic Boxing/Unboxing

## The Problem

Collection operations like `count()`, `first()`, `last()` need different SparkSQL expressions based on whether the input is:
- **Singular**: A single value → wrap in array or handle directly
- **Non-singular**: Already an array → use array functions directly

Currently, handlers must manually check cardinality and branch logic everywhere.

## The Solution: Collection Wrapper + Auto Boxing/Unboxing

### Core Ideas

1. **Stateful handlers (Command Pattern)**: Handlers created per invocation with `CodeGenContext` as instance field
2. **Collection wrapper** encapsulates Column + cardinality information
3. **Automatic boxing**: Invocation binder wraps `Column` → `Collection` when calling handlers
4. **Automatic unboxing**: Invocation binder unwraps `Collection` → `Column` from handler results
5. **Handler methods work with domain types**: `Column count(Collection input)` (no context parameter needed)

This is analogous to Java's autoboxing of primitives (`int` ↔ `Integer`).

**Key Advantage of Stateful Handlers**: Since `CodeGenContext` is stored in handler instance state, operation methods don't need it as a parameter - it's available as `this.context`. This makes method signatures even cleaner.

---

## Part 1: Collection Wrapper Design

### Extending EvalHelper Concept

The current `EvalHelper` provides the foundation, but we can make it more powerful:

```java
/**
 * Wrapper for FHIRPath collections that handles singular vs non-singular values.
 * Provides both general utilities (apply, map) and specific operations (count, first, etc.).
 */
public record Collection(@Nonnull Column column, boolean isSingular) {

    // ===== FACTORY METHODS =====

    public static Collection of(Column col, boolean isSingular) {
        return new Collection(col, isSingular);
    }

    public static Collection singular(Column col) {
        return new Collection(col, true);
    }

    public static Collection nonSingular(Column col) {
        return new Collection(col, false);
    }

    // ===== GENERAL UTILITIES =====

    /**
     * Apply different functions based on singularity.
     */
    @Nonnull
    public Column apply(
            Function<Column, Column> arrayFunction,
            Function<Column, Column> singleFunction) {
        return isSingular ? singleFunction.apply(column) : arrayFunction.apply(column);
    }

    /**
     * Apply with null safety - return default if column is null.
     */
    @Nonnull
    public Column applyNonNull(
            Function<Column, Column> arrayFunction,
            Function<Column, Column> singleFunction,
            Column defaultValue) {
        return when(column.isNotNull(), apply(arrayFunction, singleFunction))
                .otherwise(defaultValue);
    }

    /**
     * Convert to array (wraps singular values).
     */
    @Nonnull
    public Column asArray() {
        return applyNonNull(
                Function.identity(),      // Already array
                functions::array,         // Wrap single value
                functions.array()         // Empty array if null
        );
    }

    /**
     * Unwrap to raw column (for unboxing).
     */
    @Nonnull
    public Column toColumn() {
        return column;
    }

    // ===== COLLECTION OPERATIONS (Implementations) =====

    /**
     * Count operation: different implementations for singular vs array.
     */
    @Nonnull
    public Column count() {
        return applyNonNull(
                functions::size,          // Array: size(array)
                col -> lit(1),            // Singular: always 1
                lit(0)                    // Null: 0
        );
    }

    /**
     * First operation: different implementations for singular vs array.
     */
    @Nonnull
    public Column first() {
        return apply(
                col -> col.getItem(0),    // Array: array[0]
                Function.identity()       // Singular: already the value
        );
    }

    /**
     * Last operation: different implementations for singular vs array.
     */
    @Nonnull
    public Column last() {
        return apply(
                col -> functions.element_at(col, -1),  // Array: last element
                Function.identity()                     // Singular: the value
        );
    }

    /**
     * IsEmpty operation.
     */
    @Nonnull
    public Column isEmpty() {
        return applyNonNull(
                col -> functions.size(col).equalTo(0),  // Array: size == 0
                col -> lit(false),                      // Singular: never empty
                lit(true)                               // Null: empty
        );
    }

    /**
     * Exists/NotEmpty operation.
     */
    @Nonnull
    public Column exists() {
        return isEmpty().not();
    }

    /**
     * Single operation: true if collection has exactly one element.
     */
    @Nonnull
    public Column single() {
        return applyNonNull(
                col -> functions.size(col).equalTo(1),  // Array: size == 1
                col -> lit(true),                       // Singular: always true
                lit(false)                              // Null: false
        );
    }

    /**
     * Tail operation: all but first element.
     */
    @Nonnull
    public Collection tail() {
        Column tailCol = apply(
                col -> functions.slice(col, lit(2), functions.size(col)),  // Array: slice from index 2
                col -> functions.array()                                    // Singular: empty array
        );
        return Collection.nonSingular(tailCol);  // Result is always array
    }

    // ===== TRANSFORMATION UTILITIES =====

    /**
     * Map a function over collection elements.
     */
    @Nonnull
    public Collection map(Function<Column, Column> elementTransform) {
        Column mapped = apply(
                col -> functions.transform(col, elementTransform::apply),  // Array: transform
                elementTransform                                           // Singular: apply directly
        );
        return new Collection(mapped, isSingular);
    }

    /**
     * Filter collection elements.
     */
    @Nonnull
    public Collection filter(Function<Column, Column> predicate) {
        Column filtered = apply(
                col -> functions.filter(col, predicate::apply),  // Array: filter
                col -> when(predicate.apply(col), col)           // Singular: conditional
                        .otherwise(lit(null))
        );
        // Filtering always produces non-singular result (could be empty array)
        return Collection.nonSingular(filtered);
    }

    /**
     * Check if all elements satisfy predicate.
     */
    @Nonnull
    public Column all(Function<Column, Column> predicate) {
        return apply(
                col -> functions.forall(col, predicate::apply),  // Array: forall
                predicate                                        // Singular: just evaluate
        );
    }

    /**
     * Check if any element satisfies predicate.
     */
    @Nonnull
    public Column any(Function<Column, Column> predicate) {
        return apply(
                col -> functions.exists(col, predicate::apply),  // Array: exists
                predicate                                        // Singular: just evaluate
        );
    }

    // ===== NULL SAFETY HELPERS =====

    /**
     * Returns column with default if current column is null.
     */
    @Nonnull
    public Collection orDefault(Column defaultValue) {
        return new Collection(
                when(column.isNull(), defaultValue).otherwise(column),
                isSingular
        );
    }
}
```

### Key Design Points

1. **Extends EvalHelper concept**: Keeps `apply()` and `applyNonNull()`
2. **Specific operations**: `count()`, `first()`, `last()`, `isEmpty()`, etc.
3. **Transformations**: `map()`, `filter()`, `all()`, `any()`
4. **Returns Collection for chaining**: Operations that return collections return `Collection` wrapper
5. **Returns Column for values**: Operations that return scalar values return `Column`

---

## Part 2: Automatic Boxing/Unboxing

### The Invocation Binder

When calling a handler method, we need to:
1. **Box**: Convert `Column` arguments to wrapper types (`Collection`, `Quantity`, etc.)
2. **Unbox**: Convert wrapper return values back to `Column`

### Design

```java
/**
 * Handles automatic boxing/unboxing of wrapper types when invoking handler methods.
 */
public class InvocationBinder {

    /**
     * Invoke a handler method with automatic boxing/unboxing.
     *
     * @param handler     The handler instance (already has context as field)
     * @param method      The @Operation method to invoke
     * @param args        Raw Column arguments
     * @param argNodes    Original IR nodes (for type information)
     * @return The result Column (after unboxing if needed)
     */
    public Column invoke(
            OperationHandler handler,
            Method method,
            List<Column> args,
            List<IRNode> argNodes) {

        // Box arguments
        Object[] boxedArgs = boxArguments(method, args, argNodes);

        // Invoke method
        Object result = invokeMethod(method, handler, boxedArgs);

        // Unbox result
        return unboxResult(result);
    }

    /**
     * Box raw Column arguments to wrapper types based on method parameter types.
     * Note: CodeGenContext is NOT passed as parameter - it's already in handler state.
     */
    private Object[] boxArguments(
            Method method,
            List<Column> args,
            List<IRNode> argNodes) {

        Class<?>[] paramTypes = method.getParameterTypes();
        Object[] boxedArgs = new Object[paramTypes.length];

        for (int i = 0; i < args.size(); i++) {
            Column col = args.get(i);
            IRNode argNode = argNodes.get(i);
            Class<?> paramType = paramTypes[i];

            // Box based on parameter type
            boxedArgs[i] = boxArgument(col, argNode, paramType);
        }

        return boxedArgs;
    }

    /**
     * Box a single argument.
     */
    private Object boxArgument(Column col, IRNode argNode, Class<?> targetType) {
        // If target type is Column, no boxing needed
        if (targetType == Column.class) {
            return col;
        }

        // Box to Collection
        if (targetType == Collection.class) {
            boolean isSingular = argNode.getType().getCardinality().isSingular();
            return Collection.of(col, isSingular);
        }

        // Box to Quantity
        if (targetType == Quantity.class) {
            return Quantity.of(col);
        }

        // Box to other wrapper types...
        // (CodeableConcept, Period, etc.)

        // Unknown type - pass through as Column
        return col;
    }

    /**
     * Unbox result to Column.
     */
    private Column unboxResult(Object result) {
        if (result instanceof Column col) {
            return col;
        }

        if (result instanceof Collection coll) {
            return coll.toColumn();
        }

        if (result instanceof Quantity qty) {
            return qty.toColumn();
        }

        // Other wrapper types...

        throw new IllegalStateException(
            "Handler returned unexpected type: " + result.getClass()
        );
    }

    private Object invokeMethod(Method method, Object target, Object[] args) {
        try {
            return method.invoke(target, args);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to invoke handler method", e);
        }
    }
}
```

### How It Works

**Before boxing/unboxing**:
```java
// Handler must work with raw Columns
@Operation("count")
public Column count(Column input, CodeGenContext ctx) {
    // Must manually check cardinality and branch
    Type type = ???;  // Where do we get type info?
    if (type.getCardinality().isSingular()) {
        return lit(1);
    } else {
        return functions.size(input);
    }
}
```

**After boxing/unboxing** (with stateful handlers):
```java
// Handler works with Collection wrapper
// CodeGenContext available as this.context field
@Operation("count")
public Column count(Collection input) {
    // Collection handles singular vs array automatically!
    return input.count();
}
```

---

## Part 3: Handler Examples with Wrappers

### Collection Operations Handler

```java
public class CollectionOperationHandler extends AnnotatedOperationHandler {

    public CollectionOperationHandler(String operation, Type dispatchType, CodeGenContext context) {
        super(operation, dispatchType, context);
    }

    @Operation("count")
    public Column count(Collection input) {
        return input.count();  // Simple! (this.context available as field)
    }

    @Operation("first")
    public Column first(Collection input) {
        return input.first();
    }

    @Operation("last")
    public Column last(Collection input) {
        return input.last();
    }

    @Operation("isEmpty")
    public Column isEmpty(Collection input) {
        return input.isEmpty();
    }

    @Operation("exists")
    public Column exists(Collection input) {
        return input.exists();
    }

    @Operation("single")
    public Column single(Collection input) {
        return input.single();
    }

    @Operation("tail")
    public Collection tail(Collection input) {
        return input.tail();  // Returns Collection for chaining
    }
}
```

### Comparison with Multiple Collection Arguments

```java
public class ComparisonOperationHandler extends AnnotatedOperationHandler {

    public ComparisonOperationHandler(String operation, Type dispatchType, CodeGenContext context) {
        super(operation, dispatchType, context);
    }

    @Operation(name = "eq", types = {Shape.INTEGER, Shape.DECIMAL})
    public Column equalsNumeric(Collection left, Collection right) {
        // Collections handle singular vs array automatically
        // Just work with the columns
        // this.context available as field if needed
        return left.toColumn().equalTo(right.toColumn());
    }

    @Operation(name = "eq", types = {Shape.QUANTITY})
    public Column equalsQuantity(Collection left, Collection right) {
        // Box to both Collection AND Quantity
        // InvocationBinder handles nested boxing
        // For now, assume single Quantity values
        Quantity q1 = Quantity.of(left.toColumn());
        Quantity q2 = Quantity.of(right.toColumn());

        return q1.value().equalTo(q2.value());
    }
}
```

### Higher-Order Collection Operations

```java
public class CollectionOperationHandler extends AnnotatedOperationHandler {

    public CollectionOperationHandler(String operation, Type dispatchType, CodeGenContext context) {
        super(operation, dispatchType, context);
    }

    @Operation("where")
    public Collection where(Collection input, LambdaExpression predicate) {
        // predicate is a lambda that needs to be evaluated per element
        Function<Column, Column> predicateFn = element -> {
            // Evaluate lambda with element as context
            // this.context available as field
            return this.context.evaluateLambda(predicate, element);
        };

        return input.filter(predicateFn);
    }

    @Operation("select")
    public Collection select(Collection input, LambdaExpression projection) {
        Function<Column, Column> projectionFn = element -> {
            return this.context.evaluateLambda(projection, element);
        };

        return input.map(projectionFn);
    }

    @Operation("all")
    public Column all(Collection input, LambdaExpression predicate) {
        Function<Column, Column> predicateFn = element -> {
            return this.context.evaluateLambda(predicate, element);
        };

        return input.all(predicateFn);
    }

    @Operation("any")
    public Column any(Collection input, LambdaExpression predicate) {
        Function<Column, Column> predicateFn = element -> {
            return this.context.evaluateLambda(predicate, element);
        };

        return input.any(predicateFn);
    }
}
```

---

## Part 4: Extending to Other Wrappers

### Quantity Wrapper with Boxing

```java
public record Quantity(@Nonnull Column column) {

    public static Quantity of(Column col) {
        return new Quantity(col);
    }

    // Field accessors
    public Column value() {
        return column.getField("value");
    }

    public Column unit() {
        return column.getField("unit");
    }

    public Column system() {
        return column.getField("system");
    }

    public Column code() {
        return column.getField("code");
    }

    // Struct builder
    public static Column build(Column value, Column unit, Column system, Column code) {
        return functions.struct(
                value.as("value"),
                unit.as("unit"),
                system.as("system"),
                code.as("code")
        );
    }

    // Transformations
    public Quantity mapValue(Function<Column, Column> fn) {
        return new Quantity(build(fn.apply(value()), unit(), system(), code()));
    }

    // Unboxing
    public Column toColumn() {
        return column;
    }
}
```

### Quantity Handler with Auto-Boxing

```java
public class QuantityHandler extends AnnotatedOperationHandler {

    public QuantityHandler(String operation, Type dispatchType, CodeGenContext context) {
        super(operation, dispatchType, context);
    }

    @Operation("add")
    public Quantity add(Quantity left, Quantity right) {
        // Work with Quantity domain objects!
        // this.context available as field if needed
        Column resultValue = left.value().plus(right.value());
        return new Quantity(
                Quantity.build(resultValue, left.unit(), left.system(), left.code())
        );
    }

    @Operation("multiply")
    public Quantity multiply(Quantity quantity, Column scalar) {
        // Mix wrappers and raw Columns as needed
        Column resultValue = quantity.value().multiply(scalar);
        return new Quantity(
                Quantity.build(resultValue, quantity.unit(), quantity.system(), quantity.code())
        );
    }

    @Operation("abs")
    public Quantity abs(Quantity quantity) {
        return quantity.mapValue(functions::abs);
    }
}
```

---

## Part 5: Nested Boxing (Collection of Quantities)

### Challenge

What if we have a collection of quantities?
- IR type: `Collection<Quantity>`
- Column: `Column` containing array of structs
- Handler parameter: `Collection` containing quantities

### Solution: Lazy Boxing

```java
public record Collection(@Nonnull Column column, boolean isSingular) {

    /**
     * Map with automatic boxing of elements.
     * The mapper function receives boxed elements.
     */
    @Nonnull
    public <T> Collection mapBoxed(
            Class<T> elementWrapperType,
            Function<T, Column> mapper) {

        Function<Column, Column> elementTransform = element -> {
            // Box element
            T boxedElement = boxElement(element, elementWrapperType);
            // Apply mapper
            return mapper.apply(boxedElement);
        };

        return map(elementTransform);
    }

    private <T> T boxElement(Column element, Class<T> wrapperType) {
        if (wrapperType == Quantity.class) {
            return wrapperType.cast(Quantity.of(element));
        }
        // Other wrapper types...
        throw new IllegalArgumentException("Unknown wrapper type: " + wrapperType);
    }

    /**
     * Example: Add 5 to value of each quantity in collection.
     */
    public Collection addToQuantityValues(Column amount) {
        return mapBoxed(Quantity.class, qty -> {
            Column newValue = qty.value().plus(amount);
            return Quantity.build(newValue, qty.unit(), qty.system(), qty.code());
        });
    }
}
```

---

## Part 6: Recommendations

### Recommendation 1: Implement Collection Wrapper

**Do:**
- Create `Collection` wrapper extending `EvalHelper` concept
- Implement common operations: `count()`, `first()`, `last()`, `isEmpty()`, etc.
- Provide general utilities: `apply()`, `map()`, `filter()`, `all()`, `any()`
- Return `Collection` for operations that produce collections (tail, filter)
- Return `Column` for operations that produce scalars (count, first)

**Benefits:**
- Handlers don't need to manually check cardinality
- Cleaner, more readable handler code
- Correct handling of singular vs array guaranteed
- Reusable transformation utilities

### Recommendation 2: Implement Automatic Boxing/Unboxing

**Do:**
- Create `InvocationBinder` that wraps handler method invocation
- Automatically box `Column` → wrapper types based on method parameter types
- Use `IRNode.getType().getCardinality()` to determine if Collection is singular
- Automatically unbox wrapper types → `Column` from return values
- Support nested boxing (e.g., `Collection<Quantity>`) via lazy boxing

**Benefits:**
- Handlers work with domain types (Collection, Quantity)
- Type safety at domain level
- Less boilerplate in handlers
- Automatic cardinality handling

### Recommendation 3: Wrapper Types to Support

**High Priority** (implement first):
1. **Collection** - Used everywhere, handles singular vs array
2. **Quantity** - Complex struct with 4 fields
3. **CodeableConcept** - Complex struct with coding arrays

**Medium Priority**:
4. **Period** - Struct with start/end
5. **Range** - Struct with low/high
6. **Ratio** - Struct with numerator/denominator

**Low Priority** (may not need wrappers):
- Primitives (Integer, Decimal, String) - single Column, no complexity

### Recommendation 4: Handler Organization with Wrappers

**Collection operations** → Generic handler using `Collection` wrapper:
```java
CollectionOperationHandler
  count(Collection) → Column
  first(Collection) → Column
  where(Collection, Lambda) → Collection
```

**Quantity operations** → Type-based handler using `Quantity` wrapper:
```java
QuantityHandler
  add(Quantity, Quantity) → Quantity
  getValue(Quantity) → Column
```

**Comparison operations** → Operation-based handler using `Collection` for cardinality:
```java
ComparisonOperationHandler
  eq(Collection, Collection) → Column  // Handles singular vs array
```

### Recommendation 5: Validation in InvocationBinder

Add validation that checks:
- Parameter type compatibility with IR node types
- Cardinality matches (singular vs collection)
- Return type compatibility

This provides **runtime type checking** that complements the **registration-time checking**.

---

## Summary

### Before (Manual Cardinality Handling)

```java
@Operation("count")
public Column count(Column input, CodeGenContext ctx) {
    Type type = ???;  // How to get type info?
    if (type.getCardinality().isSingular()) {
        return when(input.isNotNull(), lit(1)).otherwise(lit(0));
    } else {
        return when(input.isNotNull(), functions.size(input)).otherwise(lit(0));
    }
}
```

### After (Automatic Boxing/Unboxing + Stateful Handlers)

```java
@Operation("count")
public Column count(Collection input) {
    // Collection handles singular vs array
    // this.context available as field if needed
    return input.count();  // Handles everything!
}
```

### Key Benefits

✅ **Cleaner handler code** - Work with domain types, not raw Columns
✅ **Automatic cardinality handling** - Collection wrapper knows if singular
✅ **Type safety** - Compile-time checking of wrapper types
✅ **Reusable operations** - Collection operations defined once, work everywhere
✅ **Better testing** - Test wrapper operations independently
✅ **Less boilerplate** - No manual branching on cardinality

### Implementation Priority

1. **Collection wrapper** - Most impactful, used everywhere
2. **InvocationBinder** - Enables automatic boxing/unboxing
3. **Quantity wrapper** - Second most common complex type
4. **Other wrappers** - As needed (CodeableConcept, Period, etc.)

This design gives you the elegance of working with domain types while maintaining the performance and correctness of the underlying SparkSQL translation.
