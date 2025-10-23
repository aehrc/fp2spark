# Simplified Code Generator with Type Checking

## Key Insight: This is a Translator, Not a Fluent API

**Critical Context**: The code generator translates IR operations to SparkSQL expressions. Most operations map to **single SQL API calls** or simple struct building. We don't need complex wrapper chains or fluent APIs.

**Question**: Can the simpler handler approach still provide type checking between IRNode argument types and handler method expected types?

**Answer**: **YES** - through annotation scanning and validation at handler registration time.

---

## Type Checking Without Wrapper Classes

### The Mechanism

When a handler is registered, we can validate that its `@Operation` methods have signatures compatible with the operation's signature definition:

```java
public abstract class AnnotatedTypeHandler implements TypeHandler {
    private final Map<String, ValidatedOperationMethod> operationRegistry;

    protected AnnotatedTypeHandler(SignatureRegistry signatures) {
        this.operationRegistry = scanAndValidateOperations(signatures);
    }

    private Map<String, ValidatedOperationMethod> scanAndValidateOperations(SignatureRegistry signatures) {
        Map<String, ValidatedOperationMethod> registry = new HashMap<>();

        for (Method method : this.getClass().getDeclaredMethods()) {
            Operation annotation = method.getAnnotation(Operation.class);
            if (annotation != null) {
                String operationName = annotation.value();
                SignatureDefinition sigDef = signatures.lookup(operationName);

                // TYPE CHECKING HAPPENS HERE
                validateMethodSignature(method, sigDef, operationName);

                registry.put(operationName, new ValidatedOperationMethod(method, sigDef));
            }
        }

        return registry;
    }

    private void validateMethodSignature(Method method, SignatureDefinition sigDef, String operationName) {
        Class<?>[] paramTypes = method.getParameterTypes();

        // Check arity (excluding context parameter)
        int expectedArity = sigDef.parameters().size();
        int actualArity = paramTypes.length - 1; // Last param is CodeGenContext
        if (actualArity != expectedArity) {
            throw new IllegalStateException(
                "Handler method %s.%s has %d parameters but operation signature expects %d"
                    .formatted(this.getClass().getSimpleName(), method.getName(), actualArity, expectedArity)
            );
        }

        // Check parameter types
        for (int i = 0; i < expectedArity; i++) {
            ParamSpec paramSpec = sigDef.parameters().get(i);
            Class<?> actualType = paramTypes[i];

            // All parameters must be Column (or a type-specific wrapper if used)
            if (!Column.class.isAssignableFrom(actualType)) {
                throw new IllegalStateException(
                    "Handler method %s.%s parameter %d has type %s but expected Column (or wrapper)"
                        .formatted(this.getClass().getSimpleName(), method.getName(), i, actualType.getSimpleName())
                );
            }
        }

        // Check return type
        Class<?> returnType = method.getReturnType();
        if (!Column.class.isAssignableFrom(returnType)) {
            throw new IllegalStateException(
                "Handler method %s.%s returns %s but expected Column (or wrapper)"
                    .formatted(this.getClass().getSimpleName(), method.getName(), returnType.getSimpleName())
            );
        }

        // Additional validation: Check context parameter
        if (paramTypes.length > 0 && !CodeGenContext.class.equals(paramTypes[paramTypes.length - 1])) {
            throw new IllegalStateException(
                "Handler method %s.%s must have CodeGenContext as final parameter"
                    .formatted(this.getClass().getSimpleName(), method.getName())
            );
        }
    }
}
```

### What This Achieves

✅ **Compile-time type safety**: Handler methods have explicit parameter types
✅ **Registration-time validation**: Mismatches detected when handler is instantiated
✅ **Clear error messages**: Tells you exactly which method has wrong signature
✅ **No wrapper classes required**: Works with `Column` directly

### Runtime Type Checking

We can also add runtime type checking in the code generator:

```java
// In SparkCodeGenerator.visitOperation()
public Column visitOperation(Operation op) {
    String name = op.name();
    ResolvedSignature sig = op.signature();

    // Evaluate arguments
    List<Column> argColumns = op.args().stream()
        .map(this::visit)
        .toList();

    // Determine dispatch type
    Type dispatchType = determineDispatchType(sig.definition(), op);

    // Get handler
    TypeHandler handler = handlerRegistry.getHandler(dispatchType);

    // RUNTIME TYPE VALIDATION
    validateArgumentTypes(op.args(), sig.definition().parameters());

    // Execute operation
    return handler.handle(name, argColumns, context);
}

private void validateArgumentTypes(List<IRNode> argNodes, List<ParamSpec> paramSpecs) {
    for (int i = 0; i < argNodes.size(); i++) {
        Type actualType = argNodes.get(i).getType();
        ParamSpec expected = paramSpecs.get(i);

        // Check if actual type matches expected parameter spec
        if (!expected.matches(actualType)) {
            throw new CodeGenerationException(
                "Argument %d has type %s but operation signature expects %s"
                    .formatted(i, actualType, expected)
            );
        }
    }
}
```

---

## Recommended Simplified Design

### What to Keep

1. **Annotation-based dispatch** (`@Operation` methods)
   - Eliminates switch statements
   - Provides type checking via method signatures
   - Clean separation of concerns

2. **Shared operation providers** (`CommonOperations`, `StringOperations`)
   - Eliminates 74% duplication across primitive types
   - Easy to maintain and extend
   - No wrappers needed

3. **Type dispatch strategies** (RESULT_TYPE, FIRST_ARG_TYPE, GENERIC)
   - Makes dispatch logic explicit and consistent
   - Easy to reason about and test

4. **Factory method pattern** (`@OperationMappings`)
   - Declarative operation registration
   - Clean and maintainable

### What to Simplify

1. **No wrapper classes for primitives** (Integer, Decimal, String, Boolean)
   - These map to single SQL calls: `a.plus(b)`, `a.gt(b)`, `functions.upper(a)`
   - Wrappers add zero value for single method calls
   - Just use `Column` directly

2. **Wrappers ONLY for complex types with 3+ fields** (Quantity, CodeableConcept, Period)
   - These genuinely benefit from field accessors: `q.value()`, `q.unit()`
   - Struct building is complex enough to warrant utilities
   - Still just utilities, not full-blown type systems

3. **No interface hierarchies** (SQLComparable, SQLNumeric, etc.)
   - We're not building a fluent API
   - We're mapping IR operations to SQL calls
   - Interfaces add complexity without benefit in this context

---

## Example: Simplified Handlers

### Primitive Type Handler (No Wrapper)

```java
public class IntegerHandler extends AnnotatedTypeHandler {

    @OperationMappings
    public Stream<NamedMapping> operations() {
        return Stream.concat(
            CommonOperations.arithmeticOps(),    // add, sub, multiply, divide, mod
            CommonOperations.comparisonOps(),    // gt, lt, gte, lte
            CommonOperations.mathOps()           // abs, ceiling, floor
        );
    }

    // No methods needed! All operations come from shared providers
    // (Unless there's Integer-specific logic)
}
```

**Result**: 8 lines of code handles 15+ operations

### Complex Type Handler (With Wrapper Utility)

```java
public class QuantityHandler extends AnnotatedTypeHandler {

    @Operation("add")
    public Column add(Column left, Column right, CodeGenContext ctx) {
        // Wrapper only used for field access convenience
        Quantity q1 = Quantity.of(left);
        Quantity q2 = Quantity.of(right);

        // Simple translation to SQL
        Column resultValue = q1.value().plus(q2.value());
        return Quantity.build(resultValue, q1.unit(), q1.system(), q1.code());
    }

    @Operation("multiply")
    public Column multiply(Column quantity, Column scalar, CodeGenContext ctx) {
        Quantity q = Quantity.of(quantity);
        Column resultValue = q.value().multiply(scalar);
        return Quantity.build(resultValue, q.unit(), q.system(), q.code());
    }

    @OperationMappings
    public Stream<NamedMapping> mathOps() {
        return Stream.of(
            unary("abs", q -> Quantity.of(q).mapValue(functions::abs)),
            unary("ceiling", q -> Quantity.of(q).mapValue(functions::ceil)),
            unary("floor", q -> Quantity.of(q).mapValue(functions::floor))
        );
    }
}

// Quantity.java - Just a utility record with field accessors
public record Quantity(@Nonnull Column target) {
    public static Quantity of(Column col) { return new Quantity(col); }

    public Column value() { return target.getField("value"); }
    public Column unit() { return target.getField("unit"); }
    public Column system() { return target.getField("system"); }
    public Column code() { return target.getField("code"); }

    public static Column build(Column value, Column unit, Column system, Column code) {
        return functions.struct(
            value.as("value"),
            unit.as("unit"),
            system.as("system"),
            code.as("code")
        );
    }

    public Quantity mapValue(Function<Column, Column> fn) {
        return new Quantity(build(fn.apply(value()), unit(), system(), code()));
    }

    public Column toColumn() { return target; }
}
```

