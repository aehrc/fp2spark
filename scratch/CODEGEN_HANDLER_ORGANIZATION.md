# Handler Organization: Type-Based vs Operation-Based

## The Core Tension

When designing handler organization, there are two natural approaches:

| Approach | Organization | Adding New Operation | Adding New Type |
|----------|-------------|---------------------|-----------------|
| **Type-Based** | One handler per type | Touch many files | Add one file |
| **Operation-Based** | One handler per operation (group) | Touch one file | Touch many files |

This is the classic tension between organizing by type vs. organizing by operation.

---

## Problem: Heavily Overloaded Operations

### Example: Comparison Operators

Comparison operators (`eq`, `ne`, `gt`, `gte`, `lt`, `lte`) work on many types:
- Integer
- Decimal
- String
- Boolean
- Date
- DateTime
- Time
- Quantity (?)
- CodeableConcept (?)

**Type-Based Handler Approach**:
```
IntegerHandler      → eq, ne, gt, gte, lt, lte
DecimalHandler      → eq, ne, gt, gte, lt, lte
StringHandler       → eq, ne, gt, gte, lt, lte
BooleanHandler      → eq, ne
DateHandler         → eq, ne, gt, gte, lt, lte
DateTimeHandler     → eq, ne, gt, gte, lt, lte
TimeHandler         → eq, ne, gt, gte, lt, lte
QuantityHandler     → eq, ne, gt, gte, lt, lte (maybe)
```

**Adding `ne` operator**:
- If using shared provider: Update `CommonOperations.comparisonOps()` → done (all primitives get it)
- If type-specific: Touch 7+ handler files

**Operation-Based Handler Approach**:
```
ComparisonOperationHandler
  → handleInteger(op, args)
  → handleDecimal(op, args)
  → handleString(op, args)
  → handleBoolean(op, args)
  → handleDate(op, args)
  → handleDateTime(op, args)
  → handleTime(op, args)
  → handleQuantity(op, args)
```

**Adding `ne` operator**:
- Add case to each `handleXxx()` method
- All in ONE file
- Easier to ensure consistency

---

## Comparison: Type-Based Handlers

### Structure

```java
// IntegerHandler.java
public class IntegerHandler extends AnnotatedTypeHandler {

    @OperationMappings
    public Stream<NamedMapping> operations() {
        return Stream.concat(
            CommonOperations.arithmeticOps(),     // add, sub, multiply, divide, mod
            CommonOperations.comparisonOps(),     // eq, ne, gt, gte, lt, lte
            CommonOperations.mathOps()            // abs, ceiling, floor, ...
        );
    }

    // Type-specific operations (if any)
    @Operation("toDecimal")
    public Column toDecimal(Column value, CodeGenContext ctx) {
        return value.cast(DataTypes.createDecimalType());
    }
}

// DecimalHandler.java
public class DecimalHandler extends AnnotatedTypeHandler {

    @OperationMappings
    public Stream<NamedMapping> operations() {
        return Stream.concat(
            CommonOperations.arithmeticOps(),
            CommonOperations.comparisonOps(),
            CommonOperations.mathOps()
        );
    }
}

// StringHandler.java
public class StringHandler extends AnnotatedTypeHandler {

    @OperationMappings
    public Stream<NamedMapping> sharedOps() {
        return CommonOperations.comparisonOps();  // eq, ne
    }

    @OperationMappings
    public Stream<NamedMapping> stringOps() {
        return StringOperations.all();  // upper, lower, substring, matches, ...
    }
}

// QuantityHandler.java
public class QuantityHandler extends AnnotatedTypeHandler {

    @Operation("add")
    public Column add(Column left, Column right, CodeGenContext ctx) {
        Quantity q1 = Quantity.of(left);
        Quantity q2 = Quantity.of(right);
        return Quantity.build(q1.value().plus(q2.value()), q1.unit(), q1.system(), q1.code());
    }

    @OperationMappings
    public Stream<NamedMapping> mathOps() {
        return Stream.of(
            unary("abs", q -> Quantity.of(q).mapValue(functions::abs)),
            unary("ceiling", q -> Quantity.of(q).mapValue(functions::ceil))
        );
    }
}
```

