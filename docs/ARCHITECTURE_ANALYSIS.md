# IRNode Architecture Analysis
**Date:** 2025-10-13
**Subject:** Comprehensive Analysis of IRNode System and Analyzer Implementation

---

## Executive Summary

This document provides a comprehensive architectural analysis of the current IRNode implementation and its usage by the Analyzer in the FHIRPath to SparkSQL translator. The analysis identifies key strengths and weaknesses, compares the design to industry-standard IR systems (LLVM, Spark Catalyst), and proposes concrete improvements to address redundancy, class proliferation, and multi-target reusability.

**Key Findings:**
- ✅ Strong foundation with type-safe signature-based resolution
- ⚠️ Type information redundantly defined in signatures and getType() methods
- ⚠️ Proliferation of IRNode subclasses (18+ and growing)
- ❌ Tight coupling to SparkSQL prevents reuse for other target systems
- ❌ Resolved signature metadata discarded after IR construction

**Recommended Approach:** Visitor-based code generation with attributed IR nodes and operation registry consolidation.

---

<analysis>

## 1. Current IRNode Implementation

### 1.1 Architecture Overview

The current system implements a **single-phase, signature-based resolution** pattern:

```
AST (FHIRPath syntax)
  ↓ parsed by ParserFacade
AstNode tree
  ↓ analyzed by Analyzer
  ↓ resolved via FunctionRegistry + OverloadResolver
IRNode tree (typed, with Cast nodes inserted)
  ↓ evaluated via eval()
Spark Column (SparkSQL expression)
```

**Core Components:**

1. **IRNode Interface** (`src/main/java/com/example/fhirpath/ir/IRNode.java:6-14`)
   ```java
   public interface IRNode {
       Type getType();
       Column eval();
   }
   ```
   - Minimal interface: type information + SparkSQL generation
   - No intermediate representation between IR and target

2. **Function Signatures** (`src/main/java/com/example/fhirpath/ir/math/Abs.java:19-21`)
   ```java
   public static final List<FunctionSignature> SIGNATURES = Stream.of(
       INTEGER, Type.DECIMAL, Type.QUANTITY
   ).map(t -> new FunctionSignature(List.of(t), t)).toList();
   ```
   - Static field in each IRNode class
   - Declares all supported type overloads
   - Includes both parameter types AND result types

3. **OverloadResolver** (`src/main/java/com/example/fhirpath/analyzer/OverloadResolver.java:23-61`)
   - Cost-based signature matching (exact match = 0, cast = 1)
   - Automatically inserts Cast and GetValue nodes for type adaptation
   - Selects best matching signature from candidates

4. **FunctionRegistry** (`src/main/java/com/example/fhirpath/analyzer/FunctionRegistry.java:26-44`)
   - Maps function/operator names → IRNodeBuilder instances
   - Uses IRClassBuilder to instantiate via reflection
   - Extracts SIGNATURES field reflectively

5. **IRNode Subclasses** (15+ concrete implementations)
   - Each FHIRPath function/operator has its own class
   - Records with child IRNode references
   - Implements both getType() and eval()

### 1.2 How Overloaded Signatures Work

**Declaration Example** (`src/main/java/com/example/fhirpath/ir/arythm/Add.java:21-30`):
```java
public static final List<FunctionSignature> SIGNATURES = List.of(
    biOperator(Type.INTEGER),                    // INTEGER + INTEGER → INTEGER
    biOperator(Type.DECIMAL),                    // DECIMAL + DECIMAL → DECIMAL
    biOperator(Type.QUANTITY),                   // QUANTITY + QUANTITY → QUANTITY
    biOperatorLeft(Type.DATE_TIME, Type.QUANTITY), // DATETIME + QUANTITY → DATETIME
    biOperator(Type.STRING)                      // STRING + STRING → STRING
);
```

**Resolution Process**:

1. **Analyzer** encounters `AstBinaryOperator("+", left, right)` (`Analyzer.java:93-95`)
2. **FunctionRegistry** looks up "+" → `forClass(Add.class)` (`FunctionRegistry.java:30`)
3. **OverloadResolver** receives:
   - Candidates: `Add.SIGNATURES` (5 overloads above)
   - Arguments: `[leftIR, rightIR]` with their types
4. **Matching Algorithm** (`OverloadResolver.java:28-54`):
   - For each signature, calculate adaptation cost:
     - Exact match = cost 0
     - Implicit cast available = cost 1
     - No adaptation possible = reject
   - Select signature with lowest cost
   - Return `ResolvedCall(signature, adaptedArgs)` with Cast nodes inserted
5. **IRNode Construction** (`FunctionRegistry.java:75`):
   - `builder.build(resolvedCall.args())` creates `Add(leftIR, rightIR)`
   - **Signature is discarded** - only adapted args are kept

**Type Adaptation** (`OverloadResolver.java:66-87`):
```java
private static Adapt adapt(IRNode arg, Type target) {
    Type actual = arg.getType();
    if (actual.effectiveType() == target.effectiveType()) {
        return new Adapt(arg, true, 0);  // Exact match
    }

    if (TypeSystem.canCast(actual, target)) {
        // Insert Cast node
        final IRNode implicitCast;
        if (actual instanceof FhirType && target instanceof PrimitiveType) {
            implicitCast = new Cast(new GetValue(arg), target);
        } else {
            implicitCast = new Cast(arg, target);
        }
        return new Adapt(implicitCast, true, 1);  // Cast with cost 1
    }

    return new Adapt(arg, false, Integer.MAX_VALUE);  // No match
}
```

