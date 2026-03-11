# Additional Design Considerations for SparkSQL Code Generator

## Areas to Consider Beyond Core Architecture

---

## 1. Error Handling and Validation

### Runtime Error Strategy

**Question**: What happens when operations fail during code generation or execution?

**Considerations**:
- **Type mismatches** at runtime (despite compile-time checks)
- **Null handling** - FHIRPath vs SQL null semantics differ
- **Invalid operations** - operations on empty collections, division by zero
- **Cardinality violations** - expecting singular but got collection

**Recommendation**:
```java
public class OperationExecutionException extends RuntimeException {
    private final String operationName;
    private final Type dispatchType;
    private final List<Type> argumentTypes;

    // Rich error context for debugging
}

// In handlers:
@Operation("divide")
public Column divide(Column left, Column right) {
    // Protect against division by zero?
    // Return null, throw exception, or generate SQL with safe handling?
    return when(right.equalTo(0), lit(null))
           .otherwise(left.divide(right));
}
```

**Questions to Answer**:
- Should handlers throw exceptions or generate defensive SQL?
- How to preserve FHIRPath null propagation semantics in SQL?
- Should validation happen at code gen time or defer to Spark runtime?

---

## 2. Null Handling Strategy

### FHIRPath vs SQL Null Semantics

**Issue**: FHIRPath and SQL have different null handling:
- **FHIRPath**: Operations on empty/null collections return empty, not null
- **SQL**: `NULL + 5 = NULL` (null propagation)

**Example Problem**:
```java
// FHIRPath: 5 + {} = {}  (empty collection)
// SQL: 5 + NULL = NULL   (null value)

// Need to decide: which semantics to implement?
```

**Recommendation**:
Define clear null handling policy:
1. **Empty collection** → Empty array `[]` in SQL
2. **Null value** → SQL `NULL`
3. **Operations on empty** → Return empty array, not NULL
4. **Operations on null** → Return NULL or empty based on operation

**Implementation**:
```java
public record Collection(@Nonnull Column column, boolean isSingular) {

    // Distinguish empty from null
    public Column isEmpty() {
        return applyNonNull(
            col -> functions.size(col).equalTo(0),  // Empty array
            col -> lit(false),                      // Singular value (not empty)
            lit(true)                               // NULL = empty
        );
    }

    // Empty-safe operations
    public Column count() {
        return applyNonNull(
            functions::size,
            col -> lit(1),
            lit(0)  // NULL/empty → 0, not NULL
        );
    }
}
```

---

## 3. Lambda Expression Handling

### Context and Scope Management

**Question**: How are lambda parameters bound and scoped?

**Current Gap**: Need to define:
1. How lambda parameters are bound to collection elements
2. How outer variables are captured
3. How nested lambdas work
4. How `$this` and `$index` are handled

**Example**:
```
// FHIRPath: collection.where(value > 5).select(value * 2)
//           ^^^^^^                ^^^^^ outer lambda
//                  ^^^^^^^^ nested lambda, needs $this binding
```

**Recommendation**:
```java
public class LambdaContext {
    private final Column element;      // $this
    private final Column index;        // $index (if available)
    private final Map<String, Column> captures;  // Outer variables

    public Column resolve(String identifier) {
        if ("$this".equals(identifier)) return element;
        if ("$index".equals(identifier)) return index;
        return captures.get(identifier);
    }
}

// In CodeGenContext:
public class CodeGenContext {
    private final Deque<LambdaContext> lambdaStack = new ArrayDeque<>();

    public Column evaluateLambda(LambdaExpression lambda, Column element) {
        lambdaStack.push(new LambdaContext(element, null, currentCaptures()));
        try {
            return visit(lambda.body());
        } finally {
            lambdaStack.pop();
        }
    }

    public Column resolveIdentifier(String name) {
        // Check lambda stack first
        if (!lambdaStack.isEmpty()) {
            Column resolved = lambdaStack.peek().resolve(name);
            if (resolved != null) return resolved;
        }
        // Then check other scopes...
    }
}
```

---

## 4. Type Variables and Generic Operations

