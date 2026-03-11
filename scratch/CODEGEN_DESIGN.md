# SparkSQL Code Generator - Design Summary

**Date**: 2025-01-23
**Status**: Design Complete - Ready for Implementation

---

## Context and Requirements

### Purpose
Translate FHIRPath IR (Intermediate Representation) to SparkSQL Column expressions for execution on FHIR resources stored in Spark DataFrames.

### Key Constraints
- **This is a translator**: Most IR operations map to single SparkSQL API calls, not complex expression chains
- **Schema representation**:
  - `Shape(ONE, T)` → SQL scalar `mapToSpark(T)`
  - `Shape(MANY, T)` → SQL `ARRAY(mapToSpark(T))`
  - Empty collection → SQL `NULL`
- **Spark version**: Use Spark 3.x-compatible functions (non-ANSI semantics) for FHIRPath compatibility
- **Type safety**: Analyzer performs static Shape checking; code generator trusts IR is well-typed

### Current Issues with Existing Code
From analysis of existing `SparkCodeGenerator.java`:
- 56-line switch statement for operation dispatch
- Type-specific logic scattered across 15+ methods
- Inconsistent type dispatch (some use resultType, some use argType)
- N×M complexity (operations × types)
- Difficult to add new operations or types

---

## Core Architecture

### Stateful Handlers (Command Pattern)

Handlers are created **per invocation** as command objects encapsulating:
- `operation` - Operation name
- `dispatchType` - Type determining handler selection
- `context` - Code generation context (includes SparkSession, etc.)

**Key benefit**: Operation methods have minimal signatures - no context parameter needed.

### Hybrid Handler Organization

Three handler types based on operation characteristics:

1. **Operation-Based**: For heavily overloaded operations across many types
   - Examples: Comparison (`eq`, `ne`, `gt`), Arithmetic (`add`, `sub`), Math (`abs`, `ceiling`)
   - Benefit: Adding new comparison operator touches ONE file

2. **Type-Based**: For type-specific operations
   - Examples: String ops (`upper`, `lower`, `substring`), Quantity ops (`getValue`, `getUnit`)
   - Benefit: All operations for a type in one place

3. **Generic**: For polymorphic operations independent of element type
   - Examples: Collection ops (`count`, `first`, `last`, `where`, `select`)
   - Benefit: Work on any collection type without specialization

### Automatic Boxing/Unboxing

`InvocationBinder` automatically converts between SparkSQL `Column` and domain wrappers:

**Boxing** (Column → Wrapper):
- `Column` + `IRNode.getType()` → `Collection(column, isSingular)` (when handling cardinality)
- `Column` → `Quantity(column)` (for complex types with multiple fields)
- IR `Lambda` node → `LambdaExpression(lambda, context)` (for lambda operations)
- `Column` → `Column` (pass through for primitives)

**Unboxing** (Wrapper → Column):
- `Collection.toColumn()` → `Column`
- `Quantity.toColumn()` → `Column`
- `Column` → `Column` (pass through)

**Note**: Wrappers used **sparingly** - only where they add value (Collection for cardinality handling, Quantity for field access, LambdaExpression for lambda evaluation). Most primitive type operations work directly with raw `Column`.

---

## Key Design Decisions

### 1. Null Handling

**Empty Collections = SQL NULL**
- Both `Shape(ONE, T)` and `Shape(MANY, T)` use NULL for empty
- Explicit empty (`{}`) and implicit empty (`1.where(false)`) both represented as NULL

**Leverage SQL NULL Propagation**
- Use SparkSQL's NULL propagation where it matches FHIRPath semantics
- Add explicit NULL checks ONLY when semantics differ
- Example: `count()` must return 0 for NULL, not NULL

**Use Spark 3.x Functions**
- `functions.get()` NOT `functions.element_at()` (ANSI mode throws errors)
- Non-ANSI mode returns NULL on errors, matching FHIRPath empty semantics

### 2. Lambda Expression Handling

**LambdaExpression Wrapper**
- IR `Lambda` node boxed to `LambdaExpression` wrapper
- Wrapper provides: `Column apply(Column thisElement)`
- Encapsulates lambda evaluation logic

**Handler Usage**
```java
@Operation("where")
public Collection where(Collection input, LambdaExpression predicate) {
    return input.filter(predicate::apply);  // Clean and simple
}
```

**Initial Implementation**: Only `$this` variable supported (bound to element in `apply()`)

**Future Extensions**: `$index`, nested lambda scope, variable capture

### 3. Collection Element Type

**Decision**: Do NOT add element type to Collection wrapper at this stage

**Rationale**:
- No type checking between IRNode types and handler signatures currently
- Element type not needed for current operations
- Can be added later if `select()` or `iif()` need result cardinality for flattening

**Collection wrapper**: `Collection(Column column, boolean isSingular)`

### 4. Handler Registry

**Factory Method Initialization**: `HandlerRegistry.standard()`

**No Validation**: Registry doesn't validate against SignatureRegistry