### Pros
✅ All operations for a type in one place
✅ Easy to see what a type supports
✅ Natural for type-specific operations (Quantity struct manipulation)
✅ Good locality for type-specific complexity
✅ Shared providers reduce duplication for simple operations

### Cons
❌ Adding heavily overloaded operation requires updating shared provider + verifying all handlers include it
❌ Risk of inconsistency if types forget to include shared provider
❌ Two-step process: define in provider, include in handlers

### Impact of Adding `ne` Operator
1. Add to `CommonOperations.comparisonOps()` → `binary("ne", (a, b) -> a.notEqual(b))`
2. All handlers using `CommonOperations.comparisonOps()` get it automatically
3. **If well-designed: 1 line change**
4. **If not well-designed: 7+ files to touch**

---

## Comparison: Operation-Based Handlers

### Structure

```java
// ComparisonOperationHandler.java
public class ComparisonOperationHandler implements OperationHandler {

    @Override
    public boolean canHandle(String operationName) {
        return Set.of("eq", "ne", "gt", "gte", "lt", "lte").contains(operationName);
    }

    @Override
    public Column handle(String operation, List<Column> args, Type dispatchType, CodeGenContext ctx) {
        Column left = args.get(0);
        Column right = args.get(1);

        // Dispatch by type
        if (dispatchType.equals(Shape.INTEGER) || dispatchType.equals(Shape.DECIMAL)) {
            return handleNumeric(operation, left, right);
        } else if (dispatchType.equals(Shape.STRING)) {
            return handleString(operation, left, right);
        } else if (dispatchType.equals(Shape.DATE)) {
            return handleDate(operation, left, right);
        } else if (dispatchType.equals(Shape.DATETIME)) {
            return handleDateTime(operation, left, right);
        } else if (dispatchType.equals(Shape.QUANTITY)) {
            return handleQuantity(operation, left, right, ctx);
        } else {
            throw new UnsupportedOperationException("Comparison not supported for type: " + dispatchType);
        }
    }

    private Column handleNumeric(String operation, Column left, Column right) {
        return switch (operation) {
            case "eq" -> left.equalTo(right);
            case "ne" -> left.notEqual(right);
            case "gt" -> left.gt(right);
            case "gte" -> left.geq(right);
            case "lt" -> left.lt(right);
            case "lte" -> left.leq(right);
            default -> throw new IllegalArgumentException("Unknown operation: " + operation);
        };
    }

    private Column handleString(String operation, Column left, Column right) {
        return switch (operation) {
            case "eq" -> left.equalTo(right);
            case "ne" -> left.notEqual(right);
            case "gt" -> left.gt(right);
            case "gte" -> left.geq(right);
            case "lt" -> left.lt(right);
            case "lte" -> left.leq(right);
            default -> throw new IllegalArgumentException("Unknown operation: " + operation);
        };
    }

    private Column handleQuantity(String operation, Column left, Column right, CodeGenContext ctx) {
        Quantity q1 = Quantity.of(left);
        Quantity q2 = Quantity.of(right);

        // Compare values (ignoring units for now - would need unit conversion)
        return switch (operation) {
            case "eq" -> q1.value().equalTo(q2.value());
            case "ne" -> q1.value().notEqual(q2.value());
            case "gt" -> q1.value().gt(q2.value());
            case "gte" -> q1.value().geq(q2.value());
            case "lt" -> q1.value().lt(q2.value());
            case "lte" -> q1.value().leq(q2.value());
            default -> throw new IllegalArgumentException("Unknown operation: " + operation);
        };
    }

    // Similar methods for Date, DateTime, etc.
}

// ArithmeticOperationHandler.java
public class ArithmeticOperationHandler implements OperationHandler {

    @Override
    public boolean canHandle(String operationName) {
        return Set.of("add", "sub", "multiply", "divide", "mod").contains(operationName);
    }

    @Override
    public Column handle(String operation, List<Column> args, Type dispatchType, CodeGenContext ctx) {
        // Similar type dispatch...
    }
}

// StringOperationHandler.java
public class StringOperationHandler implements OperationHandler {

    @Override
    public boolean canHandle(String operationName) {
        return Set.of("upper", "lower", "substring", "startsWith", "endsWith", "matches", "length").contains(operationName);
    }

    @Override
    public Column handle(String operation, List<Column> args, Type dispatchType, CodeGenContext ctx) {
        // String-specific operations (no type dispatch needed)
        Column str = args.get(0);

        return switch (operation) {
            case "upper" -> functions.upper(str);
            case "lower" -> functions.lower(str);
            case "substring" -> functions.substring(str, args.get(1), args.get(2));
            // ...
        };
    }
}

// QuantityOperationHandler.java
public class QuantityOperationHandler implements OperationHandler {

    @Override
    public boolean canHandle(String operationName) {
        return Set.of("getValue", "getUnit", "toDecimal").contains(operationName);
    }

    @Override
    public Column handle(String operation, List<Column> args, Type dispatchType, CodeGenContext ctx) {
        // Quantity-specific operations (no type dispatch needed)
        Quantity q = Quantity.of(args.get(0));

        return switch (operation) {
            case "getValue" -> q.value();
            case "getUnit" -> q.unit();
            case "toDecimal" -> q.value();
            default -> throw new IllegalArgumentException("Unknown operation: " + operation);
        };
    }
}
```