### 1.3 How Result Types Are Calculated

**Problem: Two Sources of Truth**

Result types are defined in **two places**:

1. **In FunctionSignature** (`Add.SIGNATURES` shown above)
   - Each signature declares `resultType`
   - Example: `biOperator(Type.INTEGER)` → signature with resultType = INTEGER

2. **In getType() Method** (`Add.java:33-35`)
   ```java
   @Override
   public Type getType() {
       return left.getType();  // Assumes left operand's type is result type
   }
   ```

**Resolution Process:**
- OverloadResolver picks signature based on parameter types
- That signature **has a resultType field** (e.g., INTEGER)
- Signature is passed to builder, then **discarded**
- Later, when IR node's type is queried, getType() **recalculates** from children

**Example Trace:**
```
1. Parse: "age + 5" where age: INTEGER
2. Analyze left → leftIR with type INTEGER
3. Analyze right → rightIR with type INTEGER (literal)
4. OverloadResolver receives:
   - candidates: Add.SIGNATURES (5 overloads)
   - args: [leftIR:INTEGER, rightIR:INTEGER]
5. OverloadResolver selects:
   - signature: biOperator(INTEGER) with resultType=INTEGER ← SELECTED BUT DISCARDED
   - args: [leftIR, rightIR] (no casts needed)
6. Create: Add(leftIR, rightIR)
7. Query type: add.getType() → left.getType() → INTEGER ← RECALCULATED
```

**Redundancy Analysis:**

| IRNode Class | Signature Result Type | getType() Implementation | Match? |
|--------------|----------------------|--------------------------|--------|
| Add | `left.type` (via biOperator) | `left.getType()` | ✅ |
| Abs | `target.type` (via unary sig) | `target.getType()` | ✅ |
| GreaterThan | `BOOLEAN` (via biOperator) | `Type.BOOLEAN` (from interface) | ✅ |
| Count | N/A (no signatures) | `Type.INTEGER` | ➖ |
| Substring | `STRING` | `Type.STRING` | ✅ |

**Risk:** If signature declaration and getType() logic diverge, type system becomes inconsistent.

### 1.4 Redundancy in Type Definitions

**Location 1: Static SIGNATURES Field**

Every operation class declares signatures:
- `Add.SIGNATURES` (5 overloads) - `Add.java:21-30`
- `Abs.SIGNATURES` (3 overloads) - `Abs.java:19-21`
- `ComparisonOperator.SIGNATURES` (7 overloads) - `ComparisonOperator.java:22-32`
- `Union.SIGNATURES` (all types) - `Union.java:17-21`

**Location 2: getType() Method**

Every operation implements type calculation:
- `Add.getType()` returns `left.getType()`
- `Abs.getType()` returns `target.getType()`
- `ComparisonOperator.getType()` returns `Type.BOOLEAN`
- `Count.getType()` returns `Type.INTEGER`

**Location 3: eval() Method Type Switches**

Many operations switch on type again for evaluation:
```java
// Abs.java:35-39
return switch ((PrimitiveType) getType()) {
    case INTEGER, DECIMAL -> functions.abs(target.eval());
    case QUANTITY -> quantity(target.eval()).abs();
    default -> throw new IllegalArgumentException("Unsupported result type for Abs: " + getType());
};
```

**Redundancy Count:**
- Result type in signature declaration
- Result type calculation in getType()
- Result type switching in eval()
- **Total: 3 places that must be kept in sync**

### 1.5 Current IRNode Subclass Inventory

**Arithmetic Operations** (3 classes):
- `Add` - Addition with 5 overloads
- `Sub` - Subtraction with 3 overloads
- `Divide` - Division with 3 overloads

**Comparison Operations** (4 classes + 1 interface):
- `ComparisonOperator` - Shared interface with 7 type overloads
- `GreaterThan` - Implements comparison with type-specific logic
- `LessThan` - Similar to GreaterThan
- `GreaterEqual` - Similar to GreaterThan
- `Equals` - Equality with special null handling

**Math Functions** (2 classes):
- `Abs` - Absolute value with 3 overloads
- `Exp` - Exponential with 2 overloads

**String Functions** (1 class):
- `Substring` - String slicing with variable arity (2-3 params)

**Aggregate Functions** (2 classes):
- `Count` - Returns INTEGER, no type restrictions
- `Exists` - Returns BOOLEAN, no type restrictions

**Infrastructure Nodes** (6 classes):
- `Literal` - Constant values with type
- `Cast` - Type conversion
- `GetValue` - Extract value from FHIR types
- `Traversal` - Field access on complex types
- `Resource` - Root resource reference
- `Union` - Collection union with all type overloads

**Total: 18 classes**

**Growth Rate:**
- FHIRPath spec has 50+ functions
- Current coverage: ~15 functions (30%)
- **Projected: 50+ classes at full coverage**

### 1.6 Separation Between IRNode Subclasses

**Current Pattern:**

Each function/operator has its own class, but some abstraction exists:

1. **ComparisonOperator Interface** (`ComparisonOperator.java:13-53`)
   - Shared SIGNATURES for all comparisons
   - Common getType() → BOOLEAN
   - Subclasses implement `evalColumns(Column, Column)`
   - **Good abstraction:** Reduces duplication for 4 comparison classes

