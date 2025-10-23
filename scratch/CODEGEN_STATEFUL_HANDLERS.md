# Stateful Handlers: Command Object Pattern

## The Idea

Instead of stateless singleton handlers that receive all context as parameters, create **stateful handler instances per invocation** that encapsulate:
- Operation name
- Dispatch type
- CodeGenContext
- Any other invocation-specific state

This is the **Command Pattern** - each handler invocation becomes a command object.

---

## Comparison: Stateless vs Stateful

### Current Approach: Stateless Handlers

```java
// Handler is a singleton, reused for all invocations
public class ComparisonOperationHandler implements OperationHandler {

    @Override
    public Column handle(String operation, List<Column> args, Type dispatchType, CodeGenContext ctx) {
        // All state passed as parameters
        Column left = args.get(0);
        Column right = args.get(1);

        if (dispatchType.equals(Shape.INTEGER) || dispatchType.equals(Shape.DECIMAL)) {
            return handleNumeric(operation, left, right);
        } else if (dispatchType.equals(Shape.STRING)) {
            return handleString(operation, left, right);
        }
        // ...
    }

    private Column handleNumeric(String operation, Column left, Column right) {
        return switch (operation) {
            case "eq" -> left.equalTo(right);
            case "ne" -> left.notEqual(right);
            // ...
        };
    }
}

// Usage in code generator
OperationHandler handler = registry.getHandler("eq");  // Singleton
Column result = handler.handle("eq", args, dispatchType, context);
```

### Proposed Approach: Stateful Handlers

```java
// Handler created per invocation with state
public class ComparisonOperationHandler implements OperationHandler {
    private final String operation;
    private final Type dispatchType;
    private final CodeGenContext context;

    // Constructor captures invocation state
    public ComparisonOperationHandler(String operation, Type dispatchType, CodeGenContext context) {
        this.operation = operation;
        this.dispatchType = dispatchType;
        this.context = context;
    }

    @Override
    public Column handle(List<Column> args) {
        // State already available as fields
        Column left = args.get(0);
        Column right = args.get(1);

        if (dispatchType.equals(Shape.INTEGER) || dispatchType.equals(Shape.DECIMAL)) {
            return handleNumeric(left, right);
        } else if (dispatchType.equals(Shape.STRING)) {
            return handleString(left, right);
        }
        // ...
    }

    private Column handleNumeric(Column left, Column right) {
        // Can access this.operation, this.context directly
        return switch (operation) {
            case "eq" -> left.equalTo(right);
            case "ne" -> left.notEqual(right);
            case "gt" -> left.gt(right);
            // ...
        };
    }

    private Column handleString(Column left, Column right) {
        return switch (operation) {
            case "eq" -> left.equalTo(right);
            case "ne" -> left.notEqual(right);
            // ...
        };
    }
}

// Usage in code generator
OperationHandler handler = new ComparisonOperationHandler("eq", dispatchType, context);
Column result = handler.handle(args);
```

---

## Benefits of Stateful Handlers

### 1. Simpler Method Signatures

**Before** (stateless):
```java
private Column handleNumeric(String operation, Column left, Column right, CodeGenContext ctx) {
    // 4+ parameters
}
```

**After** (stateful):
```java
private Column handleNumeric(Column left, Column right) {
    // Only essential parameters; state in fields
}
```

### 2. Natural State Encapsulation

```java
public class QuantityOperationHandler implements OperationHandler {
    private final String operation;
    private final Type dispatchType;
    private final CodeGenContext context;

    // Can add derived state computed once
    private final boolean requiresUnitConversion;

    public QuantityOperationHandler(String operation, Type dispatchType, CodeGenContext context) {
        this.operation = operation;
        this.dispatchType = dispatchType;
        this.context = context;

        // Compute once, use many times
        this.requiresUnitConversion = Set.of("add", "sub", "gt", "lt").contains(operation);
    }

    @Override
    public Column handle(List<Column> args) {
        if (requiresUnitConversion) {
            // Use cached state
        }
        // ...
    }
}
```

### 3. Cleaner Error Messages

```java
@Override
public Column handle(List<Column> args) {
    if (!isValidArity(args)) {
        throw new IllegalArgumentException(
            "Operation '%s' on type '%s' expects %d arguments but got %d"
                .formatted(operation, dispatchType, expectedArity(), args.size())
        );
    }
    // ...
}
```

Error context automatically available from fields.

### 4. Builder Pattern for Construction

```java
// For complex handlers with many options
public class ComplexOperationHandler implements OperationHandler {
    private final String operation;
    private final Type dispatchType;
    private final CodeGenContext context;
    private final boolean strict;
    private final NullHandling nullHandling;

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String operation;
        private Type dispatchType;
        private CodeGenContext context;
        private boolean strict = false;
        private NullHandling nullHandling = NullHandling.DEFAULT;

        public Builder operation(String operation) {
            this.operation = operation;
            return this;
        }

        public Builder dispatchType(Type type) {
            this.dispatchType = type;
            return this;
        }

        public Builder context(CodeGenContext ctx) {
            this.context = ctx;
            return this;
        }

        public Builder strict() {
            this.strict = true;
            return this;
        }

        public ComplexOperationHandler build() {
            return new ComplexOperationHandler(this);
        }
    }
}

// Usage
OperationHandler handler = ComplexOperationHandler.builder()
    .operation("add")
    .dispatchType(Shape.QUANTITY)
    .context(context)
    .strict()
    .build();
```