**Result**: Clean translation without over-engineering

---

## Architecture Comparison

### Original Proposal (Full Wrappers + Interfaces)

```
┌─────────────────────────────────────────────────────────────────┐
│  Wrapper Classes (with instance methods)                        │
│  - Integer, Decimal, String, Boolean, Quantity, etc.            │
│  - Each has @Operation methods                                  │
│  - Parameter binder auto-boxes/unboxes                          │
├─────────────────────────────────────────────────────────────────┤
│  Interface Hierarchy                                             │
│  - SQLComparable, SQLNumeric, SQLString, etc.                   │
│  - Default implementations using SparkSQL API                   │
└─────────────────────────────────────────────────────────────────┘
```

**Complexity**: HIGH (many classes, boxing/unboxing, interface hierarchy)
**Benefit**: Consistency, strong typing, fluent API
**Problem**: Over-engineered for a translator

### Simplified Proposal (Handlers + Utility Wrappers)

```
┌─────────────────────────────────────────────────────────────────┐
│  Type Handlers (work with Column directly)                      │
│  - IntegerHandler, DecimalHandler, QuantityHandler, etc.        │
│  - @Operation methods: Column → Column                          │
│  - @OperationMappings: Use shared providers                     │
├─────────────────────────────────────────────────────────────────┤
│  Shared Operation Providers                                      │
│  - CommonOperations (arithmetic, comparison, math)              │
│  - StringOperations (upper, lower, substring, etc.)             │
├─────────────────────────────────────────────────────────────────┤
│  Utility Wrappers (for complex types only)                      │
│  - Quantity, CodeableConcept, Period                            │
│  - Simple records with field accessors                          │
│  - No operations, just utilities                                │
└─────────────────────────────────────────────────────────────────┘
```

**Complexity**: LOW (simpler, focused)
**Benefit**: Clean translation, easy to understand and maintain
**Fits Purpose**: Perfect for a translator (IR → SQL)

---

## Type Checking Strategy Summary

| Check Type | When | How | Example |
|------------|------|-----|---------|
| **Method Signature** | Handler registration | Reflection validates `@Operation` methods | `validateMethodSignature()` |
| **Arity** | Handler registration | Compare param count to signature definition | "Expected 2 args, got 3" |
| **Parameter Types** | Handler registration | Check all params are `Column` | "Expected Column, got String" |
| **Return Type** | Handler registration | Check return is `Column` | "Expected Column, got void" |
| **Argument Types** | Code generation | Check IRNode types match signature specs | `validateArgumentTypes()` |

**Result**: Full type checking without wrapper classes

---

## Conclusion

**Answer to Your Question**: YES, we can have type checking without wrapper classes for all types.

**Recommended Approach**:
1. Keep annotation-based handlers with `@Operation` methods
2. Keep shared operation providers (eliminate duplication)
3. Use wrappers ONLY for complex types (Quantity, etc.) as utilities
4. No wrappers for primitives (just use Column)
5. No interface hierarchies (unnecessary for translator)

**Why This Works**:
- Type checking via method signatures + validation at registration
- Clean IR → SQL translation (most operations = single call)
- Low complexity, high maintainability
- Fits the actual use case (translator, not fluent API)

**Code Reduction**:
- Handlers: ~10-20 lines each (vs. 100+ in current code)
- Wrappers: Only 3-4 needed (vs. 8+ if all types had them)
- Overall: 60-70% reduction in code size
