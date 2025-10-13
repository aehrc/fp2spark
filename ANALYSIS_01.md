the # Analysis of IRNode Design and Architecture Options

**Date:** 2025-10-13
**Topic:** IRNode implementation, signature resolution, and result type calculation

---

## Current Architecture Overview

### Key Components

1. **IRNode Interface** (`IRNode.java:6-14`):
   - Minimal interface with `getType()` and `eval()` methods
   - Type is baked into each node instance

2. **Signature-Based Resolution** (`FunctionSignature.java:8`, `OverloadResolver.java:23-61`):
   - Each IRNode subclass defines static `SIGNATURES` field
   - `OverloadResolver` finds best signature match based on argument types
   - Performs implicit type coercion via `Cast` and `GetValue` nodes
   - Cost-based selection (exact match = 0 cost, cast = 1 cost)

3. **Builder Pattern** (`IRClassBuilder.java:11-44`):
   - Uses reflection to instantiate IRNode subclasses
   - Reads static `SIGNATURES` field via reflection
   - Single builder per IRNode class

### Identified Issues (Redundancy)

1. **Type Definition Duplication**:
   - Result type defined in `FunctionSignature` (e.g., `Add.java:21-27`)
   - Result type recalculated in `getType()` method (e.g., `Add.java:33-35`)
   - Example: `Add` has signatures defining result types, but `getType()` returns `left.getType()`

2. **One Class Per Function**:
   - Each FHIRPath function requires a dedicated Java class
   - Boilerplate code for constructor, SIGNATURES, getType(), eval()
   - Example: 10+ separate classes for arithmetic/comparison operators

3. **Evaluation Logic Coupled to Type**:
   - `eval()` methods contain switch statements on types (e.g., `Add.java:38-46`, `GreaterThan.java:17-26`)
   - Logic scattered across multiple methods and classes
   - Not easily portable to other target systems

4. **Signature-Node Disconnect**:
   - Signatures used only during resolution
   - No connection between resolved signature and node instance
   - Node must re-implement type logic that signatures already describe

---

## Design Alternative Options

### Option 1: Catalyst-Style Two-Phase Resolution (Unresolved → Resolved)

#### Design
- Keep unresolved expression tree after parsing
- Apply analysis rules in passes to resolve types and add casts
- Transform unresolved nodes to resolved nodes in-place or create new resolved tree
- Similar to Spark's Analyzer transforming UnresolvedAttribute → AttributeReference

#### Implementation
```java
// Phase 1: Unresolved nodes
interface UnresolvedIRNode {
    IRNode resolve(TypeContext ctx);
}

// Phase 2: Resolved nodes with baked types
interface IRNode {
    Type getType();  // Known after resolution
    Column eval();
}

class UnresolvedFunctionCall implements UnresolvedIRNode {
    String name;
    List<UnresolvedIRNode> args;

    IRNode resolve(TypeContext ctx) {
        List<IRNode> resolvedArgs = args.map(a -> a.resolve(ctx));
        FunctionSignature sig = FunctionRegistry.resolveSignature(name, resolvedArgs);
        return new ResolvedFunctionCall(name, sig, resolvedArgs);
    }
}

class ResolvedFunctionCall implements IRNode {
    String name;
    FunctionSignature signature;
    List<IRNode> args;

    Type getType() { return signature.resultType(); }
    Column eval() { /* target-specific evaluation */ }
}
```

#### Pros
- Clean separation of concerns (resolution vs evaluation)
- Type resolution happens once, result cached in resolved tree
- Easy to inspect/optimize resolved tree before evaluation
- Pattern-matching rules can be applied iteratively
- Well-proven approach (Spark, many SQL engines)

#### Cons
- Two parallel type hierarchies to maintain
- More complex initial implementation
- Tree transformation overhead (though done once)
- Still requires per-function classes unless combined with another approach

#### Multi-Target Support
**Rating: ★★★★☆**
- Excellent: Resolved tree is target-agnostic
- Evaluation separated: different backends implement eval() differently
- Could swap eval() implementation per target

---

### Option 2: Signature-Driven Evaluation (Eliminate IRNode Subclasses)

#### Design
- Single generic `FunctionCall` IRNode class
- All behavior driven by `FunctionSignature` plus evaluation lambda
- Signatures enriched with execution strategy

#### Implementation
```java
// Enhanced signature with evaluation strategy
record FunctionSignature(
    String name,
    List<Type> parameterTypes,
    Type resultType,
    int minArity,
    EvaluationStrategy evalStrategy
) {}

interface EvaluationStrategy {
    Column eval(List<Column> args, Type resultType);
}

// Single IRNode for all functions
record FunctionCall(
    String name,
    FunctionSignature signature,
    List<IRNode> args
) implements IRNode {

    Type getType() { return signature.resultType(); }

    Column eval() {
        List<Column> argColumns = args.stream().map(IRNode::eval).toList();
        return signature.evalStrategy().eval(argColumns, getType());
    }
}

// Registry-based function definitions
static {
    register("abs",
        List.of(
            sig(List.of(INTEGER), INTEGER, (args, type) -> functions.abs(args.get(0))),
            sig(List.of(DECIMAL), DECIMAL, (args, type) -> functions.abs(args.get(0))),
            sig(List.of(QUANTITY), QUANTITY, (args, type) -> quantity(args.get(0)).abs())
        )
    );
}
```

