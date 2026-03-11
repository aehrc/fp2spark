# SparkSQL Code Generator Design Decisions

This document records key design decisions made during the architecture phase.

---

## Decision 1: Null Handling Semantics

**Date**: 2025-01-23

### Context

FHIRPath and SQL have different null semantics. Need to define how empty collections and null values are represented and handled in generated SparkSQL.

### Decision

**Representation**:
- `Shape(ONE, T)` → SQL scalar type `mapToSpark(T)` (not array)
- `Shape(MANY, T)` → SQL `ARRAY(mapToSpark(T))`
- **Empty collection** → SQL `NULL` (in both cases)

**Empty Collection Types**:
- **Explicit empty**: `{}` literal
- **Implicit empty**: `1.where(false)`, `name.prefix.first()` on missing data
- Both represented as `NULL`
- Future optimization: explicit empty might be optimized away in expressions

**Handler Semantics**:
- Handlers **receive NULL** for empty collections
- Handlers **return NULL** for empty collections
- In many cases, SQL NULL semantics match FHIRPath semantics
- **Only when semantics differ**: Add NULL checking to generated expressions

**SparkSQL Version Compatibility**:
- SparkSQL 4.x uses ANSI NULL handling (different from 3.6)
- **Use functions with old semantics** (closer to FHIRPath)
- Example: Use `functions.get()` NOT `functions.element_at()`
- This ensures FHIRPath-compatible null behavior

### Implications

1. **Collection wrapper** doesn't need to distinguish empty from null - both are NULL
2. **Handlers** work with NULL values directly
3. **NULL-safe operations** needed when SQL semantics differ from FHIRPath:
   ```java
   // Example: count() must handle NULL as 0, not NULL
   public Column count() {
       return applyNonNull(
           functions::size,      // Array: size(array)
           col -> lit(1),        // Singular: 1
           lit(0)                // NULL: 0 (not NULL!)
       );
   }
   ```
4. **Use Spark 3.x-compatible functions** for FHIRPath semantics

### Rationale

- Aligns with chosen FHIR resource SQL schema representation
- Simplifies handler implementation (no special empty marker)
- Allows future optimization of explicit empty collections
- Leverages SQL NULL naturally where semantics align

---

## Decision 2: Lambda Expression Handling

**Date**: 2025-01-23

### Context

FHIRPath has lambda expressions in operations like `where()`, `select()`, `all()`, `any()`. Need to define how lambdas are passed to handlers and evaluated in the new stateful handler architecture.

### Current Implementation Pattern

The existing `evaluateWhere()` method shows the pattern:
```java
// For collections: use Spark's filter with Java lambda
Column filtered = functions.filter(collection, elem -> {
    SparkCodeGenerator lambdaGen = withThisColumn(elem);
    return lambda.body().accept(lambdaGen);
});

// For singular: evaluate lambda directly
SparkCodeGenerator singularGen = withThisColumn(collection);
Column criteriaResult = lambda.body().accept(singularGen);
```

Key pattern: Create new code generator with `$this` bound to element, then visit lambda body.

### Decision

**Naming**:
- **IR node**: `Lambda` (existing IR node type)
- **Wrapper**: `LambdaExpression` (boxed wrapper for handlers)

**LambdaExpression Wrapper Class**:
- Create `LambdaExpression` wrapper that encapsulates lambda evaluation logic
- Wrapper provides `Column apply(Column thisElement)` method
- Handler methods receive `LambdaExpression` wrapper (not IR `Lambda` node):
  ```java
  @Operation("where")
  public Collection where(Collection input, LambdaExpression predicate)
  ```

**LambdaExpression Implementation**:
```java
/**
 * Wrapper for FHIRPath lambda expressions that can be evaluated with element binding.
 * This is the boxed form of IR Lambda nodes, used by handlers.
 */
public class LambdaExpression {
    private final Lambda lambdaNode;  // IR node
    private final CodeGenContext context;

    public LambdaExpression(Lambda lambdaNode, CodeGenContext context) {
        this.lambdaNode = lambdaNode;
        this.context = context;
    }

    /**
     * Evaluate lambda with given element bound to $this.
     */
    public Column apply(Column thisElement) {
        SparkCodeGenerator lambdaGen = context.withThisColumn(thisElement);
        return lambdaNode.body().accept(lambdaGen);
    }
}
```

**Lambda Variables (Initial Implementation)**:
- **Only `$this` supported initially**
- `$this` bound to element passed to `apply()`
- Future extension: Could pass struct/map with multiple variables (`$index`, etc.)