2. **Independent Classes** (Add, Abs, Substring, etc.)
   - Each standalone record
   - No shared logic between similar operations
   - Example: Add, Sub, Divide all have similar structure but no common base

3. **Infrastructure Classes** (Literal, Cast, Traversal)
   - Don't represent FHIRPath functions
   - Unique structure and behavior
   - Unlikely to benefit from consolidation

**Question: Are Separate Classes Needed?**

**Analysis by Category:**

| Category | Current Design | Alternative | Recommendation |
|----------|---------------|-------------|----------------|
| Arithmetic (Add, Sub, Divide) | 3 classes, similar structure | Generic `BinaryArithmeticOp(name, ...)` | **Consolidate** |
| Comparison (GT, LT, GEQ) | 4 classes, shared interface | Already abstracted well | **Keep interface** |
| Math (Abs, Exp) | 2 classes, identical structure | Generic `UnaryMathOp(name, ...)` | **Consolidate** |
| String (Substring) | 1 class, complex logic | Keep specific class | **Keep** |
| Aggregates (Count, Exists) | 2 classes, simple | Generic `AggregateOp(name, ...)` | **Could consolidate** |
| Infrastructure | 6 classes, unique | Keep as-is | **Keep** |

**Consolidation Potential:**
- Arithmetic: 3 → 1 class
- Math: 2 → 1 class
- Aggregates: 2 → 1 class
- **Total reduction: 18 → 13 classes (28% reduction)**

**Full Consolidation (Generic Operation):**
- All operations → 1 `Operation(name, args, signature)` class
- **Total reduction: 18 → 7 classes (61% reduction)**
  - 1 Operation (all functions/operators)
  - 6 infrastructure classes

### 1.7 Reusability for Different Target Systems

**Current Coupling to SparkSQL:**

Every IRNode's eval() returns `org.apache.spark.sql.Column`:
```java
public interface IRNode {
    Type getType();
    Column eval();  // ← Spark-specific return type
}
```

**Evidence of Spark Dependency:**

1. **Direct Spark Function Calls** (`Abs.java:36-37`)
   ```java
   case INTEGER, DECIMAL -> functions.abs(target.eval());
   ```

2. **Spark Custom Types** (`Add.java:42-43`)
   ```java
   case DATE_TIME -> dateTime(left.eval()).plus(quantity(right.eval()));
   case QUANTITY -> quantity(left.eval()).plus(quantity(right.eval()));
   ```

3. **Complex Spark Column Logic** (`Substring.java:28-51`)
   ```java
   final Column nullCondition = nullPropagationCondition.or(posOutOfBoundsCondition);
   return functions.when(functions.not(nullCondition),
       functions.substr(targetColumn, posColumn, nonNullLengthColumn));
   ```

**Reusability Assessment:**

| Target System | Can Reuse IR Tree? | Can Reuse Signature System? | Effort to Add Support |
|---------------|-------------------|----------------------------|----------------------|
| SparkSQL | ✅ Yes (current) | ✅ Yes | N/A |
| SQL Server | ❌ No (eval() is Spark) | ✅ Yes | **High** - Rewrite all eval() |
| PostgreSQL | ❌ No (eval() is Spark) | ✅ Yes | **High** - Rewrite all eval() |
| FHIR JSON | ❌ No (eval() is Spark) | ⚠️ Partial | **Very High** |

**Current Reusability Score: 2/10**
- Signature system is target-agnostic ✅
- Type system is target-agnostic ✅
- IR tree structure is target-agnostic ✅
- **BUT eval() locks to Spark ❌**

**Blocker:** Adding SQL Server support would require:
1. Create SqlServerIRNode interface with `String toSql()` method
2. Duplicate all 18 IRNode classes with SQL Server logic
3. Maintain two parallel hierarchies

**Alternative Approach (Visitor Pattern):**
1. Remove eval() from IRNode
2. Add `<T> T accept(IRNodeVisitor<T> visitor)`
3. Implement `SparkCodeGenerator implements IRNodeVisitor<Column>`
4. Implement `SqlServerCodeGenerator implements IRNodeVisitor<String>`
5. **Result: Single IR tree, multiple backends**

</analysis>

---

<design_options>

## 2. Design Options

Based on the analysis and expert consultations, here are four distinct architectural approaches:

### Option 1: Visitor Pattern with Attributed Nodes

**Description:**
- Store resolved FunctionSignature in each Operation node
- Remove eval() from IRNode interface
- Add accept(IRNodeVisitor<T>) for code generation
- Create target-specific visitors (SparkCodeGenerator, SqlServerCodeGenerator)

**Implementation:**