---

## Handler Registry: Factory Pattern

The registry becomes a **factory** instead of a cache:

### Stateless Registry (current)

```java
public class HandlerRegistry {
    private final Map<String, OperationHandler> operationHandlers = new HashMap<>();
    private final Map<Type, TypeHandler> typeHandlers = new HashMap<>();

    public HandlerRegistry() {
        // Register singletons once
        operationHandlers.put("comparison", new ComparisonOperationHandler());
        typeHandlers.put(Shape.STRING, new StringHandler());
    }

    public Column handleOperation(String op, List<Column> args, Type dispatchType, CodeGenContext ctx) {
        // Get singleton handler
        OperationHandler handler = operationHandlers.get(determineHandlerKey(op));
        // Pass all state as parameters
        return handler.handle(op, args, dispatchType, ctx);
    }
}
```

### Stateful Registry (factory)

```java
public class HandlerRegistry {
    // Store handler FACTORIES, not handler instances
    private final Map<String, OperationHandlerFactory> operationHandlerFactories = new HashMap<>();
    private final Map<Type, TypeHandlerFactory> typeHandlerFactories = new HashMap<>();

    public HandlerRegistry() {
        // Register factories (can be lambdas!)
        operationHandlerFactories.put("comparison", ComparisonOperationHandler::new);
        operationHandlerFactories.put("arithmetic", ArithmeticOperationHandler::new);

        typeHandlerFactories.put(Shape.STRING, StringHandler::new);
        typeHandlerFactories.put(Shape.QUANTITY, QuantityHandler::new);
    }

    public Column handleOperation(String op, List<Column> args, Type dispatchType, CodeGenContext ctx) {
        // Create handler for this invocation
        OperationHandler handler = createHandler(op, dispatchType, ctx);
        // Handler already has state
        return handler.handle(args);
    }

    private OperationHandler createHandler(String op, Type dispatchType, CodeGenContext ctx) {
        OperationHandlerFactory factory = operationHandlerFactories.get(determineHandlerKey(op));
        if (factory != null) {
            return factory.create(op, dispatchType, ctx);
        }

        TypeHandlerFactory typeFactory = typeHandlerFactories.get(dispatchType);
        if (typeFactory != null) {
            return typeFactory.create(op, dispatchType, ctx);
        }

        throw new UnsupportedOperationException("No handler for operation: " + op);
    }
}

// Factory interfaces
@FunctionalInterface
interface OperationHandlerFactory {
    OperationHandler create(String operation, Type dispatchType, CodeGenContext context);
}

@FunctionalInterface
interface TypeHandlerFactory {
    OperationHandler create(String operation, Type dispatchType, CodeGenContext context);
}
```

---

## Performance Considerations

### Object Allocation

**Concern**: Creating a handler per operation means more allocations.

**Analysis**:
- Modern JVMs are excellent at short-lived object allocation
- Handlers are allocated in Eden space (young generation)
- Most will be GC'd in minor collections (very cheap)
- Handler construction is simple (just field assignments)

**Measurement**: For a typical FHIRPath expression with 10 operations:
- Stateless: 0 handler allocations
- Stateful: 10 handler allocations (~400 bytes total)

**Verdict**: Negligible performance impact for translation workload.

### When Stateless is Better

Stateless handlers may be preferable if:
- Handler construction is expensive (complex initialization)
- Handler holds expensive resources (connections, caches)
- Operation invocation frequency is extremely high (millions/second)

**For code generation (translation)**: Stateful handlers are fine.

---

## Combining with Annotation-Based Dispatch

Stateful handlers work beautifully with annotations:

```java
public abstract class AnnotatedOperationHandler implements OperationHandler {
    protected final String operation;
    protected final Type dispatchType;
    protected final CodeGenContext context;

    protected AnnotatedOperationHandler(String operation, Type dispatchType, CodeGenContext context) {
        this.operation = operation;
        this.dispatchType = dispatchType;
        this.context = context;
    }

    @Override
    public Column handle(List<Column> args) {
        // Find method annotated with @Operation(operation)
        Method method = findOperationMethod(operation, dispatchType);

        if (method != null) {
            return invokeOperationMethod(method, args);
        }

        throw new UnsupportedOperationException(
            "Operation '%s' not supported for type '%s'".formatted(operation, dispatchType)
        );
    }
}

// Concrete handler
public class ComparisonOperationHandler extends AnnotatedOperationHandler {

    public ComparisonOperationHandler(String operation, Type dispatchType, CodeGenContext context) {
        super(operation, dispatchType, context);
    }

    @Operation(name = "eq", types = {Shape.INTEGER, Shape.DECIMAL})
    public Column equalsNumeric(Column left, Column right) {
        // Can access this.operation, this.context directly
        return left.equalTo(right);
    }

    @Operation(name = "ne", types = {Shape.INTEGER, Shape.DECIMAL})
    public Column notEqualsNumeric(Column left, Column right) {
        return left.notEqual(right);
    }

    @Operation(name = "eq", types = {Shape.STRING})
    public Column equalsString(Column left, Column right) {
        // Context available for string comparison options
        if (context.isCaseSensitive()) {
            return left.equalTo(right);
        } else {
            return functions.lower(left).equalTo(functions.lower(right));
        }
    }
}
```