### Pros
✅ Adding new heavily overloaded operation touches ONE file
✅ All type variants visible together → consistency easier
✅ Clear which operations need type dispatch vs. don't
✅ Natural for operations with uniform behavior across types

### Cons
❌ Type-specific logic scattered across operation handlers
❌ Hard to see all operations supported by a type
❌ Large handlers for operations with many type variants
❌ Type-specific complexity (Quantity fields) spread out

### Impact of Adding `ne` Operator
1. Add case to `ComparisonOperationHandler.handleNumeric()` → 1 line
2. Add case to `ComparisonOperationHandler.handleString()` → 1 line
3. Add case to `ComparisonOperationHandler.handleQuantity()` → 1 line
4. **Total: 1 file, ~6 lines**

---

## Hybrid Approach: Best of Both Worlds?

### Idea: Use Dispatch Strategy to Determine Organization

```java
// For operations that dispatch by FIRST_ARG_TYPE (comparisons)
// → Use operation-based handlers
ComparisonOperationHandler  // eq, ne, gt, gte, lt, lte for all types
ArithmeticOperationHandler  // add, sub, multiply, divide for all numeric types
MathOperationHandler        // abs, ceiling, floor for all numeric types

// For operations that dispatch by RESULT_TYPE (type-specific)
// → Use type-based handlers
IntegerHandler              // toDecimal, toInteger, ...
StringHandler               // upper, lower, substring, matches, ...
QuantityHandler             // getValue, getUnit, toDecimal, ...
DateTimeHandler             // year, month, day, ...

// For operations with GENERIC dispatch (polymorphic)
// → Use standalone handlers
CollectionOperationHandler  // where, select, all, any, count, ...
```

### Structure

```java
// Operation-based for heavily overloaded ops
public class ComparisonOperationHandler implements OperationHandler {
    // Handles: eq, ne, gt, gte, lt, lte
    // Dispatches internally by type
}

// Type-based for type-specific ops
public class StringHandler extends AnnotatedTypeHandler {
    @OperationMappings
    public Stream<NamedMapping> operations() {
        return StringOperations.all();
    }
}

public class QuantityHandler extends AnnotatedTypeHandler {
    @Operation("getValue")
    public Column getValue(Column quantity, CodeGenContext ctx) {
        return Quantity.of(quantity).value();
    }
}

// Registry decides which to use
public class HandlerRegistry {
    private final Map<Type, TypeHandler> typeHandlers;
    private final List<OperationHandler> operationHandlers;

    public Column handleOperation(String opName, List<Column> args, Type dispatchType, CodeGenContext ctx) {
        // First check operation-based handlers
        for (OperationHandler handler : operationHandlers) {
            if (handler.canHandle(opName)) {
                return handler.handle(opName, args, dispatchType, ctx);
            }
        }

        // Fall back to type-based handler
        TypeHandler typeHandler = typeHandlers.get(dispatchType);
        if (typeHandler != null && typeHandler.canHandle(opName)) {
            return typeHandler.handle(opName, args, ctx);
        }

        throw new UnsupportedOperationException("No handler for operation: " + opName);
    }
}
```

### Pros
✅ Operations grouped by natural organization
✅ Heavily overloaded ops in one place (comparisons, arithmetic)
✅ Type-specific ops with their type (string ops, quantity ops)
✅ Clear separation of concerns
✅ Easy to add new operations of either kind