```java
// Core IR interface
public sealed interface IRNode permits Operation, Literal, Traversal, Cast, Resource {
    Type getType();
    <T> T accept(IRNodeVisitor<T> visitor);
}

// Generic operation node
public record Operation(
    String name,
    List<IRNode> args,
    FunctionSignature signature  // ← STORED
) implements IRNode {

    @Override
    public Type getType() {
        return signature.resultType();  // ← Single source of truth
    }

    @Override
    public <T> T accept(IRNodeVisitor<T> visitor) {
        return visitor.visitOperation(this);
    }
}

// Visitor interface
public interface IRNodeVisitor<T> {
    T visitOperation(Operation node);
    T visitLiteral(Literal node);
    T visitTraversal(Traversal node);
    T visitCast(Cast node);
    T visitResource(Resource node);
}

// Spark backend
public class SparkCodeGenerator implements IRNodeVisitor<Column> {
    @Override
    public Column visitOperation(Operation op) {
        List<Column> argCols = op.args().stream()
            .map(arg -> arg.accept(this))
            .toList();

        return switch(op.name()) {
            case "add" -> evaluateAdd(argCols, op.getType());
            case "abs" -> evaluateAbs(argCols, op.getType());
            // ...
        };
    }

    private Column evaluateAdd(List<Column> args, Type resultType) {
        return switch((PrimitiveType) resultType) {
            case INTEGER, DECIMAL -> args.get(0).plus(args.get(1));
            case STRING -> functions.concat(args.get(0), args.get(1));
            case QUANTITY -> quantity(args.get(0)).plus(quantity(args.get(1)));
            // ...
        };
    }
}
```

**How It Addresses Issues:**

1. **Type Redundancy:** ✅ Eliminated
   - Result type stored once in signature
   - getType() returns signature.resultType()
   - No recalculation

2. **Class Proliferation:** ✅ Reduced 61%
   - 18 operation classes → 1 Operation class
   - All function logic in visitors
   - Only 7 IR node types total

3. **Multi-Target Reusability:** ✅ Fully Supported
   - IR tree is target-agnostic
   - Add new target = implement new visitor
   - No modification to IR classes

4. **Lost Signature Metadata:** ✅ Preserved
   - Signature stored in node
   - Can query "which overload was chosen?"
   - Enables signature-based optimizations

**Pros:**
- ✅ Single IR tree, multiple backends
- ✅ Eliminates type redundancy completely
- ✅ Dramatically reduces class count
- ✅ Standard compiler pattern (proven)
- ✅ Easy to add new targets
- ✅ IR can be inspected/optimized before codegen
- ✅ Testing without Spark is possible

**Cons:**
- ⚠️ More indirection (accept/visit calls)
- ⚠️ Function logic spread across visitor methods
- ⚠️ Requires significant refactoring

**Impact on Reusability:**
- **Before:** 2/10 (Spark-locked)
- **After:** 9/10 (visitor per target)

**Implementation Complexity:**
- **Effort:** Medium-High (3-4 weeks)
- **Risk:** Medium (requires careful migration)
- **Phases:**
  1. Add signature storage to existing classes (1 week)
  2. Extract visitor interface + SparkCodeGenerator (1 week)
  3. Consolidate to Operation node (1 week)
  4. Add SQL Server visitor as proof of concept (1 week)

---

### Option 2: Catalyst-Style Two-Phase Resolution

**Description:**
- Separate unresolved and resolved IR node types
- Build unresolved IR tree during parsing
- Apply analysis rules to transform unresolved → resolved
- Enables rule-based optimizations and better error messages

**Implementation:**

```java
// Unresolved nodes (AST-like but typed)
public sealed interface UnresolvedIRNode {
    record UnresolvedOperation(String name, List<UnresolvedIRNode> args)
        implements UnresolvedIRNode {}
    record UnresolvedLiteral(Object value)
        implements UnresolvedIRNode {}
    // ...
}

// Resolved nodes (current IRNode)
public sealed interface ResolvedIRNode {
    Type getType();
    Column eval();  // Or accept(visitor) if combined with Option 1

    record ResolvedOperation(String name, List<ResolvedIRNode> args, FunctionSignature signature)
        implements ResolvedIRNode {}
    // ...
}

// Analysis rules
public interface AnalysisRule {
    UnresolvedIRNode resolve(UnresolvedIRNode node, AnalysisContext ctx);
}

public class TypeResolutionRule implements AnalysisRule {
    @Override
    public UnresolvedIRNode resolve(UnresolvedIRNode node, AnalysisContext ctx) {
        if (node instanceof UnresolvedOperation op) {
            // Resolve children first
            List<ResolvedIRNode> resolvedArgs = op.args().stream()
                .map(arg -> (ResolvedIRNode) resolve(arg, ctx))
                .toList();

            // Resolve overload
            List<FunctionSignature> sigs = OperationRegistry.getSignatures(op.name());
            ResolvedCall call = OverloadResolver.resolveCall(sigs, resolvedArgs);

            return new ResolvedOperation(op.name(), call.args(), call.signature());
        }
        // ...
    }
}

// Analyzer runs rules
public class Analyzer {
    private List<AnalysisRule> rules = List.of(
        new TypeResolutionRule(),
        new CastInsertionRule(),
        new ConstantFoldingRule()  // Optimization!
    );

    public ResolvedIRNode analyze(AstNode ast) {
        UnresolvedIRNode unresolved = buildUnresolvedIR(ast);

        for (AnalysisRule rule : rules) {
            unresolved = rule.resolve(unresolved, context);
        }

        return (ResolvedIRNode) unresolved;
    }
}
```

**How It Addresses Issues:**

1. **Type Redundancy:** ✅ Eliminated
   - Resolved nodes store signature
   - Clear separation of untyped/typed phases

2. **Class Proliferation:** ➖ No improvement
   - Still need separate classes per function
   - Actually doubles classes (unresolved + resolved)

3. **Multi-Target Reusability:** ⚠️ Partial
   - Resolved IR still has eval()
   - Must combine with Option 1 for full benefit

4. **Lost Signature Metadata:** ✅ Preserved
   - Signature stored in resolved nodes