**Handler Usage Pattern**:
```java
@Operation("where")
public Collection where(Collection input, LambdaExpression predicate) {
    // LambdaExpression wrapper already has apply() - just use it!
    return input.filter(predicate::apply);
}

@Operation("select")
public Collection select(Collection input, LambdaExpression projection) {
    return input.map(projection::apply);
}

@Operation("all")
public Column all(Collection input, LambdaExpression predicate) {
    return input.all(predicate::apply);
}
```

**Boxing Behavior**:
- `InvocationBinder` recognizes IR `Lambda` node in arguments
- Creates `LambdaExpression` wrapper: `new LambdaExpression(lambdaIRNode, context)`
- Passes wrapper to handler method
- Handler just calls `lambdaExpression.apply(element)` - no context needed!

### Future Extensions

- **`$index`**: Add to lambda context for operations that need element position
- **Nested lambdas**: Track lambda scope stack in `CodeGenContext`
- **Variable capture**: Pass struct/map with all context variables
- **Multiple lambda parameters**: Support operations with multiple lambda arguments

### Implications

1. **InvocationBinder** boxes IR `Lambda` nodes to `LambdaExpression` wrapper (like other wrappers)
2. **LambdaExpression wrapper** encapsulates all evaluation logic internally
3. **Handlers** are extremely simple - just `lambdaExpression.apply(element)` or method reference
4. **Collection wrapper** utilities (`map`, `filter`, etc`) work with `Function<Column, Column>`
5. **No context needed in handlers** - LambdaExpression wrapper already has it
6. **Clear naming**: `Lambda` = IR node, `LambdaExpression` = boxed wrapper

### Rationale

- Reuses proven pattern from existing `evaluateWhere()` implementation
- Keeps lambda evaluation logic centralized in `CodeGenContext`
- Allows future extension to `$index` and other variables
- Clean handler signatures - lambdas are first-class parameters
- Stateful handlers already have `context` available for evaluation

---

## Decision 3: Collection Element Type Tracking

**Date**: 2025-01-23

### Context

Question whether `Collection` wrapper should track element type information:
```java
// Current
public record Collection(Column column, boolean isSingular)

// With element type?
public record Collection(Column column, boolean isSingular, Type elementType)
```

### Decision

**Do NOT add element type to Collection wrapper at this time.**

**Rationale**:
1. **No type checking**: Currently no type checking between IRNode types and handler function signature types
2. **Handler return types don't matter**: Whether handler returns `Column` or `Collection`, no validation happens
3. **Element type not needed for current operations**:
   - Simple ops (`count`, `first`, `last`) - work on any element type
   - Lambda ops (`where`, `select`) - lambda evaluation handles typing
   - Type dispatch already happened before handler is called

**What MIGHT be needed (deferred)**:
- **Lambda result arity/cardinality**: Operations like `select()` may need to know if lambda result is singular or collection (for flattening nested collections)
- Example: `collection.select(items)` where `items` is itself a collection → need to flatten

**When to revisit**:
- When implementing `select()` operation
- When implementing `iif()` operation
- If flattening/unnesting logic requires knowing result cardinality

### Implications

1. **Collection wrapper stays simple**: Just `(Column, boolean isSingular)`
2. **No element type in boxing**: `InvocationBinder` doesn't extract element type from IRNode
3. **Handlers work generically**: Operations don't specialize on element type
4. **Future-proof**: Can add element type or result cardinality later if needed

### Alternative Considered

Adding `Type elementType` to Collection - rejected because:
- Not needed for any current use case
- Adds complexity without benefit
- Can be added later if actual need emerges

---

## Decision 4: Handler Registry Initialization

**Date**: 2025-01-23

### Context

Need to define how `HandlerRegistry` is initialized and when handlers are registered.

### Decision

**Use factory method pattern**: `HandlerRegistry.standard()`

**No extensibility needed**: No plugin mechanism or custom handler registration at this stage

**No validation needed**: No validation against `SignatureRegistry` at this stage

**Implementation**:
```java
public class HandlerRegistry {

    public static HandlerRegistry standard() {
        HandlerRegistry registry = new HandlerRegistry();

        // Register operation-based handler factories
        registry.registerOperationHandlerFactory("comparison",
            ComparisonOperationHandler::new);
        registry.registerOperationHandlerFactory("arithmetic",
            ArithmeticOperationHandler::new);
        registry.registerOperationHandlerFactory("math",
            MathOperationHandler::new);

        // Register type-based handler factories
        registry.registerTypeHandlerFactory(Shape.STRING,
            StringHandler::new);
        registry.registerTypeHandlerFactory(Shape.QUANTITY,
            QuantityHandler::new);

        // Register generic handler factories
        registry.registerGenericHandlerFactory(
            CollectionOperationHandler::new);

        return registry;
    }

    private void registerOperationHandlerFactory(
            String key,
            OperationHandlerFactory factory) {
        // Store factory for operation-based handlers
    }

    private void registerTypeHandlerFactory(
            Type type,
            TypeHandlerFactory factory) {
        // Store factory for type-based handlers
    }

    private void registerGenericHandlerFactory(
            GenericHandlerFactory factory) {
        // Store factory for generic handlers
    }

    public OperationHandler createHandler(
            String operation,
            Type dispatchType,
            CodeGenContext context) {
        // Create handler instance using appropriate factory
    }
}
```

**Usage**:
```java
// When creating SparkCodeGenerator
HandlerRegistry registry = HandlerRegistry.standard();
SparkCodeGenerator codeGen = new SparkCodeGenerator(registry, spark);
```

### Implications

1. **Simple initialization**: One-line factory call
2. **No validation overhead**: Registry doesn't check signature coverage
3. **No extension mechanism**: Can't add custom handlers (add later if needed)
4. **Standard set**: All built-in handlers registered via factory method
5. **Stateless registry**: Just stores factories, creates handlers on demand

### Future Extensions (Deferred)

- **Validation**: Check all operations in `SignatureRegistry` have handlers
- **Custom handlers**: Plugin mechanism for user-defined operations
- **Builder pattern**: More flexible configuration if needed

### Rationale

- Simplest approach that meets current needs
- Easy to add validation/extensibility later
- Clear, explicit handler registration in factory method
- No magic (no classpath scanning, reflection, etc.)

---

## Decision 5: Debugging and Observability

**Date**: 2025-01-23

### Context

Need to define logging and debugging strategy for code generation pipeline to track transformations and diagnose issues.

### Decision

**Use standard Slf4j logging**

**All representations must be printable**:
- **AST** (Abstract Syntax Tree)
- **IR** (Intermediate Representation)
- **SparkSQL** (Generated SQL expressions)

**Print formats required**:
1. **Single-line format**: Compact representation for inline logging
2. **Tree format** (optional): Hierarchical representation for detailed inspection

**Logging levels**:
- **DEBUG**: Major transformations (AST → IR → SQL)
- **TRACE**: Detailed node-by-node processing

**IRNode representation requirements**:
- **Must include Shape** for all nodes
- Shape provides type and cardinality information

**Implementation guidelines**:
```java
// Example: IRNode with toString() including shape
public record Operation(
    String name,
    List<IRNode> args,
    ResolvedSignature signature
) implements IRNode {

    @Override
    public String toString() {
        // Single-line format with shape
        return "Operation(%s, args=%s, shape=%s)"
            .formatted(name, args, getType());
    }

    public String toTreeString() {
        // Tree format
        StringBuilder sb = new StringBuilder();
        sb.append("Operation: ").append(name)
          .append(" [").append(getType()).append("]\n");
        for (int i = 0; i < args.size(); i++) {
            sb.append("  arg").append(i).append(": ")
              .append(args.get(i).toTreeString().indent(2));
        }
        return sb.toString();
    }
}

// Example: SparkCodeGenerator logging
public class SparkCodeGenerator {

    private static final Logger log = LoggerFactory.getLogger(SparkCodeGenerator.class);

    @Override
    public Column visitOperation(Operation op) {
        if (log.isDebugEnabled()) {
            log.debug("Generating SQL for operation: {}", op);
        }

        Column result = generateOperation(op);

        if (log.isTraceEnabled()) {
            log.trace("Operation {} generated SQL: {}",
                op.name(), result.expr().sql());
        }

        return result;
    }
}

// Example: Handler logging
public class ComparisonOperationHandler extends AnnotatedOperationHandler {

    private static final Logger log = LoggerFactory.getLogger(ComparisonOperationHandler.class);

    @Operation("eq")
    public Column equals(Collection left, Collection right) {
        if (log.isTraceEnabled()) {
            log.trace("Comparing {} (singular={}) with {} (singular={})",
                left.toColumn(), left.isSingular(),
                right.toColumn(), right.isSingular());
        }

        Column result = left.toColumn().equalTo(right.toColumn());

        if (log.isTraceEnabled()) {
            log.trace("Comparison result SQL: {}", result.expr().sql());
        }

        return result;
    }
}
```

### Transformation Tracking

**Log at key transformation points**:

1. **AST → IR** (in Analyzer):
   ```java
   log.debug("AST: {}", astNode);
   IRNode ir = analyze(astNode);
   log.debug("IR: {}", ir);
   ```

2. **IR → SQL** (in SparkCodeGenerator):
   ```java
   log.debug("Generating SQL for IR: {}", irNode);
   Column sql = visit(irNode);
   log.debug("Generated SQL: {}", sql.expr().sql());
   ```

3. **Operation dispatch** (in HandlerRegistry):
   ```java
   log.trace("Dispatching operation={}, dispatchType={}", operation, dispatchType);
   OperationHandler handler = createHandler(operation, dispatchType, context);
   log.trace("Selected handler: {}", handler.getClass().getSimpleName());
   ```

### IRNode Shape Logging

**Every IRNode toString() must include shape**:
```java
// Good
"Operation(add, args=[Literal(5), Literal(3)], shape=Shape(ONE, INTEGER))"

// Bad (missing shape)
"Operation(add, args=[Literal(5), Literal(3)])"
```

This allows tracking type flow through transformations.

### Implications

1. **All IR nodes** need `toString()` with single-line format including shape
2. **All IR nodes** should implement `toTreeString()` for hierarchical view
3. **Handlers** should log at TRACE level for detailed operation tracking
4. **SparkCodeGenerator** should log transformations at DEBUG level
5. **Column SQL representation** available via `column.expr().sql()`

### Benefits

- **Track transformations**: See AST → IR → SQL pipeline clearly
- **Type flow visibility**: Shape in IR logging shows type inference
- **Debugging support**: Tree format for complex expressions
- **Performance**: Logging guarded by level checks (`isDebugEnabled()`)
- **Standard tooling**: Slf4j works with all logging frameworks

### Example Debug Output

```
DEBUG [Analyzer] AST: BinaryOp(+, IntLiteral(5), IntLiteral(3))
DEBUG [Analyzer] IR: Operation(add, args=[Literal(5, shape=Shape(ONE, INTEGER)),
                               Literal(3, shape=Shape(ONE, INTEGER))],
                               shape=Shape(ONE, INTEGER))
DEBUG [SparkCodeGenerator] Generating SQL for IR: Operation(add, ...)
TRACE [HandlerRegistry] Dispatching operation=add, dispatchType=Shape(ONE, INTEGER)
TRACE [HandlerRegistry] Selected handler: ArithmeticOperationHandler
TRACE [ArithmeticOperationHandler] Executing add on INTEGER type
DEBUG [SparkCodeGenerator] Generated SQL: (5 + 3)
```

### Rationale

- **Slf4j**: Industry standard, flexible backend support
- **Shape in IR**: Critical for understanding type inference and dispatch
- **Two formats**: Single-line for logs, tree for debugging complex expressions
- **Guard checks**: Avoid expensive string formatting when logging disabled
- **Trace transformations**: Essential for debugging code generation issues

---

## Decision 6: Error Handling Strategy

**Date**: 2025-01-23

### Context

Need to define how errors are handled during code generation and what runtime behavior is expected from generated SQL.

### Decision

**Three-tier error handling approach**:

#### Tier 1: Code Generation Failures → Throw CodeGenerationException

**All unsupported operations throw exception**, regardless of reason:
- Operation not registered in handler registry
- Handler method not found for operation
- Any code generation failure

**All static mismatches throw exception**:
- Arity mismatch between IR args and handler method parameters
- Type mismatches between IR node types and handler expectations
- Invalid handler configurations

**Exception type**:
```java
public class CodeGenerationException extends RuntimeException {
    private final String operationName;
    private final Type dispatchType;
    private final List<Type> argumentTypes;

    public CodeGenerationException(
            String message,
            String operationName,
            Type dispatchType,
            List<Type> argumentTypes) {
        super(message);
        this.operationName = operationName;
        this.dispatchType = dispatchType;
        this.argumentTypes = argumentTypes;
    }

    public CodeGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

**When to throw**:
```java
// Unsupported operation
if (!registry.canHandle(operation)) {
    throw new CodeGenerationException(
        "No handler registered for operation: " + operation,
        operation, dispatchType, argTypes
    );
}

// Arity mismatch (detected during handler validation)
if (methodParamCount != signatureParamCount) {
    throw new CodeGenerationException(
        "Handler method %s.%s has %d parameters but signature expects %d"
            .formatted(handlerClass, methodName, methodParamCount, signatureParamCount)
    );
}

// Handler method invocation failure
catch (ReflectiveOperationException e) {
    throw new CodeGenerationException(
        "Failed to invoke handler method for operation: " + operation,
        e
    );
}
```

#### Tier 2: Generated SQL Behavior → Implement FHIRPath Semantics

**Principle**: Generated SQL must correctly implement FHIRPath function semantics

**Most common case**: Return NULL (representing empty collection)
- NULL in SQL corresponds to empty collection in FHIRPath
- Operations on empty collections typically return empty

**Use SQL NULL propagation where it matches FHIRPath**:
```java
// Good: SQL NULL propagation matches FHIRPath
@Operation("add")
public Column add(Collection left, Collection right) {
    // If either is NULL, result is NULL (matches FHIRPath empty semantics)
    return left.toColumn().plus(right.toColumn());
}
```

**Add explicit NULL checks ONLY when SQL semantics differ**:
```java
// Example: count() must return 0 for NULL, not NULL
@Operation("count")
public Column count(Collection input) {
    return input.applyNonNull(
        functions::size,        // Array: size
        col -> lit(1),          // Singular: 1
        lit(0)                  // NULL: 0 (NOT NULL!)
    );
}
```

**Use Spark 3.x-compatible functions** (non-ANSI semantics):
```java
// Good: get() returns NULL on out-of-bounds (Spark 3.x)
functions.get(array, index)

// Avoid: element_at() throws error on out-of-bounds (Spark 4.x ANSI)
functions.element_at(array, index)
```

**Division by zero**: Let SQL NULL propagation handle it
```java
@Operation("divide")
public Column divide(Collection left, Collection right) {
    // Spark returns NULL on division by zero (non-ANSI mode)
    // Matches FHIRPath empty collection semantics
    return left.toColumn().divide(right.toColumn());
}
```

**Type coercion failures**: Default NULL behavior
```java
@Operation("toInteger")
public Column toInteger(Collection input) {
    // Cast returns NULL on failure - matches FHIRPath empty semantics
    return input.toColumn().cast(DataTypes.IntegerType);
}
```

#### Tier 3: Runtime SQL Errors → Should Not Occur

**Assumption**: Most error cases caught by static Shape checking in AST analyzer

**Rationale**:
- Type errors caught during analysis (Shape inference)
- Cardinality violations caught during analysis
- Invalid operations on types caught during analysis

**Therefore**: Generated SQL should NOT throw runtime exceptions

**If runtime errors occur**: Indicates bug in analyzer or code generator, not user error

### Implications

1. **Fast failure for code gen problems**: Clear exceptions with context
2. **Correct FHIRPath semantics**: Generated SQL implements spec correctly
3. **Trust the analyzer**: Type/shape checking happens before code gen
4. **NULL = empty**: Most operations return NULL for edge cases
5. **Minimal defensive checks**: Only where SQL differs from FHIRPath
6. **Use Spark 3.x functions**: Non-ANSI mode for FHIRPath semantics

### Error Message Requirements

**CodeGenerationException must include**:
- Clear error message explaining what failed
- Operation name (when applicable)
- Dispatch type (when applicable)
- Argument types (when applicable)
- Handler class name (when applicable)
- Cause exception (when wrapping another exception)

**Example error messages**:
```
No handler registered for operation: unknownOp, dispatchType=Shape(ONE, INTEGER)

Handler method ComparisonOperationHandler.ne has 3 parameters but signature expects 2

Failed to invoke handler method for operation: add
Caused by: IllegalAccessException: ...
```

### Handler Implementation Guidelines

**Rely on NULL propagation**:
```java
// Simple - let NULL propagate
@Operation("multiply")
public Column multiply(Collection left, Collection right) {
    return left.toColumn().multiply(right.toColumn());
}
```

**Add explicit checks only when needed**:
```java
// count() needs explicit check - SQL NULL propagation differs
@Operation("count")
public Column count(Collection input) {
    return input.applyNonNull(
        functions::size,
        col -> lit(1),
        lit(0)  // Explicit: NULL → 0, not NULL
    );
}
```

**Document when FHIRPath semantics differ from SQL**:
```java
@Operation("divide")
public Column divide(Collection left, Collection right) {
    // FHIRPath: X / 0 = {} (empty)
    // Spark: X / 0 = NULL (in non-ANSI mode)
    // These are equivalent in our representation
    return left.toColumn().divide(right.toColumn());
}
```

### Rationale

- **Clear failure modes**: Code gen errors are bugs, fail fast with context
- **Correct semantics**: Trust analyzer for type safety, generate correct FHIRPath behavior
- **Simplicity**: Minimal defensive checks, leverage SQL NULL propagation
- **Performance**: No unnecessary NULL checks where SQL semantics match
- **Debuggability**: Rich error messages for code gen failures

---