### Cons
❌ More complex (two handler types)
❌ Need to decide which approach for new operations
❌ Need precedence rules in registry (operation-based first? type-based first?)

---

## Decision Matrix

### When to Use Type-Based Handlers

Use when operation is:
- **Type-specific**: Only makes sense for one or few types (e.g., `substring` for String)
- **Complex type manipulation**: Needs deep type knowledge (e.g., Quantity field manipulation)
- **Result-type dispatch**: Handler determined by result type
- **Low fan-out**: Only a few types support it

**Examples**:
- String operations: `upper`, `lower`, `substring`, `matches`
- Quantity operations: `getValue`, `getUnit`, `toDecimal`
- DateTime operations: `year`, `month`, `day`
- Type conversions: `toInteger`, `toDecimal`, `toString`

### When to Use Operation-Based Handlers

Use when operation is:
- **Heavily overloaded**: Works on many types with similar behavior
- **Uniform across types**: Implementation is similar for all types
- **First-arg-type dispatch**: Handler determined by argument type
- **High fan-out**: Many types support it

**Examples**:
- Comparison operations: `eq`, `ne`, `gt`, `gte`, `lt`, `lte`
- Arithmetic operations: `add`, `sub`, `multiply`, `divide`
- Math operations: `abs`, `ceiling`, `floor`, `round`

### When to Use Generic Handlers

Use when operation is:
- **Polymorphic**: Works on any type (via generics)
- **Collection-based**: Operates on collections regardless of element type
- **Generic dispatch**: No type-specific logic

**Examples**:
- Collection operations: `where`, `select`, `all`, `any`, `count`, `first`, `last`

---

## Recommendation

### For Your Codebase

Given that you have:
- Many comparison operators across many types
- Some heavily type-specific operations (Quantity, String)
- Need to add new operations over time

**Recommended Approach**: **Hybrid**

**Organization**:
```
com.example.fhirpath.codegen.spark.handlers
├── operation/                    # Operation-based handlers
│   ├── ComparisonOperationHandler.java
│   ├── ArithmeticOperationHandler.java
│   └── MathOperationHandler.java
├── type/                         # Type-based handlers
│   ├── StringHandler.java
│   ├── QuantityHandler.java
│   ├── DateTimeHandler.java
│   └── CodeableConceptHandler.java
└── generic/                      # Generic handlers
    └── CollectionOperationHandler.java
```

**Registry**:
```java
public class HandlerRegistry {
    public HandlerRegistry() {
        // Register operation-based handlers (checked first)
        registerOperationHandler(new ComparisonOperationHandler());
        registerOperationHandler(new ArithmeticOperationHandler());
        registerOperationHandler(new MathOperationHandler());

        // Register type-based handlers (checked second)
        registerTypeHandler(Shape.STRING, new StringHandler(sigRegistry));
        registerTypeHandler(Shape.QUANTITY, new QuantityHandler(sigRegistry));
        registerTypeHandler(Shape.DATETIME, new DateTimeHandler(sigRegistry));

        // Register generic handlers (checked last)
        registerGenericHandler(new CollectionOperationHandler());
    }
}
```

**Impact**:
- Adding `ne` operator: 1 file (ComparisonOperationHandler)
- Adding `replace` string operation: 1 file (StringHandler)
- Adding new type: 1 file (new type handler, reuses operation handlers)

---

## Summary

| Approach | Best For | Adding Operation | Adding Type |
|----------|----------|------------------|-------------|
| **Type-Based** | Type-specific ops | Touch many files (unless shared provider) | Add 1 file |
| **Operation-Based** | Heavily overloaded ops | Touch 1 file | Touch many files |
| **Hybrid** | Mixed workload | Touch 1 file (right org) | Add 1 file + reuse |

**Final Answer**: Use **Hybrid approach** with:
- Operation-based handlers for comparisons, arithmetic, math (heavily overloaded)
- Type-based handlers for string, quantity, datetime ops (type-specific)
- Generic handlers for collections (polymorphic)

This gives you:
✅ Best locality for each kind of operation
✅ Easy to add heavily overloaded operations (1 file)
✅ Easy to add type-specific operations (1 file)
✅ Easy to add new types (reuse operation handlers)
✅ Clear organization principle