**Pros:**
- ✅ Better error messages (can show unresolved tree)
- ✅ Enables optimization rules (constant folding, etc.)
- ✅ Clear separation of syntax and semantics
- ✅ Can inspect unresolved IR for debugging
- ✅ Modular analysis (add/remove rules easily)

**Cons:**
- ⚠️ Significant complexity increase
- ⚠️ Doubles node types (unresolved + resolved)
- ⚠️ May be overkill for expression translator
- ⚠️ Doesn't solve multi-target problem alone

**Impact on Reusability:**
- **Before:** 2/10
- **After (alone):** 3/10
- **After (+ Option 1):** 9/10

**Implementation Complexity:**
- **Effort:** High (5-6 weeks)
- **Risk:** High (fundamental architecture change)
- **Recommendation:** Only pursue if optimization rules are critical

---

### Option 3: Operation Registry with Evaluation Lambdas

**Description:**
- Consolidate all operations into OperationRegistry
- Store evaluation logic as lambdas in registry
- Single Operation class for all functions/operators
- Keep eval() in IRNode but dispatch to registry

**Implementation:**

```java
// Generic operation node
public record Operation(
    String name,
    List<IRNode> args,
    FunctionSignature signature
) implements IRNode {

    @Override
    public Type getType() {
        return signature.resultType();
    }

    @Override
    public Column eval() {
        List<Column> argCols = args.stream().map(IRNode::eval).toList();
        return OperationRegistry.evaluate(name, argCols, signature);
    }
}

// Centralized registry
public class OperationRegistry {

    // Function signature: (args, resultType) -> Column
    private record OperationDef(
        List<FunctionSignature> signatures,
        TriFunction<String, List<Column>, Type, Column> evaluator
    ) {}

    private static final Map<String, OperationDef> OPERATIONS = new HashMap<>();

    static {
        // Arithmetic operations
        OPERATIONS.put("add", new OperationDef(
            List.of(
                biOperator(INTEGER),
                biOperator(DECIMAL),
                biOperator(QUANTITY),
                biOperatorLeft(DATE_TIME, QUANTITY),
                biOperator(STRING)
            ),
            OperationRegistry::evaluateAdd
        ));

        // Math operations
        OPERATIONS.put("abs", new OperationDef(
            List.of(
                signature(INTEGER, INTEGER),
                signature(DECIMAL, DECIMAL),
                signature(QUANTITY, QUANTITY)
            ),
            OperationRegistry::evaluateAbs
        ));
    }

    public static List<FunctionSignature> getSignatures(String name) {
        return OPERATIONS.get(name).signatures();
    }

    public static Column evaluate(String name, List<Column> args, Type resultType) {
        return OPERATIONS.get(name).evaluator().apply(name, args, resultType);
    }

    private static Column evaluateAdd(String name, List<Column> args, Type resultType) {
        Column left = args.get(0);
        Column right = args.get(1);

        return switch((PrimitiveType) resultType) {
            case INTEGER, DECIMAL -> left.plus(right);
            case STRING -> functions.concat(left, right);
            case DATE_TIME -> dateTime(left).plus(quantity(right));
            case QUANTITY -> quantity(left).plus(quantity(right));
            default -> throw new IllegalArgumentException("Unsupported type: " + resultType);
        };
    }

    private static Column evaluateAbs(String name, List<Column> args, Type resultType) {
        Column target = args.get(0);

        return switch((PrimitiveType) resultType) {
            case INTEGER, DECIMAL -> functions.abs(target);
            case QUANTITY -> quantity(target).abs();
            default -> throw new IllegalArgumentException("Unsupported type: " + resultType);
        };
    }
}
```

**How It Addresses Issues:**

1. **Type Redundancy:** ✅ Eliminated
   - Signature stored in Operation node
   - No recalculation in getType()

2. **Class Proliferation:** ✅ Reduced 61%
   - 18 operation classes → 1 Operation class
   - Logic centralized in registry

3. **Multi-Target Reusability:** ❌ Not Addressed
   - Still uses eval() → Column
   - Still Spark-specific

4. **Lost Signature Metadata:** ✅ Preserved
   - Signature available in Operation node

**Pros:**
- ✅ Dramatically reduces class count
- ✅ All function logic in one place (easier to audit)
- ✅ Easy to add new functions (just add to registry)
- ✅ Eliminates reflection-based builder
- ✅ Simpler than visitor pattern

**Cons:**
- ⚠️ Still locked to SparkSQL
- ⚠️ Evaluation logic less discoverable (in registry not classes)
- ⚠️ Cannot add SQL Server support without major refactoring

**Impact on Reusability:**
- **Before:** 2/10
- **After:** 2/10 (no improvement)

**Implementation Complexity:**
- **Effort:** Medium (2-3 weeks)
- **Risk:** Low (straightforward consolidation)
- **Recommendation:** Good intermediate step toward Option 1

---

### Option 4: Hybrid - Registry + Visitor Pattern

**Description:**
- Combine benefits of Option 1 and Option 3
- Use OperationRegistry for signatures
- Use Visitor pattern for multi-target codegen
- Single Operation class with accept(visitor)

**Implementation:**