**No Extensibility**: No plugin mechanism for custom handlers (add later if needed)

**Usage**:
```java
HandlerRegistry registry = HandlerRegistry.standard();
SparkCodeGenerator codeGen = new SparkCodeGenerator(registry, spark);
```

### 5. Debugging and Observability

**Standard Slf4j Logging**

**Log Levels**:
- **DEBUG**: Major transformations (AST → IR → SQL)
- **TRACE**: Detailed node-by-node processing

**Printable Representations**:
- **Single-line format**: Compact for inline logging
- **Tree format**: Hierarchical for complex expressions

**IRNode Logging Requirement**: All IR nodes must include Shape in `toString()`
- Example: `Operation(add, args=[...], shape=Shape(ONE, INTEGER))`

**Transformation Tracking**: Log at AST→IR and IR→SQL boundaries

### 6. Error Handling

**Three-Tier Strategy**:

**Tier 1: Code Generation Failures** → Throw `CodeGenerationException`
- Unsupported operations
- Static mismatches (arity, types)
- Handler method invocation failures
- **Fail fast with clear context**

**Tier 2: Generated SQL Behavior** → Implement FHIRPath Semantics
- Return NULL for edge cases (empty collections)
- Use SQL NULL propagation where it matches FHIRPath
- Add explicit checks ONLY when SQL semantics differ
- Use Spark 3.x functions (non-ANSI mode)

**Tier 3: Runtime SQL Errors** → Should Not Occur
- Analyzer catches type/shape errors via static checking
- Runtime errors indicate bugs in analyzer or code generator

**Philosophy**: Trust the analyzer, generate correct FHIRPath semantics, fail fast on code gen issues

---

## Domain Wrappers

### Collection Wrapper

**Purpose**: Handle singular vs non-singular values uniformly (used for operations that need cardinality-aware behavior)

**Structure**: `Collection(Column column, boolean isSingular)`

**Key Methods**:
- `apply(arrayFn, singleFn)` - Execute different logic based on cardinality
- `count()`, `first()`, `last()`, `isEmpty()` - Collection operations
- `filter()`, `map()`, `all()`, `any()` - Transformations with lambda support
- `toColumn()` - Unwrap for unboxing

**Use Cases**: Collection operations (`count`, `where`, `select`), operations needing cardinality dispatch

**Benefits**: Handlers don't manually check cardinality where it matters

### Quantity Wrapper

**Purpose**: Simplify access to complex struct fields

**Structure**: `Quantity(Column column)`

**Key Methods**:
- `value()`, `unit()`, `system()`, `code()` - Field accessors
- `mapValue(fn)` - Transform value while preserving unit
- `build(...)` - Create Quantity struct
- `toColumn()` - Unwrap for unboxing

**Benefits**: Clean field access without repeated `getField()` calls

### When to Use Wrappers vs Raw Column

**Use wrappers**:
- **Collection**: When operation behavior differs for singular vs array (e.g., `count`, `where`, `select`)
- **Quantity**: When accessing multiple struct fields (e.g., `add` needs value + unit)
- **LambdaExpression**: For lambda-based operations (`where`, `select`, `all`, `any`)

**Use raw Column**:
- **Primitive operations**: Comparison, arithmetic, math on Integer/Decimal/String
- **Simple operations**: Single SparkSQL function call with no cardinality logic
- **Type-agnostic**: Operations that work same way regardless of type

**Example - Raw Column**:
```java
@Operation("add")
public Column add(Column left, Column right) {
    return left.plus(right);  // Simple, no wrapper needed
}
```

**Example - With Collection Wrapper**:
```java
@Operation("count")
public Column count(Collection input) {
    return input.count();  // Needs cardinality-aware logic
}
```

### LambdaExpression Wrapper

**Purpose**: Encapsulate lambda evaluation with element binding

**Structure**: `LambdaExpression(Lambda lambdaNode, CodeGenContext context)`

**Key Method**: `Column apply(Column thisElement)` - Evaluate lambda with `$this` bound

**Benefits**: Handlers use method references (`lambda::apply`), no context management needed

---

## Handler Structure

### Base Pattern

```java
public abstract class AnnotatedOperationHandler {
    protected final String operation;
    protected final Type dispatchType;
    protected final CodeGenContext context;

    protected AnnotatedOperationHandler(
            String operation,
            Type dispatchType,
            CodeGenContext context) {
        this.operation = operation;
        this.dispatchType = dispatchType;
        this.context = context;
    }
}
```

### Operation Method Pattern

```java
@Operation("count")
public Column count(Collection input) {
    // No context parameter - available as this.context
    return input.count();
}

@Operation("where")
public Collection where(Collection input, LambdaExpression predicate) {
    // LambdaExpression already has apply() - just use it
    return input.filter(predicate::apply);
}
```

### Shared Operations Pattern