#### Pros
- **Eliminates class explosion**: One class serves all functions
- **Single source of truth**: Type and evaluation defined together
- Compact function registry
- Easy to add new functions (just register)
- Natural fit for DSL/configuration-driven design

#### Cons
- Loss of type safety (everything goes through generic interface)
- Harder to debug (no dedicated class per function)
- Evaluation strategies might become complex for stateful operations
- Difficult to override specific function behavior
- May need many small lambda/strategy objects

#### Multi-Target Support
**Rating: ★★★★★**
- Excellent: Can register different `EvaluationStrategy` per target
- Example: `SparkEvalStrategy`, `SQLServerEvalStrategy`
- Signature/type resolution remains target-agnostic

---

### Option 3: Expression Rewriting with Type Annotations

#### Design
- Keep minimal IRNode hierarchy (operations vs literals vs references)
- Store resolved type and signature as node metadata
- Use visitor pattern for target-specific code generation

#### Implementation
```java
interface IRNode {
    Type getType();
    <T> T accept(IRNodeVisitor<T> visitor);
}

// Metadata-annotated nodes
record AnnotatedNode(
    IRNode wrapped,
    @Nullable FunctionSignature resolvedSignature,
    Type resolvedType
) implements IRNode {
    Type getType() { return resolvedType; }
    <T> T accept(IRNodeVisitor<T> visitor) {
        return visitor.visit(this);
    }
}

// Generic operation node
record Operation(String name, List<IRNode> args, Type type) implements IRNode {
    Type getType() { return type; }
}

// Target-specific visitors
interface SparkVisitor extends IRNodeVisitor<Column> {
    Column visit(Operation op) {
        return switch(op.name()) {
            case "abs" -> functions.abs(op.args().get(0).accept(this));
            case "add" -> handleAdd(op);
            // ...
        };
    }
}

interface SQLServerVisitor extends IRNodeVisitor<String> {
    String visit(Operation op) {
        return switch(op.name()) {
            case "abs" -> "ABS(" + op.args().get(0).accept(this) + ")";
            // ...
        };
    }
}
```

#### Pros
- Minimal node types (generic Operation, Literal, Traversal, etc.)
- Signature/type metadata preserved without redundancy
- Visitor pattern naturally extends to multiple targets
- Type information flows through annotation/metadata
- Easy to add optimization passes (tree rewriting)

#### Cons
- Visitor pattern can be verbose
- Less discoverable (operation logic spread across visitors)
- Requires careful metadata management
- May need multiple visitor passes for complex translations

#### Multi-Target Support
**Rating: ★★★★★**
- Excellent: Each target is a different visitor
- Tree structure completely target-agnostic
- Easy to add new targets without touching IR

---

### Option 4: Hybrid - Typed Expression Templates

#### Design
- Keep IRNode subclasses for major categories (Binary, Unary, etc.)
- Signatures drive type resolution and adaptation
- Templates capture eval patterns per operation family
- Store resolved signature in node

#### Implementation
```java
// Category-based hierarchy
sealed interface IRNode permits BinaryOp, UnaryOp, Literal, Traversal {}

record BinaryOp(
    String operator,
    IRNode left,
    IRNode right,
    FunctionSignature resolvedSignature
) implements IRNode {
    Type getType() { return resolvedSignature.resultType(); }

    Column eval() {
        // Dispatch based on operator family
        return BinaryOpEvaluator.eval(this);
    }
}

// Evaluator with reusable patterns
class BinaryOpEvaluator {
    static Column eval(BinaryOp op) {
        Column l = op.left().eval();
        Column r = op.right().eval();

        return switch(op.operator()) {
            case "+" -> handleAdd(l, r, op.getType());
            case "-" -> handleSub(l, r, op.getType());
            // ...
        };
    }

    private static Column handleAdd(Column l, Column r, Type type) {
        return switch(type) {
            case INTEGER, DECIMAL -> l.plus(r);
            case STRING -> functions.concat(l, r);
            case QUANTITY -> quantity(l).plus(quantity(r));
            // ...
        };
    }
}
```

#### Pros
- Balance between type safety and flexibility
- Natural operation grouping (binary, unary, ternary)
- Signature stored in node (single source of truth for type)
- Shared evaluation logic reduces duplication
- Easier to understand than fully generic approach

#### Cons
- Still need multiple node classes (though fewer than current)
- Evaluator dispatch adds indirection
- Not as flexible as pure visitor pattern
- Some duplication in category definitions

#### Multi-Target Support
**Rating: ★★★☆☆**
- Moderate: Would need different evaluator implementations per target
- Less clean than visitor pattern
- Requires strategy injection or factory pattern