```java
// Operation registry (signatures only)
public class OperationRegistry {
    private static final Map<String, List<FunctionSignature>> SIGNATURES = new HashMap<>();

    static {
        register("add", List.of(
            biOperator(INTEGER),
            biOperator(DECIMAL),
            biOperator(QUANTITY),
            biOperatorLeft(DATE_TIME, QUANTITY),
            biOperator(STRING)
        ));

        register("abs", List.of(
            signature(INTEGER, INTEGER),
            signature(DECIMAL, DECIMAL),
            signature(QUANTITY, QUANTITY)
        ));

        // ... all other operations
    }

    public static List<FunctionSignature> getSignatures(String name) {
        return SIGNATURES.getOrDefault(name, List.of());
    }
}

// Generic operation node
public record Operation(
    String name,
    List<IRNode> args,
    FunctionSignature signature
) implements IRNode {

    @Override
    public Type getType() {
        return signature.resultType();
    }

    @Override
    public <T> T accept(IRNodeVisitor<T> visitor) {
        return visitor.visitOperation(this);
    }
}

// Spark code generator
public class SparkCodeGenerator implements IRNodeVisitor<Column> {
    @Override
    public Column visitOperation(Operation op) {
        List<Column> args = op.args().stream()
            .map(arg -> arg.accept(this))
            .toList();

        return evaluateOperation(op.name(), args, op.getType());
    }

    private Column evaluateOperation(String name, List<Column> args, Type type) {
        // Dispatch based on operation name and type
        return switch(name) {
            case "add" -> evaluateAdd(args, type);
            case "sub" -> evaluateSub(args, type);
            case "abs" -> evaluateAbs(args, type);
            case "gt" -> evaluateGreaterThan(args, type);
            // ... all operations
            default -> throw new UnsupportedOperationException("Unknown operation: " + name);
        };
    }

    private Column evaluateAdd(List<Column> args, Type resultType) {
        // Same as Option 3
        return switch((PrimitiveType) resultType) {
            case INTEGER, DECIMAL -> args.get(0).plus(args.get(1));
            case STRING -> functions.concat(args.get(0), args.get(1));
            // ...
        };
    }

    // ... other evaluation methods
}

// SQL Server code generator (future)
public class SqlServerCodeGenerator implements IRNodeVisitor<String> {
    @Override
    public String visitOperation(Operation op) {
        List<String> args = op.args().stream()
            .map(arg -> arg.accept(this))
            .toList();

        return evaluateOperation(op.name(), args, op.getType());
    }

    private String evaluateOperation(String name, List<String> args, Type type) {
        return switch(name) {
            case "add" -> evaluateAdd(args, type);
            case "abs" -> "ABS(" + args.get(0) + ")";
            // ...
        };
    }

    private String evaluateAdd(List<String> args, Type type) {
        return switch((PrimitiveType) type) {
            case INTEGER, DECIMAL -> "(" + args.get(0) + " + " + args.get(1) + ")";
            case STRING -> "CONCAT(" + args.get(0) + ", " + args.get(1) + ")";
            // ...
        };
    }
}
```

**How It Addresses Issues:**

1. **Type Redundancy:** ✅ Eliminated
2. **Class Proliferation:** ✅ Reduced 61%
3. **Multi-Target Reusability:** ✅ Fully Supported
4. **Lost Signature Metadata:** ✅ Preserved

**Pros:**
- ✅ All benefits of Option 1 (visitor pattern)
- ✅ All benefits of Option 3 (registry consolidation)
- ✅ Clean separation: Registry = signatures, Visitor = codegen
- ✅ Easy to add operations (registry)
- ✅ Easy to add targets (visitor)
- ✅ Best of both worlds

**Cons:**
- ⚠️ Most complex to implement initially
- ⚠️ Requires learning both patterns

**Impact on Reusability:**
- **Before:** 2/10
- **After:** 10/10 (best possible)

**Implementation Complexity:**
- **Effort:** High (4-5 weeks)
- **Risk:** Medium
- **Recommendation:** Best long-term solution

---

## 3. Design Options Comparison Matrix

| Criteria | Option 1: Visitor | Option 2: Two-Phase | Option 3: Registry | Option 4: Hybrid |
|----------|------------------|--------------------|--------------------|------------------|
| **Eliminates Type Redundancy** | ✅ Yes | ✅ Yes | ✅ Yes | ✅ Yes |
| **Reduces Class Count** | ✅ 61% reduction | ❌ Doubles classes | ✅ 61% reduction | ✅ 61% reduction |
| **Multi-Target Support** | ✅ Excellent | ⚠️ Requires visitor | ❌ No | ✅ Excellent |
| **Enables IR Optimizations** | ⚠️ Manual | ✅ Rule-based | ❌ No | ⚠️ Manual |
| **Preserves Signature Metadata** | ✅ Yes | ✅ Yes | ✅ Yes | ✅ Yes |
| **Implementation Effort** | Medium-High | High | Medium | High |
| **Implementation Risk** | Medium | High | Low | Medium |
| **Complexity for Developers** | Medium | High | Low | Medium |
| **Alignment with Catalyst** | Partial | High | Low | High |
| **Alignment with LLVM** | High | Medium | Low | High |
| **Testability** | ✅ Excellent | ✅ Excellent | ⚠️ Same as now | ✅ Excellent |
| **Incremental Migration** | ✅ Possible | ❌ All-or-nothing | ✅ Easy | ⚠️ Moderate |

**Scoring (0-10 scale):**