---

## Hybrid: State Where It Helps

Not all handlers need to be stateful. Use state where it adds value:

### Stateful: Complex Handlers

```java
// Good use of state: complex logic that benefits from encapsulation
public class QuantityOperationHandler implements OperationHandler {
    private final String operation;
    private final CodeGenContext context;
    private final boolean needsUnitConversion;
    private final UnitConverter unitConverter;

    public QuantityOperationHandler(String operation, Type dispatchType, CodeGenContext context) {
        this.operation = operation;
        this.context = context;
        this.needsUnitConversion = determineNeedsUnitConversion(operation);
        this.unitConverter = needsUnitConversion ? context.getUnitConverter() : null;
    }

    @Override
    public Column handle(List<Column> args) {
        if (needsUnitConversion) {
            return handleWithConversion(args);
        } else {
            return handleDirect(args);
        }
    }
}
```

### Stateless: Simple Handlers

```java
// Simple handlers can stay stateless
public class StringOperations {
    public static Column upper(Column str) {
        return functions.upper(str);
    }

    public static Column lower(Column str) {
        return functions.lower(str);
    }

    public static Column substring(Column str, Column start, Column length) {
        return functions.substring(str, start, length);
    }
}

// Registry can wrap these as stateful handlers if needed
operationHandlerFactories.put("upper",
    (op, type, ctx) -> args -> StringOperations.upper(args.get(0))
);
```

---

## Testing Stateful Handlers

Testing becomes simpler because you can create handlers with specific state:

```java
@Test
void comparisonHandler_equalityOnIntegers_shouldWork() {
    // Given: Handler for "eq" operation on integers
    ComparisonOperationHandler handler = new ComparisonOperationHandler(
        "eq",
        Shape.INTEGER,
        new CodeGenContext(spark)
    );

    Dataset<Row> df = createTestData(5, 5);
    List<Column> args = List.of(df.col("a"), df.col("b"));

    // When: Execute handler
    Column result = handler.handle(args);

    // Then: Should compare correctly
    assertTrue(df.select(result).first().getBoolean(0));
}

@Test
void comparisonHandler_caseSensitiveString_shouldRespectContext() {
    // Given: Context with case-sensitive comparison
    CodeGenContext caseSensitive = new CodeGenContext(spark)
        .withCaseSensitive(true);

    ComparisonOperationHandler handler = new ComparisonOperationHandler(
        "eq",
        Shape.STRING,
        caseSensitive
    );

    Dataset<Row> df = createTestData("Hello", "hello");

    // When: Execute
    Column result = handler.handle(List.of(df.col("a"), df.col("b")));

    // Then: Should be case-sensitive (not equal)
    assertFalse(df.select(result).first().getBoolean(0));
}
```

---

## Summary

### Stateful Handler Approach

**Structure**:
```java
// Handler encapsulates invocation state
new ComparisonOperationHandler(operation, dispatchType, context).handle(args)
```

**Pros**:
✅ Simpler method signatures (state in fields, not parameters)
✅ Natural state encapsulation
✅ Cleaner error messages (context in fields)
✅ Easier testing (create handlers with specific state)
✅ Builder pattern for complex handlers
✅ State can be computed once, used many times

**Cons**:
❌ More object allocations (one per operation invocation)
❌ Registry becomes factory instead of cache
❌ Slightly more complex handler construction

**Verdict**: **Excellent choice for code generation** (translator workload)

### When to Use

Use stateful handlers when:
- Handler needs multiple pieces of context
- Handler has complex initialization logic
- Handler benefits from computed/derived state
- Performance impact of allocation is negligible

Use stateless handlers when:
- Handler is a pure function (no context needed)
- Handler construction would be expensive
- Operation invocation frequency is extreme

### Recommendation

**For FHIRPath code generator**: Use **stateful handlers**

The benefits (simpler code, better encapsulation, easier testing) outweigh the minimal cost of object allocation for a translation workload.

**Organization**:
```java
// Operation-based handlers (stateful)
ComparisonOperationHandler(operation, dispatchType, context)
ArithmeticOperationHandler(operation, dispatchType, context)

// Type-based handlers (stateful)
StringHandler(operation, dispatchType, context)
QuantityHandler(operation, dispatchType, context)

// Registry as factory
HandlerRegistry.createHandler(operation, dispatchType, context) → handler.handle(args)
```