---

### Option 5: Intermediate Representation with Lowering Passes

#### Design
- High-level IR (FHIRPath-specific, type-rich)
- Lower to target-specific IR through transformation passes
- Each target has custom lowering rules

#### Implementation
```java
// High-level FHIRPath IR
interface FHIRPathIR {
    Type getType();
    TargetIR lower(Target target);
}

record FPOperation(
    String operation,
    List<FHIRPathIR> args,
    FunctionSignature signature
) implements FHIRPathIR {
    Type getType() { return signature.resultType(); }

    TargetIR lower(Target target) {
        return target.lowerOperation(this);
    }
}

// Target-specific IR
interface TargetIR {
    Object eval();
}

// Spark target
class SparkTarget implements Target {
    TargetIR lowerOperation(FPOperation op) {
        return switch(op.operation()) {
            case "abs" -> new SparkColumn(functions.abs(/*...*/));
            case "add" -> new SparkAddOperation(/*...*/);
            // ...
        };
    }
}

// SQL Server target
class SQLServerTarget implements Target {
    TargetIR lowerOperation(FPOperation op) {
        return switch(op.operation()) {
            case "abs" -> new SQLExpression("ABS(?)");
            // ...
        };
    }
}
```

#### Pros
- Clean separation: FHIRPath IR vs target IR
- Each target can optimize independently
- Can add target-specific optimizations in lowering
- High-level IR stays stable across targets
- Most flexible for radically different targets

#### Cons
- Most complex implementation
- Two IR layers to maintain
- Potential performance overhead (though done once)
- Overkill if targets are similar (SparkSQL vs other SQL)

#### Multi-Target Support
**Rating: ★★★★★**
- Excellent: Explicit design for multiple targets
- Each target completely independent
- Can handle very different target paradigms

---

## Recommendations

### For Your Use Case

Given the requirements:
1. **Current target**: SparkSQL
2. **Future targets**: SQL Server SQL (similar paradigm)
3. **Main pain point**: Redundancy and class explosion

### Primary Recommendation: **Option 2 + Option 3 Hybrid**

**Combine signature-driven evaluation with visitor pattern:**

```java
// Minimal node types
sealed interface IRNode permits Operation, Literal, Traversal, ...

record Operation(
    String name,
    List<IRNode> args,
    FunctionSignature signature  // Stores resolved type
) implements IRNode {
    Type getType() { return signature.resultType(); }

    <T> T accept(IRNodeVisitor<T> visitor) {
        return visitor.visitOperation(this);
    }
}

// Target-specific visitors
interface SparkVisitor extends IRNodeVisitor<Column> {
    default Column visitOperation(Operation op) {
        List<Column> argCols = op.args().map(a -> a.accept(this)).toList();
        return SparkEvalRegistry.eval(op.name(), argCols, op.getType());
    }
}
```

**Benefits:**
- ✅ Eliminates one-class-per-function
- ✅ Single source of truth for types (signature)
- ✅ Easy to add new targets (new visitor)
- ✅ Clean, maintainable registry
- ✅ Similar SQL targets (SparkSQL, SQL Server) reuse most code

### Secondary Recommendation: **Option 1** (if maximum similarity to Spark Catalyst is desired)

**Choose this if:**
- Alignment with Spark's design philosophy is important
- Potential to leverage Spark's optimization patterns is valuable
- Clear separation of resolution vs evaluation phases is needed

This would be the most "Spark-native" approach and could potentially allow integration with Spark's optimizer in the future.

---

## Migration Path

**Incremental refactoring:**

1. **Phase 1**: Add `resolvedSignature` field to existing IRNode classes
2. **Phase 2**: Change `getType()` to return `resolvedSignature.resultType()`
3. **Phase 3**: Consolidate classes into generic `Operation` node
4. **Phase 4**: Implement visitor pattern for evaluation
5. **Phase 5**: Add SQL Server visitor

This allows gradual migration without breaking existing code.

---

## Key Design Insights

### Spark Catalyst Approach
Based on research into Spark's Catalyst optimizer:

- **Two-phase resolution**: Unresolved → Resolved expressions
- **Rule-based transformations**: Multiple passes until fixed point
- **Type propagation**: Types flow through expression tree during analysis
- **Catalog integration**: Resolves against external schema information
- **Separation of concerns**: Analysis, optimization, physical planning, code generation as distinct phases

### Applicable Patterns
1. **Visitor Pattern**: Best for multi-target support
2. **Strategy Pattern**: Good for pluggable evaluation logic
3. **Builder/Factory**: Useful for constructing complex IR trees
4. **Rule-based transformation**: Powerful for optimization passes

### Critical Success Factors
1. **Single source of truth** for types (avoid duplication)
2. **Minimal node hierarchy** (avoid class explosion)
3. **Target-agnostic IR** (reusable across backends)
4. **Clear separation** between resolution and evaluation
5. **Incremental migration** path from current design