| Option | Type Safety | Reusability | Maintainability | Performance | Total |
|--------|-------------|-------------|-----------------|-------------|-------|
| Option 1: Visitor | 9 | 9 | 8 | 9 | **35/40** |
| Option 2: Two-Phase | 10 | 7 | 6 | 8 | **31/40** |
| Option 3: Registry | 8 | 2 | 9 | 10 | **29/40** |
| Option 4: Hybrid | 10 | 10 | 8 | 9 | **37/40** |
| Current Design | 7 | 2 | 5 | 10 | **24/40** |

</design_options>

---

<recommendation>

## 4. Recommended Approach

### Primary Recommendation: **Option 4 - Hybrid (Registry + Visitor Pattern)**

**Rationale:**

This approach provides the best balance of:
1. **Immediate value** - Eliminates type redundancy and class proliferation
2. **Future-proofing** - Enables multi-target support (SQL Server, PostgreSQL, etc.)
3. **Maintainability** - Centralized registry + modular visitors
4. **Alignment with industry patterns** - Matches compiler/optimizer best practices
5. **Incremental delivery** - Can be implemented in phases

### Why This Recommendation?

**Addresses All Identified Issues:**
- ✅ Type redundancy eliminated (signature stored once)
- ✅ Class proliferation resolved (18 → 7 classes)
- ✅ Multi-target reusability enabled (visitor per target)
- ✅ Signature metadata preserved (stored in nodes)
- ✅ Reflection eliminated (direct registry lookup)

**Aligns with Your Requirements:**
- ✅ Reuse IR tree for multiple targets (SparkSQL, SQL Server, etc.)
- ✅ Type-safe resolution (signature system unchanged)
- ✅ Performance (no overhead, direct code generation)
- ✅ Testability (can mock visitors, test IR separately)

**Industry-Proven Patterns:**
- ✅ LLVM uses visitor pattern for multi-target codegen
- ✅ Spark Catalyst uses similar registry for functions
- ✅ Java compiler uses visitor for AST processing

### Implementation Roadmap (4 Phases)

#### **Phase 1: Attributed Nodes** (Week 1-2)
**Goal:** Eliminate type redundancy by storing signatures

**Changes:**
1. Add `signature` field to existing operation classes
2. Update constructors to accept FunctionSignature
3. Change getType() to return `signature.resultType()`
4. Update FunctionRegistry to pass signature to builders

**Files Modified:**
- All operation classes (Add, Abs, etc.)
- `FunctionRegistry.java:68-76` - pass signature to builder
- `IRClassBuilder.java` - accept signature parameter

**Testing:**
- All existing tests should pass unchanged
- Add tests verifying signature is stored correctly

**Risk:** Low (backward compatible change)

#### **Phase 2: Operation Registry** (Week 3)
**Goal:** Consolidate operation definitions

**Changes:**
1. Create `OperationRegistry.java` with all function signatures
2. Move SIGNATURES from operation classes to registry
3. Update FunctionRegistry to query OperationRegistry

**New Files:**
- `src/main/java/com/example/fhirpath/ir/OperationRegistry.java`

**Files Modified:**
- `FunctionRegistry.java:37-44` - use OperationRegistry

**Files Deleted:**
- Static SIGNATURES from Add, Abs, etc. (but keep classes for now)

**Testing:**
- Verify all operations still resolve correctly
- Add registry-specific tests

**Risk:** Low (pure refactoring)

#### **Phase 3: Visitor Infrastructure** (Week 4-5)
**Goal:** Extract evaluation logic to visitors

**Changes:**
1. Create `IRNodeVisitor<T>` interface
2. Create `SparkCodeGenerator implements IRNodeVisitor<Column>`
3. Move eval() logic from operation classes to SparkCodeGenerator
4. Add accept(visitor) method to IRNode
5. Make eval() a default method that calls accept(new SparkCodeGenerator())

**New Files:**
- `src/main/java/com/example/fhirpath/ir/IRNodeVisitor.java`
- `src/main/java/com/example/fhirpath/codegen/SparkCodeGenerator.java`

**Files Modified:**
- `IRNode.java:6-14` - add accept() method
- All operation classes - implement accept()

**Testing:**
- All existing tests should pass (eval() still works)
- Add visitor-specific tests

**Risk:** Medium (new abstraction, but backward compatible)

#### **Phase 4: Consolidate to Operation** (Week 6)
**Goal:** Eliminate operation class proliferation

**Changes:**
1. Create `Operation(name, args, signature)` record
2. Update FunctionRegistry to create Operation instances
3. Update SparkCodeGenerator to handle Operation nodes
4. Remove old operation classes (Add, Abs, etc.)

**New Files:**
- `src/main/java/com/example/fhirpath/ir/Operation.java`

**Files Deleted:**
- `Add.java`, `Abs.java`, `Exp.java`, etc. (12 operation classes)

**Files Modified:**
- `FunctionRegistry.java` - create Operation instead of specific classes
- `SparkCodeGenerator.java` - handle all operations generically

**Testing:**
- Update tests to work with Operation instead of specific classes
- Verify all function/operator combinations still work

**Risk:** Medium (significant refactoring, but well-tested by previous phases)

### Post-Implementation Benefits

**Immediate (After Phase 4):**
1. **18 → 7 classes** (61% reduction in IR node types)
2. **Single source of truth for types** (signature stored once)
3. **Centralized function definitions** (OperationRegistry)
4. **Eliminated reflection** (direct instantiation)

