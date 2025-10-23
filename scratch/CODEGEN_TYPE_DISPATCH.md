# Type Dispatch Strategy for Operation Implementation

**Date**: 2025-01-22
**Extension**: Builds on `CODEGEN_REFACTORING_PROPOSAL.md`
**Purpose**: Define clear rules for determining which TypeHandler to use for each operation

---

## Table of Contents

1. [Problem Analysis](#problem-analysis)
2. [Type Dispatch Categories](#type-dispatch-categories)
3. [Dispatch Rules](#dispatch-rules)
4. [Implementation Strategy](#implementation-strategy)
5. [Edge Cases & Solutions](#edge-cases--solutions)
6. [Complete Examples](#complete-examples)

---

## Problem Analysis

### Current Inconsistency in SparkCodeGenerator

**Examining the current code** (`SparkCodeGenerator.java:80-139`):

```java
private Column evaluateOperation(String name, List<Column> args,
                                  Type resultType, List<IRNode> argNodes) {
    return switch (name) {
        // GROUP 1: Uses RESULT TYPE
        case "add" -> evaluateAdd(args, resultType);
        case "sub" -> evaluateSub(args, resultType);
        case "multiply" -> evaluateMultiply(args, resultType);
        case "abs" -> evaluateAbs(args, resultType);

        // GROUP 2: Uses FIRST ARGUMENT TYPE
        case "gt" -> evaluateGreaterThan(args, argNodes.get(0).getType());
        case "lt" -> evaluateLessThan(args, argNodes.get(0).getType());

        // GROUP 3: Uses NEITHER (hardcoded logic)
        case "upper" -> upper(args.get(0));
        case "and" -> args.get(0).and(args.get(1));

        // GROUP 4: Uses SINGULARITY from argument
        case "count" -> evaluateCount(args.get(0), argNodes.get(0).isSingular());
    };
}
```

**Why the inconsistency?**

Let's trace through specific examples:

#### Example 1: Addition (`add`)

```java
// Integer addition: 5 + 3 -> 8
Operation: add
Arguments: [Integer(5), Integer(3)]
Result: Integer(8)

Current code: evaluateAdd(args, resultType)
              → resultType = INTEGER
              → Dispatches to INTEGER case

✅ Works because: resultType == argType
```

```java
// Quantity addition: 5kg + 3kg -> 8kg
Operation: add
Arguments: [Quantity(5, "kg"), Quantity(3, "kg")]
Result: Quantity(8, "kg")

Current code: evaluateAdd(args, resultType)
              → resultType = QUANTITY
              → Dispatches to QUANTITY case

✅ Works because: resultType == argType
```

```java
// DateTime + Quantity: 2024-01-01 + 5 days -> 2024-01-06
Operation: add
Arguments: [DateTime(2024-01-01), Quantity(5, "days")]
Result: DateTime(2024-01-06)

Current code: evaluateAdd(args, resultType)
              → resultType = DATE_TIME
              → Dispatches to DATE_TIME case

✅ Works because: We want DateTime handler (result type matches "primary" type)
```

**Conclusion for `add`**: Result type works because it identifies the "primary" operand type.

#### Example 2: Comparison (`gt`)

```java
// Integer comparison: 5 > 3 -> true
Operation: gt
Arguments: [Integer(5), Integer(3)]
Result: Boolean(true)

If using resultType:
  → resultType = BOOLEAN
  → Would dispatch to BOOLEAN handler ❌ WRONG!
  → Boolean handler doesn't know how to compare integers!

Current code: evaluateGreaterThan(args, argNodes.get(0).getType())
              → argType = INTEGER
              → Dispatches to INTEGER case
              → ✅ Correct!
```

```java
// Quantity comparison: 5kg > 3kg -> true
Operation: gt
Arguments: [Quantity(5, "kg"), Quantity(3, "kg")]
Result: Boolean(true)

If using resultType:
  → resultType = BOOLEAN
  → Would dispatch to BOOLEAN handler ❌ WRONG!

Current code: evaluateGreaterThan(args, argNodes.get(0).getType())
              → argType = QUANTITY
              → Dispatches to QUANTITY case
              → ✅ Correct!
```

**Conclusion for `gt`**: MUST use argument type, not result type!

### The Pattern Emerges

| Operation Category | Example | Args → Result | Dispatch Key |
|-------------------|---------|---------------|--------------|
| **Type-preserving** | `abs`, `negate` | `T → T` | Result type (= arg type) |
| **Homogeneous binary** | `add`, `multiply` | `(T, T) → T` | Result type (= arg type) |
| **Heterogeneous arithmetic** | DateTime `+` Quantity | `(T, U) → T` | Result type (= primary arg) |
| **Comparison** | `gt`, `lt`, `eq` | `(T, T) → Boolean` | **Arg type** (NOT result!) |
| **Type conversion** | `toString`, `toInteger` | `T → U` | **Arg type** (source type) |
| **Polymorphic** | `count`, `exists`, `first` | `Collection<T> → U` | **Generic** (no specific handler) |

---

## Type Dispatch Categories

### Category 1: Type-Preserving Operations

**Signature**: `T → T`

**Examples**: `abs`, `negate`, `ceiling`, `floor`, `upper`, `lower`

**Rule**: Dispatch based on **result type** (which equals argument type)

**Why**: Operation belongs to the type itself - "how does Integer implement abs?"

```java
// abs(Integer) -> Integer
Operation("abs", [Integer(5)], INTEGER)
→ Dispatch key: INTEGER
→ Handler: IntegerHandler

// abs(Quantity) -> Quantity
Operation("abs", [Quantity(5, "kg")], QUANTITY)
→ Dispatch key: QUANTITY
→ Handler: QuantityHandler
```

### Category 2: Homogeneous Binary Operations

**Signature**: `(T, T) → T`

**Examples**: `add`, `sub`, `multiply`, `divide` (when both args same type)

**Rule**: Dispatch based on **result type** (which equals both argument types)

**Why**: Operation belongs to the type - "how does Quantity implement addition?"

```java
// Integer + Integer -> Integer
Operation("add", [Integer(5), Integer(3)], INTEGER)
→ Dispatch key: INTEGER
→ Handler: IntegerHandler

// Quantity + Quantity -> Quantity
Operation("add", [Quantity(5, "kg"), Quantity(3, "kg")], QUANTITY)
→ Dispatch key: QUANTITY
→ Handler: QuantityHandler
```

### Category 3: Heterogeneous Operations

**Signature**: `(T, U) → V` where types differ

**Examples**:
- `DateTime + Quantity → DateTime`
- `String * Integer → String` (repeat)

**Rule**: Dispatch based on **PRIMARY argument type** (usually = result type)

**Why**: Operation belongs to primary type - "how does DateTime handle addition with Quantity?"

```java
// DateTime + Quantity -> DateTime
Operation("add", [DateTime(...), Quantity(5, "days")], DATE_TIME)
→ Dispatch key: DATE_TIME (result type = primary arg type)
→ Handler: DateTimeHandler

// String * Integer -> String (hypothetical: repeat string)
Operation("multiply", [String("abc"), Integer(3)], STRING)
→ Dispatch key: STRING (result type = primary arg type)
→ Handler: StringHandler
```

### Category 4: Comparison Operations

**Signature**: `(T, T) → Boolean`

**Examples**: `gt`, `lt`, `geq`, `leq`, `eq`, `neq`

**Rule**: Dispatch based on **ARGUMENT TYPE**, NOT result type!

**Why**: Comparison logic belongs to the compared type, not Boolean!

```java
// Integer > Integer -> Boolean
Operation("gt", [Integer(5), Integer(3)], BOOLEAN)
→ Dispatch key: INTEGER (arg type, NOT result type!)
→ Handler: IntegerHandler

// Quantity > Quantity -> Boolean
Operation("gt", [Quantity(5, "kg"), Quantity(3, "kg")], BOOLEAN)
→ Dispatch key: QUANTITY (arg type, NOT result type!)
→ Handler: QuantityHandler
```

**Anti-pattern**:
```java
// ❌ WRONG: Using result type
Operation("gt", [Quantity(...), Quantity(...)], BOOLEAN)
→ Dispatch key: BOOLEAN
→ Handler: BooleanHandler
→ ❌ BooleanHandler doesn't know how to compare Quantities!
```

### Category 5: Type Conversion Operations

**Signature**: `T → U` where `T ≠ U`

**Examples**: `toString`, `toInteger`, `toDecimal`, `getValue()` (FHIR → System)

**Rule**: Dispatch based on **SOURCE type** (argument type)

**Why**: Conversion logic belongs to source type - "how does DateTime convert to String?"

```java
// DateTime.toString() -> String
Operation("toString", [DateTime(...)], STRING)
→ Dispatch key: DATE_TIME (source type, NOT result type!)
→ Handler: DateTimeHandler

// Quantity.toDecimal() -> Decimal (get value)
Operation("toDecimal", [Quantity(5, "kg")], DECIMAL)
→ Dispatch key: QUANTITY (source type)
→ Handler: QuantityHandler
```

### Category 6: Polymorphic Operations

**Signature**: `Collection<T> → U` (works on ANY type)

**Examples**: `count`, `exists`, `empty`, `first`

**Rule**: **No type-specific dispatch** - use generic implementation or inline

**Why**: Logic is type-agnostic - just manipulates collections

```java
// count(Collection<Integer>) -> Integer
Operation("count", [Collection<Integer>], INTEGER)
→ No dispatch needed - generic implementation
→ Or dispatch to: CollectionOperations.count()

// exists(Collection<Quantity>) -> Boolean
Operation("exists", [Collection<Quantity>], BOOLEAN)
→ No dispatch needed - same logic for all types
```

---

## Dispatch Rules

### Rule 1: Default Dispatch (Type-Preserving & Homogeneous)

**For operations where**: Result type = Argument type(s)

**Dispatch key**: `operation.getType()` (result type)

**Examples**: `abs`, `add` (Integer + Integer), `negate`, `upper`

### Rule 2: Comparison Dispatch

**For operations where**: Result type is Boolean but compares values of type T

**Dispatch key**: `operation.args().get(0).getType()` (first argument type)

**Examples**: `gt`, `lt`, `geq`, `leq`, `eq`, `neq`

**Detection**: `resultType == BOOLEAN && arity >= 2`

### Rule 3: Heterogeneous Dispatch

**For operations where**: Argument types differ

**Dispatch key**: Result type (identifies primary operand)

**Examples**: `DateTime + Quantity`, `String * Integer`

**Detection**: `arg0Type != arg1Type`

### Rule 4: Conversion Dispatch

**For operations where**: Result type ≠ Argument type AND unary

**Dispatch key**: `operation.args().get(0).getType()` (source type)

**Examples**: `toString`, `toInteger`, `getValue()`

**Detection**: `arity == 1 && argType != resultType`

### Rule 5: Polymorphic (No Dispatch)

**For operations where**: Logic is type-agnostic

**Dispatch**: Use generic implementation directly

**Examples**: `count`, `exists`, `empty`, `first`, `where`, `select`

### Priority Order

When multiple rules could apply, use this priority:

1. **Polymorphic** (if operation is known to be polymorphic)
2. **Comparison** (if result is Boolean and binary/ternary)
3. **Conversion** (if unary and result type ≠ arg type)
4. **Heterogeneous** (if arg types differ)
5. **Default** (type-preserving/homogeneous)

---

## Implementation Strategy

### Approach 1: Operation Metadata

Add dispatch strategy to operation registry:

```java
public enum DispatchStrategy {
    /** Dispatch on result type (default) */
    RESULT_TYPE,

    /** Dispatch on first argument type (comparisons, conversions) */
    FIRST_ARG_TYPE,

    /** No dispatch - use generic implementation (polymorphic) */
    GENERIC
}

public record SignatureDefinition(
    List<ParamSpec> parameters,
    ResultTypeSpec resultSpec,
    int minArity,
    LambdaBindingStrategy lambdaBinding,
    DispatchStrategy dispatchStrategy  // NEW!
) {
    // ...
}
```

**Usage in Registry**:
```java
// Arithmetic: dispatch on result type
register("add",
    forTypes(NUMERIC_WITH_QUANTITY).define(Signatures::binaryOp)
        .withDispatch(DispatchStrategy.RESULT_TYPE)
);

// Comparisons: dispatch on argument type
register("gt",
    forTypes(COMPARABLE).define(Signatures::comparisonOp)
        .withDispatch(DispatchStrategy.FIRST_ARG_TYPE)
);

// Polymorphic: no dispatch
register("count",
    Signatures.collectionAggregator(ANY, INTEGER)
        .withDispatch(DispatchStrategy.GENERIC)
);
```

### Approach 2: Convention-Based Dispatch

Infer dispatch strategy from operation name and signature:

```java
public class DispatchKeyResolver {

    public static Type resolveDispatchKey(Operation op) {
        String opName = op.name();
        Type resultType = op.getType();
        List<Type> argTypes = op.args().stream()
            .map(IRNode::getType)
            .toList();

        // Rule 1: Polymorphic operations (known list)
        if (POLYMORPHIC_OPS.contains(opName)) {
            return null;  // No type-specific dispatch
        }

        // Rule 2: Comparison operations
        if (COMPARISON_OPS.contains(opName) && resultType == Types.BOOLEAN) {
            return argTypes.get(0);  // First argument type
        }

        // Rule 3: Conversion operations (unary, different types)
        if (argTypes.size() == 1 && !argTypes.get(0).equals(resultType)) {
            return argTypes.get(0);  // Source type
        }

        // Rule 4: Default - use result type
        return resultType;
    }

    private static final Set<String> POLYMORPHIC_OPS = Set.of(
        "count", "exists", "empty", "first", "last",
        "where", "select", "all", "any"
    );

    private static final Set<String> COMPARISON_OPS = Set.of(
        "gt", "lt", "geq", "leq", "eq", "neq"
    );
}
```

### Approach 3: Signature-Based Dispatch (Recommended)

Encode dispatch strategy in signature definition itself:

```java
public class Signatures {

    /**
     * Comparison operation signature.
     * Returns Boolean but dispatches on argument type.
     */
    public static SignatureDefinition comparisonOp(Type inputType) {
        return new SignatureDefinition(
            List.of(ParamSpec.single(inputType), ParamSpec.single(inputType)),
            ResultTypeSpec.single(BOOLEAN),
            2,
            null,
            DispatchStrategy.FIRST_ARG_TYPE  // Explicit dispatch strategy
        );
    }

    /**
     * Binary operation that preserves type.
     * Dispatches on result type (= argument type).
     */
    public static SignatureDefinition binaryOp(Type type) {
        return new SignatureDefinition(
            List.of(ParamSpec.single(type), ParamSpec.single(type)),
            ResultTypeSpec.single(type),
            2,
            null,
            DispatchStrategy.RESULT_TYPE
        );
    }

    /**
     * Collection aggregator - polymorphic.
     * No type-specific dispatch needed.
     */
    public static SignatureDefinition collectionAggregator(Type inputType, Type resultType) {
        return new SignatureDefinition(
            List.of(ParamSpec.collection(inputType)),
            ResultTypeSpec.single(resultType),
            1,
            null,
            DispatchStrategy.GENERIC
        );
    }
}
```

---

## Implementation in OperationImplementationRegistry

### Enhanced Registry with Dispatch Logic

```java
public class OperationImplementationRegistry {

    private final Map<RegistryKey, OperationImplementation> implementations = new HashMap<>();
    private final Map<PrimitiveType, TypeHandler> typeHandlers = new HashMap<>();
    private final Map<String, OperationImplementation> genericImplementations = new HashMap<>();

    /**
     * Get implementation for operation.
     * Uses dispatch strategy from operation's resolved signature.
     */
    public OperationImplementation getImplementation(Operation operation) {
        // Get dispatch strategy from signature
        DispatchStrategy strategy = operation.signature().dispatchStrategy();

        return switch (strategy) {
            case RESULT_TYPE -> getByType(operation.name(), operation.getType());
            case FIRST_ARG_TYPE -> getByType(operation.name(),
                                             operation.args().get(0).getType());
            case GENERIC -> getGeneric(operation.name());
        };
    }

    private OperationImplementation getByType(String opName, Type type) {
        RegistryKey key = new RegistryKey(opName, type);
        OperationImplementation impl = implementations.get(key);

        if (impl == null) {
            throw new IllegalArgumentException(
                String.format("No implementation for: %s on type %s", opName, type)
            );
        }

        return impl;
    }

    private OperationImplementation getGeneric(String opName) {
        OperationImplementation impl = genericImplementations.get(opName);

        if (impl == null) {
            throw new IllegalArgumentException(
                String.format("No generic implementation for: %s", opName)
            );
        }

        return impl;
    }

    /**
     * Register type-specific implementation.
     */
    public void register(String opName, Type type, OperationImplementation impl) {
        implementations.put(new RegistryKey(opName, type), impl);
    }

    /**
     * Register generic (polymorphic) implementation.
     */
    public void registerGeneric(String opName, OperationImplementation impl) {
        genericImplementations.put(opName, impl);
    }

    private record RegistryKey(String operationName, Type type) {}
}
```

### Updated SparkCodeGenerator

```java
public class SparkCodeGenerator implements IRNodeVisitor<Column> {

    private final OperationImplementationRegistry implementationRegistry;
    private final UdfRegistry udfRegistry;

    @Override
    public Column visitOperation(Operation op) {
        // Evaluate arguments
        List<Column> argColumns = op.args().stream()
            .map(arg -> arg instanceof Lambda ? null : arg.accept(this))
            .toList();

        // Get implementation using dispatch strategy
        OperationImplementation implementation =
            implementationRegistry.getImplementation(op);

        // Create context
        CodeGenContext context = new CodeGenContext(
            op.getType(),
            op.args().stream().map(IRNode::getType).toList(),
            thisColumn,
            udfRegistry,
            this
        );

        // Generate code
        return implementation.generate(argColumns, context);
    }
}
```

---

## Edge Cases & Solutions

### Edge Case 1: Overloaded Operations with Different Dispatch

**Problem**: Same operation name, different dispatch strategies for different signatures

```java
// String concatenation: String + String -> String
// Dispatch on result type (= arg type)
"abc" + "def" -> "abcdef"

// String comparison: String > String -> Boolean
// Dispatch on argument type (NOT result type)
"abc" > "def" -> false
```

**Solution**: Signature-specific dispatch

```java
// In OperationRegistry
register("add",
    Signatures.binaryOp(STRING)  // STRING + STRING -> STRING
        .withDispatch(RESULT_TYPE)
);

register("gt",
    Signatures.comparisonOp(STRING)  // STRING > STRING -> BOOLEAN
        .withDispatch(FIRST_ARG_TYPE)
);
```

Registry lookup uses **both** operation name and signature to find correct dispatch strategy.

### Edge Case 2: Multi-Type Operations

**Problem**: Operation works on multiple unrelated types

```java
// abs() works on Integer, Decimal, Quantity
abs(Integer(5)) -> Integer(5)
abs(Decimal(5.5)) -> Decimal(5.5)
abs(Quantity(5, "kg")) -> Quantity(5, "kg")
```

**Solution**: Register separately for each type, all using same dispatch strategy

```java
// CommonOperations provides shared implementation
for (Type type : List.of(INTEGER, DECIMAL)) {
    register("abs", type, DirectMapping.unary(functions::abs));
}

// QuantityHandler provides custom implementation
register("abs", QUANTITY, new TypeHandlerDelegate(quantityHandler, "abs"));
```

All use `RESULT_TYPE` dispatch, but different implementations.

### Edge Case 3: Polymorphic with Partial Type-Specific

**Problem**: Operation is mostly polymorphic but has type-specific optimizations

```java
// first() is polymorphic - works on any collection
// But implementation differs for singular vs collection

first(Integer(5)) -> Integer(5)  // Singular: return itself
first([1, 2, 3]) -> 1            // Collection: extract first element
```

**Solution**: Use `GENERIC` dispatch with context-aware implementation

```java
registerGeneric("first", ComplexExpression.of((args, ctx) -> {
    boolean isSingular = ctx.argTypes().get(0).isSingular();
    Column child = args.get(0);

    if (isSingular) {
        return child;  // Singular: return itself
    } else {
        return functions.get(child, lit(0));  // Collection: first element
    }
}));
```

### Edge Case 4: Type Coercion

**Problem**: Arguments coerced before operation

```java
// Integer + Decimal -> Decimal (Integer coerced to Decimal)
5 + 3.5 -> 8.5

// Which handler? Integer or Decimal?
```

**Solution**: Dispatch on **result type** (which matches the coerced type)

```java
Operation("add", [Integer(5), Decimal(3.5)], DECIMAL)
→ Dispatch key: DECIMAL (result type)
→ Handler: DecimalHandler
→ Arguments already coerced by analyzer
```

The **analyzer** handles coercion and creates appropriate IR. Code generator just sees coerced types.

### Edge Case 5: Equals (Special Case)

**Problem**: `equals` is implemented as special IR node, not Operation

```java
// Currently: Equals IR node with custom visitEquals()
5 = 5 -> true
```

**Solution**: Keep as special IR node (already correct in current design)

Alternatively, if moved to Operation:
```java
register("equals",
    // Dispatch on FIRST_ARG_TYPE (like comparisons)
    Signatures.comparisonOp(ANY)
        .withDispatch(FIRST_ARG_TYPE)
);
```

---

## Complete Examples

### Example 1: Integer Operations

```java
// abs(Integer) -> Integer
Operation("abs", [Integer(-5)], INTEGER)

Dispatch:
  strategy = RESULT_TYPE (from signature)
  key = INTEGER (result type)
  handler = IntegerHandler
  implementation = CommonOperations.mathOps() -> abs

Generated code: abs(lit(-5))
```

```java
// 5 > 3 -> Boolean
Operation("gt", [Integer(5), Integer(3)], BOOLEAN)

Dispatch:
  strategy = FIRST_ARG_TYPE (from comparison signature)
  key = INTEGER (first arg type, NOT result type!)
  handler = IntegerHandler
  implementation = CommonOperations.comparisonOps() -> gt

Generated code: lit(5).gt(lit(3))
```

### Example 2: Quantity Operations

```java
// abs(Quantity) -> Quantity
Operation("abs", [Quantity(5, "kg")], QUANTITY)

Dispatch:
  strategy = RESULT_TYPE
  key = QUANTITY
  handler = QuantityHandler
  implementation = QuantityHandler.abs()

Generated code: struct(abs(value), unit, system, code)
```

```java
// Quantity > Quantity -> Boolean
Operation("gt", [Quantity(5, "kg"), Quantity(3, "kg")], BOOLEAN)

Dispatch:
  strategy = FIRST_ARG_TYPE (comparison operation)
  key = QUANTITY (first arg type, NOT Boolean!)
  handler = QuantityHandler
  implementation = QuantityHandler.greaterThan()

Generated code:
  if (udfRegistry.has("quantityCompare"))
    call_function("quantityCompare", left, right).gt(0)
  else
    left.getField("value").gt(right.getField("value"))
```

### Example 3: DateTime + Quantity

```java
// DateTime + Quantity -> DateTime
Operation("add",
    [DateTime(2024-01-01), Quantity(5, "days")],
    DATE_TIME)

Dispatch:
  strategy = RESULT_TYPE (heterogeneous, but result = primary type)
  key = DATE_TIME (result type identifies primary operand)
  handler = DateTimeHandler
  implementation = DateTimeHandler.addQuantity()

Generated code:
  if (udfRegistry.has("dateTimeAdd"))
    call_function("dateTimeAdd", dateTime, quantity)
  else
    dateTime.plus(quantity.getField("value"))
```

### Example 4: Polymorphic (count)

```java
// count(Collection<Quantity>) -> Integer
Operation("count", [Collection<Quantity>], INTEGER)

Dispatch:
  strategy = GENERIC (polymorphic operation)
  key = (none - generic implementation)
  handler = (none)
  implementation = CollectionOperations.count()

Generated code:
  when(collection.isNotNull(), size(collection))
    .otherwise(lit(0))
```

### Example 5: String Operations

```java
// upper(String) -> String
Operation("upper", [String("hello")], STRING)

Dispatch:
  strategy = RESULT_TYPE
  key = STRING
  handler = StringHandler
  implementation = StringOperations.basicStringOps() -> upper

Generated code: upper(lit("hello"))
```

```java
// String > String -> Boolean
Operation("gt", [String("abc"), String("def")], BOOLEAN)

Dispatch:
  strategy = FIRST_ARG_TYPE (comparison)
  key = STRING (first arg, NOT Boolean!)
  handler = StringHandler
  implementation = CommonOperations.comparisonOps() -> gt

Generated code: lit("abc").gt(lit("def"))
```

---

## Summary Table

| Operation | Signature | Result Type | Dispatch Key | Handler |
|-----------|-----------|-------------|--------------|---------|
| `abs(Integer)` | `Integer → Integer` | INTEGER | INTEGER (result) | IntegerHandler |
| `abs(Quantity)` | `Quantity → Quantity` | QUANTITY | QUANTITY (result) | QuantityHandler |
| `add(Integer, Integer)` | `(Integer, Integer) → Integer` | INTEGER | INTEGER (result) | IntegerHandler |
| `add(Quantity, Quantity)` | `(Quantity, Quantity) → Quantity` | QUANTITY | QUANTITY (result) | QuantityHandler |
| `add(DateTime, Quantity)` | `(DateTime, Quantity) → DateTime` | DATE_TIME | DATE_TIME (result) | DateTimeHandler |
| `gt(Integer, Integer)` | `(Integer, Integer) → Boolean` | BOOLEAN | **INTEGER** (arg!) | IntegerHandler |
| `gt(Quantity, Quantity)` | `(Quantity, Quantity) → Boolean` | BOOLEAN | **QUANTITY** (arg!) | QuantityHandler |
| `toString(DateTime)` | `DateTime → String` | STRING | **DATE_TIME** (arg!) | DateTimeHandler |
| `count(Collection<T>)` | `Collection<T> → Integer` | INTEGER | **(generic)** | CollectionOps |
| `upper(String)` | `String → String` | STRING | STRING (result) | StringHandler |

---

## Recommendation

**Use Approach 3: Signature-Based Dispatch**

**Rationale**:
1. ✅ **Explicit**: Dispatch strategy declared in signature
2. ✅ **Type-safe**: Compiler ensures consistency
3. ✅ **Flexible**: Easy to add new strategies
4. ✅ **Self-documenting**: Clear from signature definition
5. ✅ **Centralized**: All dispatch logic in one place

**Implementation**:
```java
public enum DispatchStrategy {
    RESULT_TYPE,      // Default: abs, add, multiply
    FIRST_ARG_TYPE,   // Comparisons, conversions
    GENERIC           // Polymorphic operations
}

// In signature definition
public record SignatureDefinition(
    List<ParamSpec> parameters,
    ResultTypeSpec resultSpec,
    int minArity,
    LambdaBindingStrategy lambdaBinding,
    DispatchStrategy dispatchStrategy
) { ... }

// In Signatures factory
public static SignatureDefinition comparisonOp(Type inputType) {
    return new SignatureDefinition(
        List.of(ParamSpec.single(inputType), ParamSpec.single(inputType)),
        ResultTypeSpec.single(BOOLEAN),
        2,
        null,
        DispatchStrategy.FIRST_ARG_TYPE  // Explicit!
    );
}
```

**Migration**:
1. Add `DispatchStrategy` enum
2. Add `dispatchStrategy` field to `SignatureDefinition`
3. Update `Signatures` factory methods to specify strategy
4. Update `OperationImplementationRegistry` to use strategy
5. Update all signature definitions in `OperationRegistry`

**Estimated effort**: 4-6 hours

---

## Conclusion

**Current code is inconsistent** because:
- Some operations use result type
- Some use argument type
- No clear principle

**Solution**:
- **Default**: Dispatch on result type (type-preserving, homogeneous)
- **Comparisons**: Dispatch on argument type (result is Boolean)
- **Conversions**: Dispatch on source type (argument)
- **Polymorphic**: No dispatch (generic implementation)

**Encode strategy in signature definition** for:
- ✅ Clarity
- ✅ Type safety
- ✅ Consistency
- ✅ Maintainability