### Polymorphic Type Handling

**Question**: How to handle operations that work on `Collection<T>` for any `T`?

**Example**:
```java
// count() works on Collection<Integer>, Collection<String>, Collection<Quantity>
// The element type doesn't matter

@Operation("count")
public Column count(Collection input) {
    // Works for any element type - no type dispatch needed
    return input.count();
}

// But what about select()?
@Operation("select")
public Collection select(Collection input, LambdaExpression projection) {
    // Element type DOES matter for evaluating projection
    // How to get element type?
}
```

**Recommendation**:
Add element type information to Collection wrapper:
```java
public record Collection(
    @Nonnull Column column,
    boolean isSingular,
    @Nonnull Type elementType  // NEW: element type info
) {

    // For operations that need to know element type
    public Collection map(Function<Column, Column> mapper) {
        Column mapped = apply(
            col -> functions.transform(col, mapper::apply),
            mapper
        );
        // Element type might change - need projection result type
        return new Collection(mapped, isSingular, elementType);
    }
}
```

**Question**: Does InvocationBinder need to handle type variable instantiation?

---

## 5. Handler Registry Lifecycle

### Initialization and Extension

**Questions**:
- When/where are handlers registered?
- How to ensure all needed handlers are available before code generation?
- How to support plugins/extensions?

**Recommendation**:
```java
public class HandlerRegistry {

    // Standard registry builder
    public static HandlerRegistry standard(SignatureRegistry sigRegistry) {
        HandlerRegistry registry = new HandlerRegistry();

        // Register operation-based handlers
        registry.registerOperationHandlerFactory("comparison",
            ComparisonOperationHandler::new);
        registry.registerOperationHandlerFactory("arithmetic",
            ArithmeticOperationHandler::new);

        // Register type-based handlers
        registry.registerTypeHandlerFactory(Shape.STRING,
            StringHandler::new);
        registry.registerTypeHandlerFactory(Shape.QUANTITY,
            QuantityHandler::new);

        // Register generic handlers
        registry.registerGenericHandlerFactory(
            CollectionOperationHandler::new);

        return registry;
    }

    // Extension mechanism
    public void registerCustomHandler(
        String operationName,
        OperationHandlerFactory factory) {
        // Allow user-defined handlers
    }

    // Validation: check all operations in signature registry have handlers
    public void validate(SignatureRegistry sigRegistry) {
        for (String opName : sigRegistry.allOperations()) {
            if (!canHandle(opName)) {
                throw new IllegalStateException(
                    "No handler registered for operation: " + opName
                );
            }
        }
    }
}
```

---

## 6. Debugging and Observability

### Tracing and Error Messages

**Question**: How to debug which handler was used and why?

**Recommendation**:
```java
public class CodeGenContext {
    private final boolean debugMode;
    private final List<String> trace;  // Operation trace

    public Column handleOperation(String op, List<Column> args, Type type) {
        if (debugMode) {
            trace.add("Operation: %s, Type: %s, Handler: %s"
                .formatted(op, type, getHandlerName()));
        }

        // Generate code...
    }

    // Rich error messages
    public OperationExecutionException error(String message) {
        return new OperationExecutionException(
            message,
            currentOperation,
            currentType,
            trace  // Include full trace
        );
    }
}

// In handlers:
@Operation("add")
public Quantity add(Quantity left, Quantity right) {
    if (!left.unit().equals(right.unit())) {
        throw context.error(
            "Cannot add quantities with different units: %s vs %s"
                .formatted(left.unit(), right.unit())
        );
    }
    // ...
}
```

---

## 7. Migration Strategy

### Incremental Adoption Path

**Question**: How to migrate from current code to new design?

**Recommendation**:
1. **Phase 1**: Implement core infrastructure (wrappers, registry, binder)
2. **Phase 2**: Migrate simple operations (comparison, arithmetic)
3. **Phase 3**: Migrate complex operations (collections, lambdas)
4. **Phase 4**: Remove old code