**Future (Phase 5+):**
1. **SQL Server support:** Implement `SqlServerCodeGenerator` (1-2 weeks)
2. **PostgreSQL support:** Implement `PostgreSqlCodeGenerator` (1-2 weeks)
3. **Optimization passes:** Implement `ConstantFoldingVisitor`, etc.
4. **IR serialization:** For debugging/logging
5. **Alternative backends:** JSON Path, GraphQL, etc.

### Migration Risk Assessment

**Low Risk:**
- Phases 1-2 are pure refactoring with no behavior change
- All existing tests continue to pass
- Incremental delivery allows validation at each step

**Medium Risk:**
- Phase 3 introduces new abstraction (visitor)
- Phase 4 consolidates classes (most significant change)
- Mitigation: Keep eval() working until Phase 4 complete

**Rollback Strategy:**
- Each phase is independently revertible
- Git branches per phase for easy rollback
- Feature flags to toggle between old/new implementations during Phase 3

### Alternative Recommendation: **Option 1 (Visitor Only)**

**If multi-target support is lower priority:**

Skip the registry consolidation (Phase 2) and go directly to visitor pattern:
- **Effort:** 3 weeks instead of 6
- **Class reduction:** 0% (keep separate classes)
- **Reusability:** Same (visitor enables multi-target)
- **Type redundancy:** Eliminated (store signature)

**Best for:**
- Teams with limited capacity
- Projects where SQL Server support is uncertain
- Codebases where developers prefer explicit classes over generic Operation

### Why NOT Other Options?

**Option 2 (Two-Phase):**
- High complexity for uncertain benefit
- Optimizations can be added later via visitor transformations
- Doubles class count (unresolved + resolved)
- Recommendation: **Defer** until optimization needs are clear

**Option 3 (Registry Only):**
- Doesn't solve multi-target problem
- Only addresses class proliferation
- Better as intermediate step (Phase 2) than final goal
- Recommendation: **Include as part of Option 4**

### Success Criteria

**After Phase 4, the system should:**
1. ✅ Have exactly 7 IR node types (Operation + 6 infrastructure)
2. ✅ Store each result type in exactly one place (signature)
3. ✅ Support SparkSQL code generation via SparkCodeGenerator
4. ✅ Have zero reflection-based instantiation
5. ✅ Pass all existing tests unchanged
6. ✅ Be ready to add SQL Server support in 1-2 weeks

**Metrics:**
- Lines of code: ~20% reduction
- Class count: 61% reduction (18 → 7)
- Type redundancy: 100% eliminated
- Test coverage: Maintained at current level
- Performance: No degradation (visitor is zero-cost abstraction)

### Next Steps

1. **Review this analysis** with team and stakeholders
2. **Approve phased approach** (or select alternative)
3. **Create detailed design for Phase 1** (attributed nodes)
4. **Set up feature branch** for phased development
5. **Begin Phase 1 implementation** with test-first approach

</recommendation>

---

## Appendices

### Appendix A: File References

All file paths are relative to `/Users/szu004/dev/fp2sql/`

**Core IR:**
- `src/main/java/com/example/fhirpath/ir/IRNode.java:6-14`
- `src/main/java/com/example/fhirpath/ir/math/Abs.java`
- `src/main/java/com/example/fhirpath/ir/arythm/Add.java`
- `src/main/java/com/example/fhirpath/ir/string/Substring.java`
- `src/main/java/com/example/fhirpath/ir/comparison/ComparisonOperator.java`

**Analysis:**
- `src/main/java/com/example/fhirpath/analyzer/Analyzer.java`
- `src/main/java/com/example/fhirpath/analyzer/FunctionRegistry.java`
- `src/main/java/com/example/fhirpath/analyzer/OverloadResolver.java`
- `src/main/java/com/example/fhirpath/analyzer/FunctionSignature.java`

**Builders:**
- `src/main/java/com/example/fhirpath/ir/builder/IRNodeBuilder.java`
- `src/main/java/com/example/fhirpath/ir/builder/IRClassBuilder.java`

**Type System:**
- `src/main/java/com/example/fhirpath/typing/Type.java`
- `src/main/java/com/example/fhirpath/typing/TypeSystem.java`

### Appendix B: Expert Agent Outputs

Two specialized agents were consulted:

1. **Architect Agent:** Provided comprehensive analysis comparing current design to LLVM IR, Spark Catalyst, and SQL optimizer patterns. Recommended visitor pattern with attributed nodes.

2. **Spark Expert Agent:** Provided detailed comparison to Catalyst's two-phase resolution, rule-based transformations, and logical/physical separation. Recommended SqlExpression abstraction for multi-dialect support.

Both agents' full analyses are available in the consultation outputs.

### Appendix C: Glossary

- **IR (Intermediate Representation):** Abstract syntax tree with type information, between AST and target code
- **Attributed Node:** IR node that stores metadata (like resolved signature) for later use
- **Visitor Pattern:** Design pattern that separates tree structure from operations on that tree
- **Overload Resolution:** Process of selecting the best function signature for given arguments
- **Type Coercion:** Automatic insertion of type conversion (cast) operations
- **Catalyst:** Apache Spark's query optimizer using rule-based transformations
- **Two-Phase Resolution:** Separate passes for building unresolved tree and resolving types

---

**Document Version:** 1.0
**Last Updated:** 2025-10-13
**Author:** Claude (with architect and spark-expert consultation)