```java
public class IntegerHandler extends AnnotatedOperationHandler {

    @OperationMappings
    public Stream<NamedMapping> operations() {
        return Stream.concat(
            CommonOperations.arithmeticOps(),     // add, sub, multiply, divide
            CommonOperations.comparisonOps(),     // eq, ne, gt, gte, lt, lte
            CommonOperations.mathOps()            // abs, ceiling, floor
        );
    }

    // Type-specific operations (if any)
}
```

**Benefit**: 74% code reduction for primitive types sharing common operations

---

## InvocationBinder

**Purpose**: Automatic boxing/unboxing when invoking handler methods

**Boxing Process**:
1. Examine handler method parameter types
2. For each `Column` argument + corresponding `IRNode`:
   - If parameter type is `Collection` → box to `Collection(column, isSingular)`
   - If parameter type is `Quantity` → box to `Quantity(column)`
   - If parameter type is `LambdaExpression` → box IR `Lambda` to `LambdaExpression`
   - If parameter type is `Column` → pass through

**Unboxing Process**:
1. Receive result from handler method
2. If result is wrapper type → call `toColumn()`
3. If result is `Column` → pass through

**Key Point**: No `CodeGenContext` parameter needed - it's in handler state

---

## Handler Registry

**Structure**:
- Stores **factory functions**, not handler instances
- Three registries: operation-based, type-based, generic

**Handler Creation Flow**:
1. `SparkCodeGenerator` calls `registry.createHandler(operation, dispatchType, context)`
2. Registry determines handler type (operation-based, type-based, or generic)
3. Registry creates handler instance: `new Handler(operation, dispatchType, context)`
4. `InvocationBinder` invokes handler method with boxed arguments
5. Result unboxed to `Column`

**Precedence**: Operation-based → Type-based → Generic

---

## Integration Points

### With Analyzer
- **Input**: Well-typed IR nodes with Shape information
- **Assumption**: Analyzer has performed static type/shape checking
- **Contract**: Code generator trusts IR is valid

### With IR
- **IRNode.getType()**: Provides Shape for cardinality and type information
- **Lambda IR nodes**: Boxed to LambdaExpression wrapper
- **All IR nodes**: Must have printable toString() with Shape

### With SparkSQL
- **Column**: Primary interface to SparkSQL expressions
- **Use Spark 3.x functions**: Non-ANSI mode for FHIRPath semantics
- **NULL semantics**: Leverage SQL NULL propagation where possible

---

## Implementation Phases (Recommended)

### Phase 1: Core Infrastructure
- `CodeGenContext` class
- `InvocationBinder` with boxing/unboxing
- `HandlerRegistry` with factory pattern
- `CodeGenerationException` class

### Phase 2: Wrappers
- `Collection` wrapper with cardinality handling
- `LambdaExpression` wrapper
- `Quantity` wrapper (if needed early)

### Phase 3: Simple Handlers
- `CollectionOperationHandler` (count, first, last)
- `ComparisonOperationHandler` (eq, ne, gt, gte, lt, lte)
- Test with real IR nodes

### Phase 4: Complex Handlers
- `ArithmeticOperationHandler` (add, sub, multiply, divide)
- `StringHandler` (upper, lower, substring)
- `QuantityHandler` (if Quantity operations needed)

### Phase 5: Lambda Operations
- Extend `CollectionOperationHandler` for where, select, all, any
- Test lambda evaluation with complex expressions

### Phase 6: Migration
- Incrementally migrate operations from old SparkCodeGenerator
- Run both in parallel with comparison tests
- Remove old code when all operations migrated

---

## Success Criteria

### Code Quality
- ✅ No switch statements - annotation-based dispatch
- ✅ Clear separation of concerns - handlers focused on single responsibility
- ✅ Minimal boilerplate - shared operations, automatic boxing/unboxing
- ✅ Type safety - domain wrappers, compile-time checking

### Maintainability
- ✅ Adding heavily overloaded operation: Touch 1 file
- ✅ Adding type-specific operation: Touch 1 file
- ✅ Adding new type: Add 1 handler, reuse operation handlers

### Correctness
- ✅ FHIRPath semantics correctly implemented in SQL
- ✅ NULL handling matches FHIRPath empty collection behavior
- ✅ Cardinality handling (singular vs array) transparent to handlers

### Debuggability
- ✅ Clear error messages with operation/type context
- ✅ IR logging includes Shape at all transformations
- ✅ Single-line and tree format for complex expressions

---

## Future Extensions (Deferred)

**Collection Element Type**: Add if select()/iif() need result cardinality for flattening

**Lambda Variables**: Add `$index`, nested scope, variable capture beyond `$this`

**Registry Validation**: Validate all operations have handlers against SignatureRegistry

**Custom Handlers**: Plugin mechanism for user-defined operations

**Performance Optimization**: Handler caching, wrapper pooling (profile first)

---

## References

Detailed design decisions: `CODEGEN_DESIGN_DECISIONS.md`

Additional considerations: `CODEGEN_ADDITIONAL_CONSIDERATIONS.md`

Architecture analysis: `docs/CODEGEN_*.md` (individual topic documents)