**Compatibility Shim**:
```java
// Allow old and new handlers to coexist during migration
public class LegacyHandlerAdapter implements OperationHandler {
    private final BiFunction<List<Column>, Type, Column> legacyImpl;

    @Override
    public Column handle(List<Column> args) {
        // Delegate to old implementation
        return legacyImpl.apply(args, this.dispatchType);
    }
}

// In registry:
registry.registerLegacy("oldOperation",
    (args, type) -> oldCodePath(args, type));
```

---

## 8. Performance Considerations

### Optimization Opportunities

**Questions**:
- Should we cache handler instances?
- Pool wrapper objects?
- Optimize common patterns?

**Recommendation**:
Start simple (create handlers per invocation), profile later:
```java
// If profiling shows allocation pressure, add caching:
public class HandlerCache {
    private final Map<HandlerKey, OperationHandler> cache =
        new ConcurrentHashMap<>();

    public OperationHandler get(String op, Type type, CodeGenContext ctx) {
        HandlerKey key = new HandlerKey(op, type);
        return cache.computeIfAbsent(key,
            k -> factory.create(op, type, ctx));
    }

    // But: handlers are stateful (have context), so can't cache across calls
    // Only cache if handlers become stateless or context is immutable
}
```

**Current recommendation**: Don't optimize prematurely. Stateful handlers are fine.

---

## 9. Documentation Strategy

### Making Design Discoverable

**Recommendations**:

1. **Handler method annotations with examples**:
```java
@Operation("count")
@Doc(
    description = "Returns number of elements in collection",
    examples = {
        @Example(input = "{1, 2, 3}", output = "3"),
        @Example(input = "5", output = "1"),
        @Example(input = "{}", output = "0")
    }
)
public Column count(Collection input) {
    return input.count();
}
```

2. **Architecture Decision Records (ADRs)** for key choices:
   - Why hybrid handler organization?
   - Why stateful handlers?
   - Why automatic boxing/unboxing?

3. **Developer guide** for:
   - Adding new operations
   - Adding new types
   - Writing handler tests

---

## 10. Integration Points

### Connection to Existing System

**Questions to Answer**:

1. **How does SparkCodeGenerator get created?**
   ```java
   // Where/when is this called?
   HandlerRegistry registry = HandlerRegistry.standard(sigRegistry);
   SparkCodeGenerator codeGen = new SparkCodeGenerator(registry, spark);
   ```

2. **How does IR flow into code generation?**
   ```java
   IRNode root = analyzer.analyze(fhirPathExpression);
   Column result = codeGen.visit(root);
   ```

3. **What's the relationship with type analysis?**
   - Code gen needs `Type` information from IR nodes
   - Does `IRNode.getType()` provide everything needed?
   - Cardinality? Element type for collections?

4. **How are UDFs registered?**
   ```java
   // Do handlers register UDFs with SparkSession?
   // Or does CodeGenContext manage UDF lifecycle?
   context.registerUDF("quantityAdd", QuantityUDF.class);
   ```

---

## Summary: Priority Considerations

### High Priority (Decide Now)

1. **Null handling semantics** - FHIRPath vs SQL nulls
2. **Lambda context management** - Scope and binding strategy
3. **Collection element type tracking** - Need for generic operations
4. **Registry initialization** - When/where handlers are registered

### Medium Priority (Design, Implement Later)

5. **Error handling strategy** - Exceptions vs defensive SQL
6. **Debugging/tracing** - Observability during code generation
7. **Migration path** - How to adopt incrementally

### Low Priority (Defer Until Needed)

8. **Performance optimization** - Caching, pooling (profile first)
9. **Extension mechanism** - Plugin architecture
10. **Documentation tooling** - Annotations, ADRs

---

## Recommended Next Steps

1. **Clarify null handling** - Define FHIRPath empty collection semantics
2. **Design lambda evaluation** - Sketch `LambdaContext` and scope management
3. **Enhance Collection wrapper** - Add element type information
4. **Define registry initialization** - Create `HandlerRegistry.standard()`
5. **Prototype one handler end-to-end** - Validate design with real code
6. **Write integration test** - FHIRPath expression → IR → SQL → result

This will validate the design before full implementation.
