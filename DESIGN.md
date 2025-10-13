# FHIRPath to SQL Translation: Architecture Design

**Date:** 2025-10-13
**Status:** Implementation Design
**Approach:** Hybrid Registry + Visitor Pattern with Type Resolution

---

## Table of Contents

1. [Executive Summary](#executive-summary)
2. [Design Principles](#design-principles)
3. [Architecture Overview](#architecture-overview)
4. [Type Resolution System](#type-resolution-system)
5. [Core Components](#core-components)
6. [Multi-Target Support](#multi-target-support)
7. [Examples](#examples)
8. [Benefits](#benefits)

---

## Executive Summary

This architecture combines three key patterns to create a maintainable, extensible FHIRPath→SQL translation system:

1. **Signature-Based Type Resolution** - Centralized operation registry with flexible type resolution strategies
2. **Visitor Pattern** - Target-agnostic IR with pluggable code generators
3. **Attributed Nodes** - Resolved signatures stored in IR nodes for efficient type queries

### Key Benefits

| Aspect | Improvement |
|--------|-------------|
| **Operation Classes** | 18+ → 1 (Generic `Operation` node) |
| **Type Redundancy** | 3 places → 1 place (signature only) |
| **Multi-Target** | Spark-locked → Visitor per target |
| **Adding Functions** | ~50 lines (new class) → ~5 lines (registry entry) |
| **Adding Targets** | Duplicate all classes → Implement visitor |

---

## Design Principles

### 1. Single Generic Operation Node
Replace operation-specific classes (Add, Abs, Subtract, etc.) with one `Operation` class identified by:
- Operation name (string)
- Resolved signature (parameter + result types)
- Argument list

### 2. Centralized Operation Registry
All function/operator signatures defined in `OperationRegistry`:
- Single source of truth for the type system
- Easy auditing against FHIRPath specification
- Minimal code to add new functions

### 3. Attributed Nodes with Resolved Signatures
- Store `ResolvedSignature` in each Operation node
- `getType()` returns `signature.resultType()` - zero cost
- No type recalculation, no redundancy

### 4. Visitor Pattern for Code Generation
- Remove target-specific `eval()` from IRNode interface
- Add generic `accept(IRNodeVisitor<T>)` method
- Target-specific visitors (Spark, SQL Server, PostgreSQL)
- IR tree remains completely target-agnostic

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────┐
│                  Layer 1: AST                           │
│  AstNode, AstFunctionCall, AstBinaryOperator          │
│  (Unchanged - represents FHIRPath syntax)              │
└────────────────────┬────────────────────────────────────┘
                     │ Analyzer.analyze()
                     ▼
┌─────────────────────────────────────────────────────────┐
│           Layer 2: Signature Resolution                 │
│                                                         │
│  ┌───────────────────────────────────────┐             │
│  │ OperationRegistry                     │             │
│  │ - Maps name → List<SignatureDefinition> │          │
│  │ - SignatureDefinition contains ResultSpec │        │
│  └───────────────────────────────────────┘             │
│                     │                                   │
│                     ▼                                   │
│  ┌───────────────────────────────────────┐             │
│  │ OverloadResolver                      │             │
│  │ - Selects best signature match        │             │
│  │ - Inserts type casts if needed        │             │
│  │ - Returns ResolvedCall                │             │
│  └───────────────────────────────────────┘             │
└────────────────────┬────────────────────────────────────┘
                     │ Creates attributed IR
                     ▼
┌─────────────────────────────────────────────────────────┐
│          Layer 3: Target-Agnostic IR                    │
│                                                         │
│  sealed interface IRNode permits                        │
│    Operation, Literal, Traversal, Cast, ...            │
│                                                         │
│  record Operation(                                      │
│      String name,                                       │
│      List<IRNode> args,                                 │
│      ResolvedSignature signature  ← STORED              │
│  )                                                      │
│                                                         │
│  Type getType() { return signature.resultType(); }      │
│  <T> T accept(IRNodeVisitor<T> visitor);                │
└────────────────────┬────────────────────────────────────┘
                     │ Visited by target generator
                     ▼
┌─────────────────────────────────────────────────────────┐
│      Layer 4: Target-Specific Code Generation           │
│                                                         │
│  interface IRNodeVisitor<T> {                           │
│      T visitOperation(Operation node);                  │
│      T visitLiteral(Literal node);                      │
│      ...                                                │
│  }                                                      │
│                                                         │
│  ┌────────────────┐  ┌─────────────────┐               │
│  │ SparkCodeGen   │  │ SqlServerCodeGen│               │
│  │ → Column       │  │ → String        │               │
│  └────────────────┘  └─────────────────┘               │
└─────────────────────────────────────────────────────────┘
```

### Data Flow Example: `age + 5`

```
1. Parse → AST
   AstBinaryOperator("+", AstVariable("age"), AstLiteral(5))

2. Analyze → Query Registry
   OperationRegistry.getSignatures("add")
   → [biOp(INTEGER), biOp(DECIMAL), biOp(QUANTITY), ...]

3. Resolve Arguments
   leftIR  = Traversal(resource, ageField) : INTEGER
   rightIR = Literal(5, INTEGER) : INTEGER

4. Resolve Overload
   OverloadResolver.resolveCall(signatures, [leftIR, rightIR])
   → ResolvedCall(signature: biOp(INTEGER), args: [leftIR, rightIR])

5. Create Attributed IR
   Operation(
       name: "add",
       args: [leftIR, rightIR],
       signature: ResolvedSignature([INTEGER, INTEGER], INTEGER)
   )

6. Query Type (zero cost)
   operation.getType() → signature.resultType() → INTEGER

7. Generate Spark Code
   operation.accept(new SparkCodeGenerator())
   → leftColumn.plus(rightColumn) : Column

8. Generate SQL Server Code
   operation.accept(new SqlServerCodeGenerator())
   → "(age + 5)" : String
```

---

## Type Resolution System

### Four Type Resolution Strategies

FHIRPath operations resolve result types in four distinct ways, captured by the `ResultSpec` sealed interface:

#### 1. Static - Type Known at Definition Time

Most common case. Result type is fixed regardless of arguments.

```java
// Example: abs(Integer) → Integer, substring(String, Integer) → String

sealed interface ResultSpec {
    record Static(Type type) implements ResultSpec {
        Type resolve(List<IRNode> resolvedArgs) {
            return type;  // Always return fixed type
        }
    }
}
```

#### 2. InputType - Same as First Argument

Operations that preserve the input collection structure.

```java
// Example: Collection<T>.where(...) → Collection<T>

record InputType() implements ResultSpec {
    Type resolve(List<IRNode> resolvedArgs) {
        return resolvedArgs.get(0).getType();  // Return input type
    }
}
```

#### 3. EffectiveInputType - Element Type of Input Collection

Operations that extract elements from collections.

```java
// Example: Collection<T>.first() → T

record EffectiveInputType() implements ResultSpec {
    Type resolve(List<IRNode> resolvedArgs) {
        return resolvedArgs.get(0).getType().effectiveType();
    }
}
```

#### 4. FhirSystemType - FHIR Type to System Type Mapping

Used exclusively by `getValue()` to extract primitive values from FHIR types.

```java
// Example: FhirType(STRING).getValue() → String

record FhirSystemType() implements ResultSpec {
    Type resolve(List<IRNode> resolvedArgs) {
        FhirType fhirType = (FhirType) resolvedArgs.get(0).getType();
        return fhirType.systemType();
    }
}
```

### Signature Architecture

Two signature types serve different purposes:

#### SignatureDefinition - For Registry

Used to define available overloads in `OperationRegistry`. Contains:
- Parameter types (List<Type>)
- ResultSpec (how to compute result type)
- Min arity (for variadic functions)

```java
record SignatureDefinition(
    List<Type> parameterTypes,
    ResultSpec resultSpec,
    int minArity
) { }

// Example: substring with optional length parameter
new SignatureDefinition(
    List.of(STRING, INTEGER, INTEGER),  // param types
    new ResultSpec.Static(STRING),       // result spec
    2                                    // min arity (length is optional)
)
```

#### ResolvedSignature - For Operation Nodes

Stored in `Operation` nodes after type resolution. Contains:
- Parameter types (List<Type>)
- **Concrete result type** (Type, not ResultSpec)
- Min arity

```java
record ResolvedSignature(
    List<Type> parameterTypes,
    Type resultType,  // ← Concrete type, resolved once
    int minArity
) {
    static ResolvedSignature resolve(
        SignatureDefinition definition,
        List<IRNode> resolvedArgs
    ) {
        Type concrete = definition.resultSpec().resolve(resolvedArgs);
        return new ResolvedSignature(
            definition.parameterTypes(),
            concrete,
            definition.minArity()
        );
    }
}
```

### Factory Methods for Common Patterns

The `Signatures` helper class provides factories for ~90% of signature patterns:

```java
// Unary operations: input type = output type
// abs(Integer) → Integer, abs(Decimal) → Decimal
Signatures.unaryOp(INTEGER, DECIMAL, QUANTITY)

// Binary operations: both operands and result same type
// +(Integer, Integer) → Integer
Signatures.binaryOp(INTEGER, DECIMAL, STRING)

// Comparison operations: any type → Boolean
// >(Integer, Integer) → Boolean
Signatures.comparisonOp(INTEGER, DECIMAL, STRING, DATE, DATE_TIME)

// Collection element extraction: Collection<T> → T
// first(), last(), single()
Signatures.elementExtractor()

// Collection preservation: Collection<T> → Collection<T>
// where(criteria), select(projection)
Signatures.collectionPreserver(minArity)

// Collection aggregation: Collection<T> → R
// count() → Integer, empty() → Boolean
Signatures.collectionAggregator(INTEGER)

// Variadic operations with minimum arity
// substring(String, Integer, Integer) with minArity=2
Signatures.variadic(List.of(STRING, INTEGER, INTEGER), STRING, 2)
```

---

## Core Components

### IRNode Interface

```java
/**
 * Base interface for all IR nodes.
 * Sealed to enable exhaustive pattern matching.
 * Target-agnostic - no Spark/SQL-specific code.
 */
public sealed interface IRNode
    permits Operation, Literal, Traversal, Cast, Resource { ... } {

    /**
     * Returns FHIRPath type of this expression.
     * For operations, delegates to signature.resultType().
     */
    Type getType();

    /**
     * Visitor pattern for target-specific code generation.
     * @param <T> Return type (Column for Spark, String for SQL)
     */
    <T> T accept(IRNodeVisitor<T> visitor);
}
```

**Key Changes:**
- ❌ Removed: `Column eval()` (was Spark-specific)
- ✅ Added: `<T> T accept(IRNodeVisitor<T>)` (generic)
- ✅ Added: `sealed interface` (exhaustive matching)

### Operation Node

```java
/**
 * Generic IR node for all FHIRPath functions and operators.
 * Replaces 18+ specific classes (Add, Abs, Subtract, etc.)
 */
public record Operation(
    String name,
    List<IRNode> args,
    ResolvedSignature signature  // ← Stores resolved type info
) implements IRNode {

    @Override
    public Type getType() {
        return signature.resultType();  // Zero-cost delegation
    }

    @Override
    public <T> T accept(IRNodeVisitor<T> visitor) {
        return visitor.visitOperation(this);
    }

    public int arity() {
        return args.size();
    }
}
```

**Examples:**
```java
// Integer addition
Operation("add", [Literal(5), Literal(3)],
    ResolvedSignature([INTEGER, INTEGER], INTEGER))

// DateTime arithmetic
Operation("add", [birthDateTraversal, quantityLiteral],
    ResolvedSignature([DATE_TIME, QUANTITY], DATE_TIME))

// String concatenation
Operation("add", [Literal("Hello"), Literal("World")],
    ResolvedSignature([STRING, STRING], STRING))
```

### Operation Registry

```java
/**
 * Central registry of all FHIRPath function/operator signatures.
 * Single source of truth for type system.
 */
public final class OperationRegistry {

    private static final Map<String, List<SignatureDefinition>> REGISTRY = ...;

    static {
        // Arithmetic operators (FHIRPath Spec 6.2)
        register("add", Signatures.binaryOp(INTEGER, DECIMAL, QUANTITY, STRING));
        register("add", Signatures.binaryOpLeft(DATE_TIME, QUANTITY));

        // Math functions (FHIRPath Spec 6.4)
        register("abs", Signatures.unaryOp(INTEGER, DECIMAL, QUANTITY));
        register("sqrt", Signatures.unaryOp(DECIMAL));

        // String functions (FHIRPath Spec 6.5)
        register("substring", Signatures.variadic(
            List.of(STRING, INTEGER, INTEGER), STRING, 2));
        register("length", Signatures.unaryOp(STRING, INTEGER));

        // Comparison operators (FHIRPath Spec 6.3)
        register(">", Signatures.comparisonOp(INTEGER, DECIMAL, STRING, DATE));

        // Collection operations (FHIRPath Spec 6.6-6.7)
        register("first", Signatures.elementExtractor());
        register("where", Signatures.collectionPreserver(1));
        register("count", Signatures.collectionAggregator(INTEGER));
    }

    public static List<SignatureDefinition> getSignatures(String name) {
        return REGISTRY.getOrDefault(name, List.of());
    }
}
```

**Adding a new function:**
```java
// Just one line in registry!
register("pow", List.of(
    new SignatureDefinition(List.of(INTEGER, INTEGER), DECIMAL),
    new SignatureDefinition(List.of(DECIMAL, DECIMAL), DECIMAL)
));
```

### IRNodeVisitor Interface

```java
/**
 * Visitor for traversing/transforming IR trees.
 * Different implementations enable different targets.
 */
public interface IRNodeVisitor<T> {
    T visitOperation(Operation node);
    T visitLiteral(Literal node);
    T visitTraversal(Traversal node);
    T visitCast(Cast node);
    T visitResource(Resource node);
    // ... other infrastructure nodes
}
```

**Implementations:**
- `SparkCodeGenerator` → `Column`
- `SqlServerCodeGenerator` → `String`
- `PostgreSqlCodeGenerator` → `String`
- `DebugPrinter` → `String`
- `OptimizationVisitor` → `IRNode` (IR-to-IR transformation)

### Spark Code Generator

```java
/**
 * Generates Spark Column expressions from IR trees.
 */
public class SparkCodeGenerator implements IRNodeVisitor<Column> {

    @Override
    public Column visitOperation(Operation op) {
        // Recursively generate columns for arguments
        List<Column> argColumns = op.args().stream()
            .map(arg -> arg.accept(this))
            .toList();

        // Dispatch based on operation name
        return evaluateOperation(op.name(), argColumns, op.getType());
    }

    private Column evaluateOperation(String name, List<Column> args, Type resultType) {
        return switch (name) {
            case "add" -> evaluateAdd(args, resultType);
            case "abs" -> evaluateAbs(args, resultType);
            case "substring" -> evaluateSubstring(args);
            // ... other operations
            default -> throw new UnsupportedOperationException(
                "Unknown operation: " + name);
        };
    }

    private Column evaluateAdd(List<Column> args, Type resultType) {
        return switch ((PrimitiveType) resultType) {
            case INTEGER, DECIMAL -> args.get(0).plus(args.get(1));
            case STRING -> concat(args.get(0), args.get(1));
            case DATE_TIME -> dateTime(args.get(0)).plus(quantity(args.get(1)));
            case QUANTITY -> quantity(args.get(0)).plus(quantity(args.get(1)));
            default -> throw new IllegalArgumentException(
                "Unsupported type for add: " + resultType);
        };
    }

    // Other visit methods for infrastructure nodes
    @Override
    public Column visitLiteral(Literal lit) {
        return lit(lit.value()).cast(toSparkType(lit.type()));
    }

    @Override
    public Column visitTraversal(Traversal trav) {
        return trav.target().accept(this).getField(trav.fieldSpec().name());
    }
}
```

---

## Multi-Target Support

### Adding a New Target: SQL Server

Implementing a new code generator requires only creating a visitor class - no changes to IR or existing code:

```java
/**
 * Generates SQL Server T-SQL expressions from IR trees.
 */
public class SqlServerCodeGenerator implements IRNodeVisitor<String> {

    @Override
    public String visitOperation(Operation op) {
        // Recursively generate SQL for arguments
        List<String> argExprs = op.args().stream()
            .map(arg -> arg.accept(this))
            .toList();

        return evaluateOperation(op.name(), argExprs, op.getType());
    }

    private String evaluateOperation(String name, List<String> args, Type resultType) {
        return switch (name) {
            // Arithmetic
            case "add" -> evaluateAdd(args, resultType);
            case "multiply" -> "(" + args.get(0) + " * " + args.get(1) + ")";

            // Math functions
            case "abs" -> "ABS(" + args.get(0) + ")";
            case "sqrt" -> "SQRT(" + args.get(0) + ")";

            // String functions
            case "substring" -> evaluateSubstring(args);
            case "upper" -> "UPPER(" + args.get(0) + ")";
            case "length" -> "LEN(" + args.get(0) + ")";

            // Comparison
            case "equals" -> "(" + args.get(0) + " = " + args.get(1) + ")";
            case ">" -> "(" + args.get(0) + " > " + args.get(1) + ")";

            default -> throw new UnsupportedOperationException(
                "Unknown operation: " + name);
        };
    }

    private String evaluateAdd(List<String> args, Type resultType) {
        return switch ((PrimitiveType) resultType) {
            case INTEGER, DECIMAL -> "(" + args.get(0) + " + " + args.get(1) + ")";
            case STRING -> "CONCAT(" + args.get(0) + ", " + args.get(1) + ")";
            default -> throw new IllegalArgumentException(
                "Unsupported type: " + resultType);
        };
    }

    private String evaluateSubstring(List<String> args) {
        String str = args.get(0);
        String start = args.get(1);
        String length = args.size() > 2 ? args.get(2) : "LEN(" + str + ")";

        // SQL Server uses 1-based indexing, FHIRPath uses 0-based
        return "SUBSTRING(" + str + ", " + start + " + 1, " + length + ")";
    }

    @Override
    public String visitLiteral(Literal lit) {
        if (lit.value() == null) return "NULL";
        if (lit.value() instanceof String s) return "'" + s + "'";
        if (lit.value() instanceof Boolean b) return b ? "1" : "0";
        return lit.value().toString();
    }

    @Override
    public String visitTraversal(Traversal trav) {
        return trav.target().accept(this) + "." + trav.fieldSpec().name();
    }
}
```

**Usage:**
```java
// Same IR tree, different targets
IRNode ir = analyzer.analyze(astNode);

// Generate Spark code
Column sparkCol = ir.accept(new SparkCodeGenerator());

// Generate SQL Server code
String sqlServerExpr = ir.accept(new SqlServerCodeGenerator());

// Generate PostgreSQL code
String postgresExpr = ir.accept(new PostgreSqlCodeGenerator());
```

**Effort:** 1-2 weeks per target
**Risk:** Low (zero impact on existing code)

### Target Comparison

| Feature | Spark | SQL Server | PostgreSQL |
|---------|-------|------------|------------|
| **Return Type** | `Column` | `String` | `String` |
| **Addition** | `left.plus(right)` | `"(a + b)"` | `"(a + b)"` |
| **String Concat** | `concat(a, b)` | `"CONCAT(a, b)"` | `"(a \|\| b)"` |
| **Substring** | `substr(s, p+1, l)` | `"SUBSTRING(s, p+1, l)"` | `"SUBSTRING(s FROM p+1 FOR l)"` |
| **Abs** | `abs(x)` | `"ABS(x)"` | `"ABS(x)"` |
| **Effort** | Already done | ~1 week | ~1 week |

---

## Examples

### Example 1: Simple Arithmetic

**FHIRPath:** `5 + 3`

**Resolution:**
```java
// 1. Create IR nodes for literals
Literal left = new Literal(5, INTEGER);
Literal right = new Literal(3, INTEGER);

// 2. Query registry for "add" signatures
List<SignatureDefinition> sigs = OperationRegistry.getSignatures("add");
// Returns: [biOp(INTEGER), biOp(DECIMAL), biOp(STRING), ...]

// 3. Resolve overload (exact match, cost = 0)
ResolvedCall call = OverloadResolver.resolveCall(sigs, List.of(left, right));
// Selects: SignatureDef([INTEGER, INTEGER], Static(INTEGER))

// 4. Create Operation with resolved signature
Operation op = new Operation("add", List.of(left, right),
    ResolvedSignature.resolve(call.signature(), List.of(left, right)));
// signature.resultType() = INTEGER
```

**Spark Generation:**
```java
Column result = op.accept(new SparkCodeGenerator());
// → lit(5).plus(lit(3)) : Column
```

**SQL Server Generation:**
```java
String result = op.accept(new SqlServerCodeGenerator());
// → "(5 + 3)" : String
```

### Example 2: Type Coercion

**FHIRPath:** `5.5 + 3`

**Resolution:**
```java
// 1. Create literals with different types
Literal left = new Literal(5.5, DECIMAL);
Literal right = new Literal(3, INTEGER);

// 2. Resolve overload (needs adaptation)
ResolvedCall call = OverloadResolver.resolveCall(
    OperationRegistry.getSignatures("add"),
    List.of(left, right)
);
// Selects: SignatureDef([DECIMAL, DECIMAL], Static(DECIMAL))
// Adapts: right cast to DECIMAL

// 3. Create Operation with cast inserted
Operation op = new Operation("add",
    List.of(left, new Cast(right, DECIMAL)),  // ← Cast inserted
    ResolvedSignature.resolve(...));
// signature.resultType() = DECIMAL
```

### Example 3: Dynamic Type Resolution

**FHIRPath:** `Patient.name.first()`

**Resolution:**
```java
// 1. Traversal has type Collection<HumanName>
Traversal nameTraversal = new Traversal(
    resourceNode,
    FieldSpec("name", new CollectionType(HumanNameType))
);

// 2. Query "first" signature
List<SignatureDef> sigs = OperationRegistry.getSignatures("first");
// Returns: [elementExtractor()]  ← Uses EffectiveInputType

// 3. Resolve - ResultSpec computes result type
SignatureDef def = sigs.get(0);
List<IRNode> args = List.of(nameTraversal);

Type resultType = def.resultSpec().resolve(args);
// → args.get(0).getType().effectiveType()
// → Collection<HumanName>.effectiveType()
// → HumanName

// 4. Create Operation
Operation op = new Operation("first", args,
    new ResolvedSignature(
        List.of(new CollectionType(HumanNameType)),
        HumanNameType,  // ← Resolved from collection
        1
    ));
```

### Example 4: Complex Expression

**FHIRPath:** `Patient.birthDate + 1 year`

**IR Tree:**
```java
Operation("add",
    List.of(
        Traversal(
            Resource(PatientType),
            FieldSpec("birthDate", DATE_TIME)
        ),  // Type: DATE_TIME
        Literal(Quantity.of(1, "year"), QUANTITY)  // Type: QUANTITY
    ),
    ResolvedSignature(
        List.of(DATE_TIME, QUANTITY),
        DATE_TIME  // ← Result type from signature
    )
)
```

**Spark Generation:**
```java
visitOperation(add)
→ evaluateAdd([birthDateColumn, quantityColumn], DATE_TIME)
→ dateTime(birthDateColumn).plus(quantity(quantityColumn))
→ Column
```

---

## Benefits

### Maintainability

| Benefit | Current Design | New Design |
|---------|----------------|------------|
| **Add new function** | Create class (~50 lines) + Register | Registry entry (~5 lines) |
| **Find function logic** | Search across 18+ classes | Look in OperationRegistry + SparkCodeGenerator |
| **Type definitions** | 3 places (signature, getType(), eval()) | 1 place (signature) |
| **Multi-target** | Duplicate all eval() methods | Add new visitor class |

### Extensibility

**Adding pow() function:**

Current design:
```java
// 1. Create src/.../math/Pow.java (30 lines)
public record Pow(IRNode base, IRNode exp, Signature sig) implements IRNode {
    public static final List<Signature> SIGNATURES = ...;
    public Type getType() { return sig.resultType(); }
    public Column eval() { return functions.pow(base.eval(), exp.eval()); }
}

// 2. Register in FunctionRegistry
Map.entry("pow", forClass(Pow.class))
```

New design:
```java
// 1. Registry (3 lines)
register("pow", List.of(
    new SignatureDef(List.of(INTEGER, INTEGER), DECIMAL),
    new SignatureDef(List.of(DECIMAL, DECIMAL), DECIMAL)
));

// 2. Spark generator (1 line)
case "pow" -> functions.pow(args.get(0), args.get(1));

// 3. SQL Server generator (1 line) - optional
case "pow" -> "POWER(" + args.get(0) + ", " + args.get(1) + ")";
```

**Effort reduction:** 75%

### Type Safety

- Sealed `ResultSpec` interface → exhaustive pattern matching
- All Operation nodes have concrete types after construction
- Visitors can rely on static type information
- Compiler enforces correct signature handling

### Performance

- **Zero-cost type queries:** `getType()` is simple field access
- **No recalculation:** Type resolved once during IR construction
- **Visitor delegation:** Typically inlined by JVM (zero overhead)
- **Same Spark execution:** Generated Column expressions identical

### Code Quality

**Metrics:**
- Class count: 18 → 1 (-94%)
- Type redundancy: 3 places → 1 place (-67%)
- Lines of code: ~20% reduction
- Cyclomatic complexity: Similar or lower

**Maintainability:**
- Centralized operation registry
- Clear separation of concerns
- Easy to audit against FHIRPath spec
- Excellent IDE navigation

---

**Document Status:** Implementation Reference
**Last Updated:** 2025-10-13
**Questions:** Consult architect or spark-expert agents
