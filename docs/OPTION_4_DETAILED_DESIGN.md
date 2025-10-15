Sr# Option 4: Hybrid Registry + Visitor Pattern - Detailed Design

**Date:** 2025-10-13
**Status:** Proposed Design for Discussion
**Alignment:** Consistent with recommendations from ANALYSIS_01.md

---

## Executive Summary

This document details **Option 4: Hybrid Registry + Visitor Pattern**, which combines the best aspects of:
- **Signature-driven evaluation** (from ANALYSIS_01.md Option 2)
- **Visitor pattern for multi-target support** (from ANALYSIS_01.md Option 3)
- **Attributed nodes** (storing resolved signature)

This approach addresses all identified architectural issues while providing a clear, incremental migration path.

---

## Table of Contents

1. [Design Overview](#1-design-overview)
2. [Architecture](#2-architecture)
3. [Core Components](#3-core-components)
4. [Detailed Examples](#4-detailed-examples)
5. [Migration Path](#5-migration-path)
6. [Multi-Target Support](#6-multi-target-support)
7. [Comparison with Current Design](#7-comparison-with-current-design)
8. [Risk Assessment](#8-risk-assessment)
9. [Discussion Points](#9-discussion-points)

---

## 1. Design Overview

### 1.1 Core Principles

1. **Single Generic Operation Node**
   - Replace 12+ operation classes (Add, Abs, etc.) with one `Operation` class
   - Operation identified by name string + resolved signature

2. **Centralized Operation Registry**
   - All function/operator signatures defined in `OperationRegistry`
   - Single source of truth for type system
   - Easy to add new functions

3. **Attributed Nodes**
   - Store resolved `FunctionSignature` in each Operation node
   - `getType()` returns `signature.resultType()` - no recalculation
   - Eliminates type redundancy

4. **Visitor Pattern for Code Generation**
   - Remove `eval()` from IRNode interface
   - Add `accept(IRNodeVisitor<T>)` method
   - Target-specific visitors (SparkCodeGenerator, SqlServerCodeGenerator)
   - IR tree is target-agnostic

### 1.2 Key Benefits

| Issue | Current Design | Option 4 Design |
|-------|----------------|-----------------|
| **Type Redundancy** | Defined in signatures + getType() + eval() | Defined once in signature |
| **Class Count** | 18 classes (growing) | 7 classes (stable) |
| **Multi-Target** | Locked to Spark | Visitor per target |
| **Signature Metadata** | Discarded after resolution | Stored in node |
| **Maintainability** | Scattered across classes | Centralized registry + visitors |

### 1.3 Alignment with ANALYSIS_01.md

From ANALYSIS_01.md:407-450, the primary recommendation was:

> **Primary Recommendation: Option 2 + Option 3 Hybrid**
>
> Combine signature-driven evaluation with visitor pattern

This document expands that recommendation into a complete, implementable design with detailed migration steps.

---

## 2. Architecture

### 2.1 High-Level Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                     Layer 1: AST                            │
│  AstNode, AstFunctionCall, AstBinaryOperator               │
│  (Unchanged - represents FHIRPath syntax)                   │
└────────────────────┬────────────────────────────────────────┘
                     │ Analyzer.analyze()
                     ▼
┌─────────────────────────────────────────────────────────────┐
│              Layer 2: Signature Resolution                  │
│                                                             │
│  ┌─────────────────────────────────────────────┐           │
│  │ OperationRegistry                           │           │
│  │ - getSignatures(name) → List<Signature>     │           │
│  │ - All function/operator definitions         │           │
│  └─────────────────────────────────────────────┘           │
│                       │                                     │
│                       ▼                                     │
│  ┌─────────────────────────────────────────────┐           │
│  │ OverloadResolver                            │           │
│  │ - resolveCall(signatures, args)             │           │
│  │ - Returns: ResolvedCall(signature, args)    │           │
│  └─────────────────────────────────────────────┘           │
└────────────────────┬────────────────────────────────────────┘
                     │ Creates IR with stored signature
                     ▼
┌─────────────────────────────────────────────────────────────┐
│              Layer 3: Target-Agnostic IR                    │
│                                                             │
│  sealed interface IRNode permits                            │
│    Operation, Literal, Traversal, Cast, Resource            │
│                                                             │
│  record Operation(                                          │
│      String name,                                           │
│      List<IRNode> args,                                     │
│      FunctionSignature signature  ← STORED                  │
│  )                                                          │
│                                                             │
│  Type getType() { return signature.resultType(); }          │
│  <T> T accept(IRNodeVisitor<T> visitor);                    │
└────────────────────┬────────────────────────────────────────┘
                     │ Visited by target-specific generator
                     ▼
┌─────────────────────────────────────────────────────────────┐
│           Layer 4: Target-Specific Code Generation          │
│                                                             │
│  interface IRNodeVisitor<T> {                               │
│      T visitOperation(Operation node);                      │
│      T visitLiteral(Literal node);                          │
│      T visitTraversal(Traversal node);                      │
│      T visitCast(Cast node);                                │
│      T visitResource(Resource node);                        │
│  }                                                          │
│                                                             │
│  ┌──────────────────────┐  ┌──────────────────────┐        │
│  │ SparkCodeGenerator   │  │ SqlServerCodeGen     │        │
│  │ implements           │  │ implements           │        │
│  │ IRNodeVisitor<Column>│  │ IRNodeVisitor<String>│        │
│  └──────────────────────┘  └──────────────────────┘        │
└─────────────────────────────────────────────────────────────┘
```

### 2.2 Data Flow Example

**FHIRPath Expression:** `age + 5`

```
1. Parse → AST
   AstBinaryOperator("+", AstVariable("age"), AstLiteral(5))

2. Analyze → Query Registry
   OperationRegistry.getSignatures("add")
   → [
       biOperator(INTEGER),
       biOperator(DECIMAL),
       biOperator(QUANTITY),
       biOperatorLeft(DATE_TIME, QUANTITY),
       biOperator(STRING)
     ]

3. Resolve Arguments
   leftIR = Traversal(resource, ageField) : INTEGER
   rightIR = Literal(5, INTEGER) : INTEGER

4. Resolve Overload
   OverloadResolver.resolveCall(signatures, [leftIR, rightIR])
   → ResolvedCall(
       signature: biOperator(INTEGER),  ← Selected signature
       args: [leftIR, rightIR]          ← No casts needed
     )

5. Create IR with Stored Signature
   Operation(
       name: "add",
       args: [leftIR, rightIR],
       signature: biOperator(INTEGER)   ← STORED IN NODE
   )

6. Query Type (no recalculation)
   operation.getType()
   → operation.signature.resultType()
   → INTEGER

7. Generate Code (Spark)
   operation.accept(new SparkCodeGenerator())
   → evaluateAdd([leftColumn, rightColumn], INTEGER)
   → leftColumn.plus(rightColumn)
   → Column

8. Generate Code (SQL Server - future)
   operation.accept(new SqlServerCodeGenerator())
   → evaluateAdd([leftExpr, rightExpr], INTEGER)
   → "(" + leftExpr + " + " + rightExpr + ")"
   → String
```

---

## 3. Core Components

### 3.1 IRNode Interface (Modified)

**File:** `src/main/java/com/example/fhirpath/ir/IRNode.java`

```java
package com.example.fhirpath.ir;

import com.example.fhirpath.typing.Type;

/**
 * Base interface for all IR nodes in the FHIRPath expression tree.
 * IRNodes are target-agnostic and represent typed FHIRPath expressions.
 *
 * Code generation is delegated to target-specific visitors via the accept() method.
 */
public sealed interface IRNode
        permits Operation, Literal, Traversal, Cast, Resource, CastToSystem,
        Count, Exists, Union {

    /**
     * Returns the FHIRPath type of this expression.
     * For operations, this is the result type from the resolved signature.
     */
    Type getType();

    /**
     * Accepts a visitor for target-specific code generation.
     *
     * @param visitor The visitor to accept
     * @param <T> The return type of the visitor (e.g., Column for Spark, String for SQL)
     * @return The result of visiting this node
     */
    <T> T accept(IRNodeVisitor<T> visitor);

    /**
     * Returns whether this expression is singular (not a collection).
     * Default implementation delegates to type.
     */
    default boolean isSingular() {
        return !getType().isCollection();
    }
}
```

**Changes from Current:**
- ❌ Removed: `Column eval()` - no longer Spark-specific
- ✅ Added: `<T> T accept(IRNodeVisitor<T>)` - visitor pattern
- ✅ Added: `sealed interface` - exhaustive pattern matching

### 3.2 Operation Node (New)

**File:** `src/main/java/com/example/fhirpath/ir/Operation.java`

```java
package com.example.fhirpath.ir;

import com.example.fhirpath.analyzer.FunctionSignature;
import com.example.fhirpath.typing.Type;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * Generic IR node representing any FHIRPath function or operator.
 * Replaces specific operation classes (Add, Abs, etc.).
 *
 * The resolved signature is stored in the node, providing:
 * - Result type (via signature.resultType())
 * - Parameter types (for validation)
 * - Which overload was selected (for debugging/optimization)
 *
 * Examples:
 * - Operation("add", [leftIR, rightIR], biOperator(INTEGER))
 * - Operation("abs", [targetIR], unaryOp(INTEGER, INTEGER))
 * - Operation("substring", [strIR, posIR, lenIR], substringSignature)
 */
public record Operation(
    @Nonnull String name,
    @Nonnull List<IRNode> args,
    @Nonnull FunctionSignature signature
) implements IRNode {

    /**
     * Returns the result type from the resolved signature.
     * No recalculation needed - single source of truth.
     */
    @Override
    @Nonnull
    public Type getType() {
        return signature.resultType();
    }

    /**
     * Accepts a visitor for target-specific code generation.
     */
    @Override
    @Nonnull
    public <T> T accept(@Nonnull IRNodeVisitor<T> visitor) {
        return visitor.visitOperation(this);
    }

    /**
     * Convenience method to get argument count.
     */
    public int arity() {
        return args.size();
    }

    @Override
    public String toString() {
        return name + "(" + args + ") : " + getType();
    }
}
```

**Key Features:**
- ✅ Generic: Works for all functions/operators
- ✅ Attributed: Stores resolved signature
- ✅ Type-safe: Record with non-null annotations
- ✅ Debuggable: toString() shows name, args, and type

### 3.3 OperationRegistry (New)

**File:** `src/main/java/com/example/fhirpath/ir/OperationRegistry.java`

```java
package com.example.fhirpath.ir;

import com.example.fhirpath.analyzer.FunctionSignature;
import com.example.fhirpath.typing.Type;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static com.example.fhirpath.analyzer.FunctionSignature.*;
import static com.example.fhirpath.typing.Type.*;

/**
 * Centralized registry of all FHIRPath functions and operators.
 *
 * Each entry maps a function/operator name to its list of supported signatures.
 * The OverloadResolver uses these signatures to find the best match for given arguments.
 *
 * Adding a new FHIRPath function requires only adding an entry here -
 * no new classes needed.
 */
public final class OperationRegistry {

    private static final Map<String, List<FunctionSignature>> OPERATIONS = new HashMap<>();

    static {
        // Arithmetic operators
        // FHIRPath Spec: 6.2.1 - 6.2.4
        registerBinaryOp("add", INTEGER, DECIMAL, QUANTITY, STRING);
        OPERATIONS.put("add", appendSignature(
            OPERATIONS.get("add"),
            biOperatorLeft(DATE_TIME, QUANTITY) // DateTime + Quantity → DateTime
        ));

        registerBinaryOp("sub", INTEGER, DECIMAL, QUANTITY);
        OPERATIONS.put("sub", appendSignature(
            OPERATIONS.get("sub"),
            biOperatorLeft(DATE_TIME, QUANTITY) // DateTime - Quantity → DateTime
        ));

        registerBinaryOp("multiply", INTEGER, DECIMAL, QUANTITY);
        registerBinaryOp("divide", INTEGER, DECIMAL, QUANTITY);
        registerBinaryOp("mod", INTEGER, DECIMAL);

        // Comparison operators
        // FHIRPath Spec: 6.3.1 - 6.3.6
        registerComparison("equals", INTEGER, DECIMAL, STRING, BOOLEAN,
                          QUANTITY, DATE, DATE_TIME, TIME);
        registerComparison("notEquals", INTEGER, DECIMAL, STRING, BOOLEAN,
                          QUANTITY, DATE, DATE_TIME, TIME);
        registerComparison("gt", INTEGER, DECIMAL, STRING, QUANTITY,
                          DATE, DATE_TIME, TIME);
        registerComparison("lt", INTEGER, DECIMAL, STRING, QUANTITY,
                          DATE, DATE_TIME, TIME);
        registerComparison("geq", INTEGER, DECIMAL, STRING, QUANTITY,
                          DATE, DATE_TIME, TIME);
        registerComparison("leq", INTEGER, DECIMAL, STRING, QUANTITY,
                          DATE, DATE_TIME, TIME);

        // Math functions
        // FHIRPath Spec: 6.4.1 - 6.4.5
        registerUnaryOp("abs", INTEGER, DECIMAL, QUANTITY);
        registerUnaryOp("ceiling", INTEGER, DECIMAL);
        registerUnaryOp("floor", INTEGER, DECIMAL);
        registerUnaryOp("truncate", INTEGER, DECIMAL);
        registerUnaryOp("exp", INTEGER, DECIMAL);
        registerUnaryOp("ln", INTEGER, DECIMAL);
        registerUnaryOp("log", INTEGER, DECIMAL);
        registerUnaryOp("sqrt", INTEGER, DECIMAL);

        // String functions
        // FHIRPath Spec: 6.5.1 - 6.5.10
        register("substring", List.of(
            // substring(string, start) and substring(string, start, length)
            new FunctionSignature(List.of(STRING, INTEGER, INTEGER), STRING, 2)
        ));

        register("startsWith", List.of(
            new FunctionSignature(List.of(STRING, STRING), BOOLEAN)
        ));

        register("endsWith", List.of(
            new FunctionSignature(List.of(STRING, STRING), BOOLEAN)
        ));

        register("contains", List.of(
            new FunctionSignature(List.of(STRING, STRING), BOOLEAN)
        ));

        register("upper", List.of(
            new FunctionSignature(List.of(STRING), STRING)
        ));

        register("lower", List.of(
            new FunctionSignature(List.of(STRING), STRING)
        ));

        register("replace", List.of(
            new FunctionSignature(List.of(STRING, STRING, STRING), STRING)
        ));

        register("matches", List.of(
            new FunctionSignature(List.of(STRING, STRING), BOOLEAN)
        ));

        register("length", List.of(
            new FunctionSignature(List.of(STRING), INTEGER)
        ));

        // Collection operators
        // FHIRPath Spec: 6.6.1 - 6.6.10
        register("union", Stream.of(
            INTEGER, DECIMAL, STRING, BOOLEAN, QUANTITY, DATE, DATE_TIME, TIME
        ).map(t -> biOperator(t)).toList());

        // Aggregate functions (work on any collection type)
        // FHIRPath Spec: 6.7.1 - 6.7.3
        // These don't have specific signatures - they work on any type

        // Boolean operators
        // FHIRPath Spec: 6.8.1 - 6.8.4
        register("and", List.of(biOperator(BOOLEAN)));
        register("or", List.of(biOperator(BOOLEAN)));
        register("xor", List.of(biOperator(BOOLEAN)));
        register("implies", List.of(biOperator(BOOLEAN)));
        register("not", List.of(new FunctionSignature(List.of(BOOLEAN), BOOLEAN)));
    }

    private OperationRegistry() {
        // Utility class - no instantiation
    }

    /**
     * Returns all signatures for the given function/operator name.
     * Returns empty list if operation is not registered.
     */
    @Nonnull
    public static List<FunctionSignature> getSignatures(@Nonnull String name) {
        return OPERATIONS.getOrDefault(name, List.of());
    }

    /**
     * Checks if an operation is registered.
     */
    public static boolean isRegistered(@Nonnull String name) {
        return OPERATIONS.containsKey(name);
    }

    /**
     * Helper: Register a binary operation with same input/output type.
     */
    private static void registerBinaryOp(String name, Type... types) {
        register(name, Stream.of(types)
            .map(FunctionSignature::biOperator)
            .toList());
    }

    /**
     * Helper: Register a unary operation with same input/output type.
     */
    private static void registerUnaryOp(String name, Type... types) {
        register(name, Stream.of(types)
            .map(t -> new FunctionSignature(List.of(t), t))
            .toList());
    }

    /**
     * Helper: Register comparison operations (input types → BOOLEAN).
     */
    private static void registerComparison(String name, Type... types) {
        register(name, Stream.of(types)
            .map(t -> biOperator(t, BOOLEAN))
            .toList());
    }

    /**
     * Core registration method.
     */
    private static void register(String name, List<FunctionSignature> signatures) {
        if (OPERATIONS.containsKey(name)) {
            throw new IllegalStateException("Operation already registered: " + name);
        }
        OPERATIONS.put(name, signatures);
    }

    /**
     * Helper: Append a signature to existing list (for special cases).
     */
    private static List<FunctionSignature> appendSignature(
            List<FunctionSignature> existing,
            FunctionSignature additional) {
        return Stream.concat(existing.stream(), Stream.of(additional)).toList();
    }
}
```

**Key Features:**
- ✅ Centralized: All function definitions in one place
- ✅ Documented: Comments link to FHIRPath spec sections
- ✅ Extensible: Easy to add new functions
- ✅ Type-safe: Uses Type constants
- ✅ Auditable: Can verify against FHIRPath spec

### 3.4 IRNodeVisitor Interface (New)

**File:** `src/main/java/com/example/fhirpath/ir/IRNodeVisitor.java`

```java
package com.example.fhirpath.ir;

import javax.annotation.Nonnull;

/**
 * Visitor interface for traversing and transforming IR trees.
 *
 * Different visitor implementations enable different target backends:
 * - SparkCodeGenerator: Generates Spark Column expressions
 * - SqlServerCodeGenerator: Generates SQL Server T-SQL strings
 * - PostgreSqlCodeGenerator: Generates PostgreSQL SQL strings
 * - DebugVisitor: Generates human-readable string representation
 * - ValidationVisitor: Validates IR tree correctness
 *
 * @param <T> The return type of visit methods (e.g., Column, String, etc.)
 */
public interface IRNodeVisitor<T> {

    /**
     * Visit a generic operation node (function call or operator).
     */
    @Nonnull
    T visitOperation(@Nonnull Operation node);

    /**
     * Visit a literal constant value.
     */
    @Nonnull
    T visitLiteral(@Nonnull Literal node);

    /**
     * Visit a field traversal (e.g., Patient.name).
     */
    @Nonnull
    T visitTraversal(@Nonnull Traversal node);

    /**
     * Visit a type cast operation.
     */
    @Nonnull
    T visitCast(@Nonnull Cast node);

    /**
     * Visit a resource root reference.
     */
    @Nonnull
    T visitResource(@Nonnull Resource node);

    /**
     * Visit a getValue() operation (extract value from FHIR type).
     */
    @Nonnull
    T visitGetValue(@Nonnull CastToSystem node);

    /**
     * Visit a count() aggregate function.
     */
    @Nonnull
    T visitCount(@Nonnull Count node);

    /**
     * Visit an exists() predicate function.
     */
    @Nonnull
    T visitExists(@Nonnull Exists node);

    /**
     * Visit a union operation (collection union).
     */
    @Nonnull
    T visitUnion(@Nonnull Union node);
}
```

**Note:** Some infrastructure nodes (Count, Exists, Union, GetValue) might eventually be consolidated into Operation as well, but are kept separate initially for backward compatibility during migration.

### 3.5 SparkCodeGenerator (New)

**File:** `src/main/java/com/example/fhirpath/codegen/SparkCodeGenerator.java`

```java
package com.example.fhirpath.codegen;

import com.example.fhirpath.ir.*;
import com.example.fhirpath.typing.PrimitiveType;
import com.example.fhirpath.codegen.spark.SparkTypeMapper;
import com.example.fhirpath.typing.Type;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.functions;
import org.apache.spark.sql.types.DataType;

import javax.annotation.Nonnull;
import java.util.List;

import static com.example.fhirpath.codegen.spark.EvalHelper.*;
import static com.example.fhirpath.codegen.spark.DateTime.dateTime;
import static com.example.fhirpath.codegen.spark.Quantity.quantity;
import static com.example.fhirpath.codegen.spark.Date.date;
import static com.example.fhirpath.codegen.spark.Time.time;
import static org.apache.spark.sql.functions.*;

/**
 * Generates Spark Column expressions from FHIRPath IR trees.
 *
 * This visitor implements target-specific code generation for Apache Spark SQL.
 * Each visit method transforms an IR node into a Spark Column that can be
 * executed by the Spark SQL engine.
 */
public class SparkCodeGenerator implements IRNodeVisitor<Column> {

    @Override
    @Nonnull
    public Column visitOperation(@Nonnull Operation op) {
        // Recursively visit child arguments to generate their columns
        List<Column> argColumns = op.args().stream()
                .map(arg -> arg.accept(this))
                .toList();

        // Dispatch to appropriate evaluation method based on operation name
        return evaluateOperation(op.name(), argColumns, op.getType());
    }

    /**
     * Central dispatch for all operations.
     */
    @Nonnull
    private Column evaluateOperation(String name, List<Column> args, Type resultType) {
        return switch (name) {
            // Arithmetic
            case "add" -> evaluateAdd(args, resultType);
            case "sub" -> evaluateSub(args, resultType);
            case "multiply" -> evaluateMultiply(args, resultType);
            case "divide" -> evaluateDivide(args, resultType);
            case "mod" -> evaluateMod(args, resultType);

            // Comparison
            case "equals" -> evaluateEquals(args, resultType);
            case "notEquals" -> evaluateNotEquals(args, resultType);
            case "gt" -> evaluateGreaterThan(args, resultType);
            case "lt" -> evaluateLessThan(args, resultType);
            case "geq" -> evaluateGreaterEqual(args, resultType);
            case "leq" -> evaluateLessEqual(args, resultType);

            // Math functions
            case "abs" -> evaluateAbs(args, resultType);
            case "ceiling" -> evaluateCeiling(args, resultType);
            case "floor" -> evaluateFloor(args, resultType);
            case "truncate" -> evaluateTruncate(args, resultType);
            case "exp" -> evaluateExp(args, resultType);
            case "ln" -> evaluateLn(args, resultType);
            case "log" -> evaluateLog(args, resultType);
            case "sqrt" -> evaluateSqrt(args, resultType);

            // String functions
            case "substring" -> evaluateSubstring(args);
            case "startsWith" -> args.get(0).startsWith(args.get(1));
            case "endsWith" -> args.get(0).endsWith(args.get(1));
            case "contains" -> args.get(0).contains(args.get(1));
            case "upper" -> upper(args.get(0));
            case "lower" -> lower(args.get(0));
            case "replace" -> regexp_replace(args.get(0), args.get(1), args.get(2));
            case "matches" -> args.get(0).rlike(args.get(1));
            case "length" -> length(args.get(0));

            // Boolean operators
            case "and" -> args.get(0).and(args.get(1));
            case "or" -> args.get(0).or(args.get(1));
            case "xor" -> args.get(0).bitwiseXOR(args.get(1));
            case "implies" -> not(args.get(0)).or(args.get(1));
            case "not" -> not(args.get(0));

            default -> throw new UnsupportedOperationException(
                    "Unknown operation: " + name + " with result type: " + resultType);
        };
    }

    // ========== Arithmetic Operations ==========

    @Nonnull
    private Column evaluateAdd(List<Column> args, Type resultType) {
        Column left = args.get(0);
        Column right = args.get(1);

        return switch ((PrimitiveType) resultType) {
            case INTEGER, DECIMAL -> left.plus(right);
            case STRING -> concat(left, right);
            case DATE_TIME -> dateTime(left).plus(quantity(right));
            case QUANTITY -> quantity(left).plus(quantity(right));
            default -> throw new IllegalArgumentException(
                    "Unsupported result type for add: " + resultType);
        };
    }

    @Nonnull
    private Column evaluateSub(List<Column> args, Type resultType) {
        Column left = args.get(0);
        Column right = args.get(1);

        return switch ((PrimitiveType) resultType) {
            case INTEGER, DECIMAL -> left.minus(right);
            case DATE_TIME -> dateTime(left).minus(quantity(right));
            case QUANTITY -> quantity(left).minus(quantity(right));
            default -> throw new IllegalArgumentException(
                    "Unsupported result type for sub: " + resultType);
        };
    }

    @Nonnull
    private Column evaluateMultiply(List<Column> args, Type resultType) {
        Column left = args.get(0);
        Column right = args.get(1);

        return switch ((PrimitiveType) resultType) {
            case INTEGER, DECIMAL -> left.multiply(right);
            case QUANTITY -> quantity(left).multiply(quantity(right));
            default -> throw new IllegalArgumentException(
                    "Unsupported result type for multiply: " + resultType);
        };
    }

    @Nonnull
    private Column evaluateDivide(List<Column> args, Type resultType) {
        Column left = args.get(0);
        Column right = args.get(1);

        return switch ((PrimitiveType) resultType) {
            case INTEGER, DECIMAL -> left.divide(right);
            case QUANTITY -> quantity(left).divide(quantity(right));
            default -> throw new IllegalArgumentException(
                    "Unsupported result type for divide: " + resultType);
        };
    }

    @Nonnull
    private Column evaluateMod(List<Column> args, Type resultType) {
        return args.get(0).mod(args.get(1));
    }

    // ========== Comparison Operations ==========

    @Nonnull
    private Column evaluateGreaterThan(List<Column> args, Type inputType) {
        Column left = args.get(0);
        Column right = args.get(1);

        // Get input type from first argument's type (before comparison)
        // Note: resultType is always BOOLEAN for comparisons
        return switch ((PrimitiveType) inputType) {
            case INTEGER, DECIMAL, STRING -> left.gt(right);
            case QUANTITY -> quantity(left).gt(quantity(right));
            case DATE_TIME -> dateTime(left).gt(dateTime(right));
            case DATE -> date(left).gt(date(right));
            case TIME -> time(left).gt(time(right));
            default -> throw new IllegalArgumentException(
                    "Unsupported input type for gt: " + inputType);
        };
    }

    @Nonnull
    private Column evaluateLessThan(List<Column> args, Type inputType) {
        Column left = args.get(0);
        Column right = args.get(1);

        return switch ((PrimitiveType) inputType) {
            case INTEGER, DECIMAL, STRING -> left.lt(right);
            case QUANTITY -> quantity(left).lt(quantity(right));
            case DATE_TIME -> dateTime(left).lt(dateTime(right));
            case DATE -> date(left).lt(date(right));
            case TIME -> time(left).lt(time(right));
            default -> throw new IllegalArgumentException(
                    "Unsupported input type for lt: " + inputType);
        };
    }

    @Nonnull
    private Column evaluateGreaterEqual(List<Column> args, Type inputType) {
        Column left = args.get(0);
        Column right = args.get(1);

        return switch ((PrimitiveType) inputType) {
            case INTEGER, DECIMAL, STRING -> left.geq(right);
            case QUANTITY -> quantity(left).geq(quantity(right));
            case DATE_TIME -> dateTime(left).geq(dateTime(right));
            case DATE -> date(left).geq(date(right));
            case TIME -> time(left).geq(time(right));
            default -> throw new IllegalArgumentException(
                    "Unsupported input type for geq: " + inputType);
        };
    }

    @Nonnull
    private Column evaluateLessEqual(List<Column> args, Type inputType) {
        Column left = args.get(0);
        Column right = args.get(1);

        return switch ((PrimitiveType) inputType) {
            case INTEGER, DECIMAL, STRING -> left.leq(right);
            case QUANTITY -> quantity(left).leq(quantity(right));
            case DATE_TIME -> dateTime(left).leq(dateTime(right));
            case DATE -> date(left).leq(date(right));
            case TIME -> time(left).leq(time(right));
            default -> throw new IllegalArgumentException(
                    "Unsupported input type for leq: " + inputType);
        };
    }

    @Nonnull
    private Column evaluateEquals(List<Column> args, Type inputType) {
        return args.get(0).equalTo(args.get(1));
    }

    @Nonnull
    private Column evaluateNotEquals(List<Column> args, Type inputType) {
        return args.get(0).notEqual(args.get(1));
    }

    // ========== Math Functions ==========

    @Nonnull
    private Column evaluateAbs(List<Column> args, Type resultType) {
        Column target = args.get(0);

        return switch ((PrimitiveType) resultType) {
            case INTEGER, DECIMAL -> abs(target);
            case QUANTITY -> quantity(target).abs();
            default -> throw new IllegalArgumentException(
                    "Unsupported result type for abs: " + resultType);
        };
    }

    @Nonnull
    private Column evaluateCeiling(List<Column> args, Type resultType) {
        return ceil(args.get(0));
    }

    @Nonnull
    private Column evaluateFloor(List<Column> args, Type resultType) {
        return floor(args.get(0));
    }

    @Nonnull
    private Column evaluateTruncate(List<Column> args, Type resultType) {
        // Truncate towards zero
        Column target = args.get(0);
        return when(target.geq(lit(0)), floor(target))
                .otherwise(ceil(target));
    }

    @Nonnull
    private Column evaluateExp(List<Column> args, Type resultType) {
        return exp(args.get(0));
    }

    @Nonnull
    private Column evaluateLn(List<Column> args, Type resultType) {
        return log(args.get(0));
    }

    @Nonnull
    private Column evaluateLog(List<Column> args, Type resultType) {
        return log(10.0, args.get(0));
    }

    @Nonnull
    private Column evaluateSqrt(List<Column> args, Type resultType) {
        return sqrt(args.get(0));
    }

    // ========== String Functions ==========

    @Nonnull
    private Column evaluateSubstring(List<Column> args) {
        final Column targetColumn = args.get(0);
        // FHIRPath uses 0-based indexing, Spark uses 1-based
        final Column posColumn = args.get(1).plus(lit(1));

        // Handle optional length parameter
        final Column lengthColumn = args.size() > 2 ? args.get(2) : lit(null);
        final Column nonNullLengthColumn = coalesce(lengthColumn, lit(Integer.MAX_VALUE));

        // FHIRPath null propagation rules
        final Column nullPropagationCondition = targetColumn.isNull()
                .or(posColumn.isNull());

        final Column posOutOfBoundsCondition = posColumn.leq(0)
                .or(posColumn.gt(length(targetColumn)));

        final Column nullCondition = nullPropagationCondition
                .or(posOutOfBoundsCondition);

        return when(not(nullCondition),
                substr(targetColumn, posColumn, nonNullLengthColumn));
    }

    // ========== Infrastructure Nodes ==========

    @Override
    @Nonnull
    public Column visitLiteral(@Nonnull Literal lit) {
        DataType sparkType = SparkTypeMapper.toSparkDataType(lit.type());
        return lit(lit.value()).cast(sparkType);
    }

    @Override
    @Nonnull
    public Column visitTraversal(@Nonnull Traversal trav) {
        Column target = trav.target().accept(this);
        return target.getField(trav.fieldSpec().name());
    }

    @Override
    @Nonnull
    public Column visitCast(@Nonnull Cast cast) {
        Column child = cast.child().accept(this);
        DataType sparkType = SparkTypeMapper.toSparkDataType(cast.targetType());
        return valueOf(child).apply(
                a -> a.cast(sparkType),
                s -> s.cast(sparkType)
        );
    }

    @Override
    @Nonnull
    public Column visitResource(@Nonnull Resource res) {
        return col("resource");
    }

    @Override
    @Nonnull
    public Column visitGetValue(@Nonnull CastToSystem castToSystem) {
        Column child = castToSystem.child().accept(this);
        return child.getField("value");
    }

    @Override
    @Nonnull
    public Column visitCount(@Nonnull Count count) {
        Column child = count.child().accept(this);
        return valueOf(child).applyNonNull(
                functions::size,
                cons(1),
                lit(0)
        );
    }

    @Override
    @Nonnull
    public Column visitExists(@Nonnull Exists exists) {
        Column child = exists.child().accept(this);
        return valueOf(child).applyNonNull(
                cons(true),
                cons(true),
                lit(false)
        );
    }

    @Override
    @Nonnull
    public Column visitUnion(@Nonnull Union union) {
        Column left = union.left().accept(this);
        Column right = union.right().accept(this);
        return array_union(
                valueOf(left).asArray(),
                valueOf(right).asArray()
        );
    }
}
```

**Key Features:**
- ✅ Centralized: All Spark-specific logic in one class
- ✅ Organized: Methods grouped by category (arithmetic, comparison, etc.)
- ✅ Type-dispatched: Uses signature's result type for correct evaluation
- ✅ Null-safe: Implements FHIRPath null propagation rules
- ✅ Documented: Comments explain FHIRPath semantics

---

## 4. Detailed Examples

### 4.1 Simple Arithmetic: `5 + 3`

**AST:**
```java
AstBinaryOperator("+", AstLiteral(5), AstLiteral(3))
```

**Resolution:**
```java
// 1. Analyze literals
IRNode left = new Literal(5, Type.INTEGER);
IRNode right = new Literal(3, Type.INTEGER);

// 2. Query registry
List<FunctionSignature> sigs = OperationRegistry.getSignatures("add");
// Returns: [INTEGER+INTEGER→INTEGER, DECIMAL+DECIMAL→DECIMAL, ...]

// 3. Resolve overload
ResolvedCall call = OverloadResolver.resolveCall(sigs, List.of(left, right));
// Selects: biOperator(INTEGER) with cost 0 (exact match)

// 4. Create Operation
IRNode ir = new Operation("add", List.of(left, right), call.signature());
```

**Type Query:**
```java
ir.getType()
→ signature.resultType()
→ INTEGER
```

**Spark Code Generation:**
```java
Column col = ir.accept(new SparkCodeGenerator());
→ visitOperation(Operation("add", [...], biOperator(INTEGER)))
→ evaluateAdd([lit(5), lit(3)], INTEGER)
→ lit(5).plus(lit(3))
→ Column
```

### 4.2 String Concatenation: `"Hello" + " " + "World"`

**AST:**
```java
AstBinaryOperator("+",
    AstBinaryOperator("+", AstLiteral("Hello"), AstLiteral(" ")),
    AstLiteral("World"))
```

**IR Tree:**
```java
Operation("add",
    List.of(
        Operation("add",
            List.of(
                Literal("Hello", STRING),
                Literal(" ", STRING)
            ),
            biOperator(STRING)  // Inner add signature
        ),
        Literal("World", STRING)
    ),
    biOperator(STRING)  // Outer add signature
)
```

**Spark Code Generation:**
```java
// Outer operation
visitOperation(outerAdd)
→ evaluateAdd([innerColumn, worldColumn], STRING)
→ concat(innerColumn, worldColumn)

// Inner operation (recursive call)
visitOperation(innerAdd)
→ evaluateAdd([helloColumn, spaceColumn], STRING)
→ concat(helloColumn, spaceColumn)

// Result: concat(concat(lit("Hello"), lit(" ")), lit("World"))
```

### 4.3 Type Coercion: `5.5 + 3` (DECIMAL + INTEGER → DECIMAL)

**Resolution:**
```java
// 1. Analyze operands
IRNode left = new Literal(5.5, Type.DECIMAL);
IRNode right = new Literal(3, Type.INTEGER);

// 2. Query signatures
List<FunctionSignature> sigs = OperationRegistry.getSignatures("add");

// 3. Resolve with adaptation
ResolvedCall call = OverloadResolver.resolveCall(sigs, List.of(left, right));
// Selects: biOperator(DECIMAL)
// Adapts right: INTEGER → DECIMAL via Cast
// Returns: signature=biOperator(DECIMAL), args=[left, Cast(right, DECIMAL)]

// 4. Create Operation with adapted args
IRNode ir = new Operation("add",
    List.of(left, new Cast(right, Type.DECIMAL)),
    call.signature()
);
```

**Result:** Cast node inserted automatically, type is DECIMAL

### 4.4 Complex Expression: `Patient.birthDate + 1 year`

**AST:**
```java
AstBinaryOperator("+",
    AstTraversal(AstVariable("$this"), "birthDate"),
    AstLiteral(Quantity.of(1, "year")))
```

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
    biOperatorLeft(DATE_TIME, QUANTITY)  // DATE_TIME + QUANTITY → DATE_TIME
)
```

**Type:** `DATE_TIME` (result follows left operand per signature)

**Spark Code Generation:**
```java
visitOperation(add)
→ evaluateAdd([birthDateColumn, quantityColumn], DATE_TIME)
→ dateTime(birthDateColumn).plus(quantity(quantityColumn))
→ Column (Spark expression using custom DateTime UDF)
```

---

## 5. Migration Path

### 5.1 Overview

The migration is divided into **4 phases**, each independently testable and revertible:

```
Current Design
    ↓ Phase 1 (1-2 weeks)
Add Signature Storage
    ↓ Phase 2 (1 week)
Create Operation Registry
    ↓ Phase 3 (1-2 weeks)
Extract Visitor Pattern
    ↓ Phase 4 (1-2 weeks)
Consolidate to Operation Node
```

**Total Timeline:** 5-7 weeks
**Risk Level:** Low-Medium (incremental, testable)

### 5.2 Phase 1: Add Signature Storage (Weeks 1-2)

**Goal:** Eliminate type redundancy by storing resolved signature in nodes

**Changes:**

1. **Add `signature` field to existing operation classes**

```java
// Before: Add.java
public record Add(IRNode left, IRNode right) implements IRNode {
    public static final List<FunctionSignature> SIGNATURES = ...;

    @Override
    public Type getType() {
        return left.getType();  // Recalculated
    }

    @Override
    public Column eval() { ... }
}

// After: Add.java
public record Add(
    IRNode left,
    IRNode right,
    FunctionSignature signature  // ← ADDED
) implements IRNode {
    public static final List<FunctionSignature> SIGNATURES = ...;

    @Override
    public Type getType() {
        return signature.resultType();  // ← Single source of truth
    }

    @Override
    public Column eval() { ... }  // Unchanged
}
```

2. **Update FunctionRegistry to pass signature**

```java
// Before: FunctionRegistry.java:74-76
private static IRNode doResolve(IRNodeBuilder builder, List<IRNode> args) {
    List<FunctionSignature> candidates = builder.getSignatures();
    OverloadResolver.ResolvedCall resolvedCall = OverloadResolver.resolveCall(candidates, args);
    return builder.build(resolvedCall.args().toArray(new IRNode[0]));
    // ↑ Signature discarded
}

// After: FunctionRegistry.java:74-76
private static IRNode doResolve(IRNodeBuilder builder, List<IRNode> args) {
    List<FunctionSignature> candidates = builder.getSignatures();
    OverloadResolver.ResolvedCall resolvedCall = OverloadResolver.resolveCall(candidates, args);
    return builder.build(
        resolvedCall.signature(),  // ← Pass signature
        resolvedCall.args().toArray(new IRNode[0])
    );
}
```

3. **Update IRNodeBuilder interface**

```java
// Before:
public interface IRNodeBuilder {
    IRNode build(IRNode... children);
}

// After:
public interface IRNodeBuilder {
    IRNode build(FunctionSignature signature, IRNode... children);
}
```

4. **Update IRClassBuilder to pass signature to constructor**

```java
// Before: IRClassBuilder.java:20-25
@Override
public IRNode build(IRNode... children) {
    return (IRNode) irClass.getDeclaredConstructors()[0]
        .newInstance((Object[]) children);
}

// After: IRClassBuilder.java:20-25
@Override
public IRNode build(FunctionSignature signature, IRNode... children) {
    Object[] args = new Object[children.length + 1];
    System.arraycopy(children, 0, args, 0, children.length);
    args[children.length] = signature;
    return (IRNode) irClass.getDeclaredConstructors()[0]
        .newInstance(args);
}
```

**Files Modified:**
- All operation classes (Add, Abs, Exp, etc.) - add signature field
- `FunctionRegistry.java` - pass signature to builder
- `IRNodeBuilder.java` - update interface
- `IRClassBuilder.java` - pass signature to constructor

**Testing:**
- All existing tests should pass unchanged
- Add test verifying signature is stored: `assertEquals(expectedSig, operation.signature())`

**Validation:**
- Run full test suite
- Verify no performance regression
- Check that getType() returns signature.resultType()

**Rollback Strategy:**
- Revert commit
- All changes are additive (signature field), no breaking changes

**Risk:** **Low**
- Pure refactoring, no behavior change
- Compiler enforces correct signature passing
- Backward compatible (eval() unchanged)

---

### 5.3 Phase 2: Create Operation Registry (Week 3)

**Goal:** Centralize function definitions for easier maintenance

**Changes:**

1. **Create OperationRegistry.java** (see section 3.3 above)

2. **Update FunctionRegistry to query OperationRegistry**

```java
// Before: FunctionRegistry.java:37-44
final static Map<String, IRNodeBuilder> FUNCTIONS = Map.ofEntries(
    Map.entry("count", forClass(Count.class)),
    Map.entry("exists", forClass(Exists.class)),
    Map.entry("abs", forClass(Abs.class)),
    Map.entry("exp", forClass(Exp.class)),
    // ...
);

// After: FunctionRegistry.java (simplified)
public static IRNode resolve(Analyzer analyzer, AstFunctionCall call) {
    List<IRNode> args = call.children().map(analyzer::analyze).toList();
    String name = call.functionName();

    // Query registry for signatures
    List<FunctionSignature> sigs = OperationRegistry.getSignatures(name);

    if (sigs.isEmpty()) {
        // Fallback to old builder-based approach for infrastructure nodes
        return Optional.ofNullable(FUNCTIONS.get(name))
            .map(builder -> doResolve(builder, args))
            .orElseThrow(() -> new UnsupportedOperationException(
                "Function '" + name + "' is not supported"));
    }

    // Use registry-based resolution (still creates specific classes for now)
    ResolvedCall call = OverloadResolver.resolveCall(sigs, args);
    return FUNCTIONS.get(name).build(call.signature(), call.args());
}
```

3. **Remove SIGNATURES from operation classes**

```java
// Before: Abs.java
public record Abs(IRNode target, FunctionSignature signature) implements IRNode {
    public static final List<FunctionSignature> SIGNATURES = Stream.of(
        INTEGER, Type.DECIMAL, Type.QUANTITY
    ).map(t -> new FunctionSignature(List.of(t), t)).toList();
    // ...
}

// After: Abs.java
public record Abs(IRNode target, FunctionSignature signature) implements IRNode {
    // No SIGNATURES field - defined in OperationRegistry
    // ...
}
```

**Files Created:**
- `src/main/java/com/example/fhirpath/ir/OperationRegistry.java`

**Files Modified:**
- `FunctionRegistry.java` - query OperationRegistry
- All operation classes - remove SIGNATURES field

**Testing:**
- Verify all operations still resolve correctly
- Add tests for OperationRegistry.getSignatures()
- Verify unknown operations throw appropriate exceptions

**Validation:**
- Run full test suite
- Check that all FHIRPath functions still work
- Verify error messages are clear for unsupported operations

**Rollback Strategy:**
- Revert to Phase 1
- Keep OperationRegistry but don't use it (no harm)

**Risk:** **Low**
- Pure refactoring of signature storage
- No change to IR tree structure
- All tests should still pass

---

### 5.4 Phase 3: Extract Visitor Pattern (Weeks 4-5)

**Goal:** Separate IR structure from Spark-specific evaluation

**Changes:**

1. **Create IRNodeVisitor interface** (see section 3.4 above)

2. **Create SparkCodeGenerator** (see section 3.5 above)

3. **Add accept() method to IRNode**

```java
// IRNode.java
public interface IRNode {
    Type getType();
    Column eval();  // Keep for backward compatibility
    <T> T accept(IRNodeVisitor<T> visitor);  // NEW
}
```

4. **Implement accept() in all node classes**

```java
// Abs.java
public record Abs(IRNode target, FunctionSignature signature) implements IRNode {
    @Override
    public Type getType() { return signature.resultType(); }

    @Override
    public Column eval() {
        // Delegate to visitor for now (backward compatible)
        return accept(new SparkCodeGenerator());
    }

    @Override
    public <T> T accept(IRNodeVisitor<T> visitor) {
        return visitor.visitAbs(this);  // Specific visit method
    }
}
```

5. **Implement visitor methods in SparkCodeGenerator**

```java
// SparkCodeGenerator.java
public class SparkCodeGenerator implements IRNodeVisitor<Column> {

    @Override
    public Column visitAbs(Abs abs) {
        Column target = abs.target().accept(this);
        return switch((PrimitiveType) abs.getType()) {
            case INTEGER, DECIMAL -> functions.abs(target);
            case QUANTITY -> quantity(target).abs();
            default -> throw new IllegalArgumentException(...);
        };
    }

    // ... other visit methods
}
```

**Files Created:**
- `src/main/java/com/example/fhirpath/ir/IRNodeVisitor.java`
- `src/main/java/com/example/fhirpath/codegen/SparkCodeGenerator.java`

**Files Modified:**
- `IRNode.java` - add accept() method
- All IRNode classes - implement accept(), delegate eval() to visitor

**Testing:**
- All existing tests should still pass (eval() works via delegation)
- Add visitor-specific tests:
  - Test that accept(visitor) produces same result as eval()
  - Test visitor methods directly

**Validation:**
- Run full test suite
- Benchmark: verify no performance regression from delegation
- Test with real Spark queries

**Rollback Strategy:**
- Remove accept() method
- Remove SparkCodeGenerator
- Revert to Phase 2

**Risk:** **Medium**
- New abstraction layer (visitor pattern)
- Delegation adds slight indirection
- Must ensure all cases handled in visitor

**Mitigation:**
- Keep eval() working (backward compatible)
- Extensive testing of visitor implementation
- Gradual cutover (optional feature flag)

---

### 5.5 Phase 4: Consolidate to Operation Node (Weeks 6-7)

**Goal:** Eliminate class proliferation, use generic Operation node

**Changes:**

1. **Create Operation class** (see section 3.2 above)

2. **Update FunctionRegistry to create Operation instances**

```java
// Before: FunctionRegistry.java
public static IRNode resolve(Analyzer analyzer, AstFunctionCall call) {
    List<IRNode> args = call.children().map(analyzer::analyze).toList();
    String name = call.functionName();

    List<FunctionSignature> sigs = OperationRegistry.getSignatures(name);
    ResolvedCall resolvedCall = OverloadResolver.resolveCall(sigs, args);

    return FUNCTIONS.get(name).build(resolvedCall.signature(), resolvedCall.args());
    // ↑ Still creates specific class (Abs, Add, etc.)
}

// After: FunctionRegistry.java
public static IRNode resolve(Analyzer analyzer, AstFunctionCall call) {
    List<IRNode> args = call.children().map(analyzer::analyze).toList();
    String name = call.functionName();

    List<FunctionSignature> sigs = OperationRegistry.getSignatures(name);

    if (!sigs.isEmpty()) {
        // Registry-based operations → generic Operation node
        ResolvedCall resolvedCall = OverloadResolver.resolveCall(sigs, args);
        return new Operation(name, resolvedCall.args(), resolvedCall.signature());
    } else {
        // Fallback for infrastructure nodes (Count, Exists, etc.)
        return Optional.ofNullable(FUNCTIONS.get(name))
            .map(builder -> builder.build(/* ... */))
            .orElseThrow(() -> new UnsupportedOperationException(...));
    }
}
```

3. **Update SparkCodeGenerator to handle Operation**

```java
// SparkCodeGenerator.java
@Override
public Column visitOperation(Operation op) {
    List<Column> argColumns = op.args().stream()
        .map(arg -> arg.accept(this))
        .toList();

    return evaluateOperation(op.name(), argColumns, op.getType());
}

// Central dispatch method (see section 3.5)
private Column evaluateOperation(String name, List<Column> args, Type resultType) {
    return switch(name) {
        case "add" -> evaluateAdd(args, resultType);
        case "abs" -> evaluateAbs(args, resultType);
        // ...
    };
}
```

4. **Update IRNodeVisitor interface**

```java
// Before: IRNodeVisitor.java
public interface IRNodeVisitor<T> {
    T visitAbs(Abs node);
    T visitAdd(Add node);
    T visitExp(Exp node);
    // ... one method per operation class
}

// After: IRNodeVisitor.java
public interface IRNodeVisitor<T> {
    T visitOperation(Operation node);  // Single method for all operations
    T visitLiteral(Literal node);
    T visitTraversal(Traversal node);
    // ... only infrastructure nodes
}
```

5. **Delete old operation classes**

```bash
# Remove:
src/main/java/com/example/fhirpath/ir/math/Abs.java
src/main/java/com/example/fhirpath/ir/math/Exp.java
src/main/java/com/example/fhirpath/ir/arythm/Add.java
src/main/java/com/example/fhirpath/ir/arythm/Sub.java
src/main/java/com/example/fhirpath/ir/arythm/Divide.java
src/main/java/com/example/fhirpath/ir/comparison/GreaterThan.java
src/main/java/com/example/fhirpath/ir/comparison/LessThan.java
src/main/java/com/example/fhirpath/ir/comparison/GreaterEqual.java
src/main/java/com/example/fhirpath/ir/string/Substring.java
# ... etc.
```

6. **Update tests**

```java
// Before: AddTest.java
@Test
void testAddIntegers() {
    IRNode left = new Literal(5, Type.INTEGER);
    IRNode right = new Literal(3, Type.INTEGER);
    IRNode add = new Add(left, right, biOperator(INTEGER));

    assertInstanceOf(Add.class, add);
    assertEquals(Type.INTEGER, add.getType());
}

// After: OperationTest.java
@Test
void testAddIntegers() {
    IRNode left = new Literal(5, Type.INTEGER);
    IRNode right = new Literal(3, Type.INTEGER);
    IRNode add = new Operation("add", List.of(left, right), biOperator(INTEGER));

    assertInstanceOf(Operation.class, add);
    assertEquals("add", ((Operation) add).name());
    assertEquals(Type.INTEGER, add.getType());
}
```

**Files Created:**
- `src/main/java/com/example/fhirpath/ir/Operation.java`

**Files Modified:**
- `FunctionRegistry.java` - create Operation instead of specific classes
- `SparkCodeGenerator.java` - handle Operation generically
- `IRNodeVisitor.java` - replace specific visit methods with visitOperation()
- Test files - update to use Operation

**Files Deleted:**
- 12+ operation classes (Add, Abs, Exp, etc.)

**Testing:**
- Update all operation-specific tests to use Operation
- Verify all FHIRPath expressions still work
- Test error handling for unknown operations
- Benchmark performance (should be same or better)

**Validation:**
- Run full test suite (with updated tests)
- Integration tests with real Spark queries
- Verify error messages are still clear
- Check debugger experience (toString() works well)

**Rollback Strategy:**
- Revert to Phase 3
- Keep Operation class (no harm)
- Restore specific operation classes

**Risk:** **Medium-High**
- Most significant change (deletes many classes)
- Tests need updates
- Debugging experience changes

**Mitigation:**
- Extensive testing at each step
- Good toString() implementation for debugging
- Gradual migration (can keep both approaches temporarily)

---

### 5.6 Post-Migration Validation

After completing all phases, validate:

1. **Functionality:**
   - All FHIRPath expressions work correctly
   - Error messages are clear
   - Null handling follows FHIRPath spec

2. **Performance:**
   - No regression in query execution time
   - Memory usage similar or improved
   - Compilation time similar

3. **Code Quality:**
   - Class count reduced from 18 to 7
   - Lines of code reduced by ~20%
   - Cyclomatic complexity similar or lower

4. **Maintainability:**
   - Easy to add new functions (just registry entry)
   - Easy to find function logic (in SparkCodeGenerator)
   - Clear separation of concerns

---

## 6. Multi-Target Support

### 6.1 Adding SQL Server Support

After Phase 4, adding SQL Server support requires only implementing a new visitor:

**File:** `src/main/java/com/example/fhirpath/codegen/SqlServerCodeGenerator.java`

```java
package com.example.fhirpath.codegen;

import com.example.fhirpath.ir.*;
import com.example.fhirpath.typing.PrimitiveType;
import com.example.fhirpath.typing.Type;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * Generates SQL Server T-SQL expressions from FHIRPath IR trees.
 */
public class SqlServerCodeGenerator implements IRNodeVisitor<String> {

    @Override
    @Nonnull
    public String visitOperation(@Nonnull Operation op) {
        List<String> argExprs = op.args().stream()
                .map(arg -> arg.accept(this))
                .toList();

        return evaluateOperation(op.name(), argExprs, op.getType());
    }

    @Nonnull
    private String evaluateOperation(String name, List<String> args, Type resultType) {
        return switch (name) {
            // Arithmetic
            case "add" -> evaluateAdd(args, resultType);
            case "sub" -> evaluateSub(args, resultType);
            case "multiply" -> "(" + args.get(0) + " * " + args.get(1) + ")";
            case "divide" -> "(" + args.get(0) + " / " + args.get(1) + ")";
            case "mod" -> "(" + args.get(0) + " % " + args.get(1) + ")";

            // Comparison
            case "equals" -> "(" + args.get(0) + " = " + args.get(1) + ")";
            case "notEquals" -> "(" + args.get(0) + " <> " + args.get(1) + ")";
            case "gt" -> "(" + args.get(0) + " > " + args.get(1) + ")";
            case "lt" -> "(" + args.get(0) + " < " + args.get(1) + ")";
            case "geq" -> "(" + args.get(0) + " >= " + args.get(1) + ")";
            case "leq" -> "(" + args.get(0) + " <= " + args.get(1) + ")";

            // Math functions
            case "abs" -> "ABS(" + args.get(0) + ")";
            case "ceiling" -> "CEILING(" + args.get(0) + ")";
            case "floor" -> "FLOOR(" + args.get(0) + ")";
            case "sqrt" -> "SQRT(" + args.get(0) + ")";
            case "exp" -> "EXP(" + args.get(0) + ")";
            case "ln" -> "LOG(" + args.get(0) + ")";
            case "log" -> "LOG10(" + args.get(0) + ")";

            // String functions
            case "substring" -> evaluateSubstring(args);
            case "startsWith" -> "(" + args.get(0) + " LIKE " + args.get(1) + " + '%')";
            case "endsWith" -> "(" + args.get(0) + " LIKE '%' + " + args.get(1) + ")";
            case "contains" -> "CHARINDEX(" + args.get(1) + ", " + args.get(0) + ") > 0";
            case "upper" -> "UPPER(" + args.get(0) + ")";
            case "lower" -> "LOWER(" + args.get(0) + ")";
            case "length" -> "LEN(" + args.get(0) + ")";

            // Boolean
            case "and" -> "(" + args.get(0) + " AND " + args.get(1) + ")";
            case "or" -> "(" + args.get(0) + " OR " + args.get(1) + ")";
            case "not" -> "(NOT " + args.get(0) + ")";

            default -> throw new UnsupportedOperationException(
                    "Unknown operation: " + name);
        };
    }

    @Nonnull
    private String evaluateAdd(List<String> args, Type resultType) {
        String left = args.get(0);
        String right = args.get(1);

        return switch ((PrimitiveType) resultType) {
            case INTEGER, DECIMAL -> "(" + left + " + " + right + ")";
            case STRING -> "CONCAT(" + left + ", " + right + ")";
            // DATE_TIME and QUANTITY would need custom handling
            default -> throw new IllegalArgumentException(
                    "Unsupported result type for add: " + resultType);
        };
    }

    @Nonnull
    private String evaluateSub(List<String> args, Type resultType) {
        return "(" + args.get(0) + " - " + args.get(1) + ")";
    }

    @Nonnull
    private String evaluateSubstring(List<String> args) {
        String target = args.get(0);
        String start = args.get(1);
        String length = args.size() > 2 ? args.get(2) : "LEN(" + target + ")";

        // SQL Server SUBSTRING uses 1-based indexing, FHIRPath uses 0-based
        return "SUBSTRING(" + target + ", " + start + " + 1, " + length + ")";
    }

    @Override
    @Nonnull
    public String visitLiteral(@Nonnull Literal lit) {
        Object value = lit.value();
        if (value == null) return "NULL";
        if (value instanceof String) return "'" + value + "'";
        if (value instanceof Boolean) return (Boolean) value ? "1" : "0";
        return value.toString();
    }

    @Override
    @Nonnull
    public String visitTraversal(@Nonnull Traversal trav) {
        String target = trav.target().accept(this);
        return target + "." + trav.fieldSpec().name();
    }

    @Override
    @Nonnull
    public String visitCast(@Nonnull Cast cast) {
        String child = cast.child().accept(this);
        String sqlType = toSqlServerType(cast.targetType());
        return "CAST(" + child + " AS " + sqlType + ")";
    }

    @Override
    @Nonnull
    public String visitResource(@Nonnull Resource res) {
        return "resource";
    }

    // ... implement other visit methods

    private String toSqlServerType(Type type) {
        return switch ((PrimitiveType) type) {
            case INTEGER -> "INT";
            case DECIMAL -> "DECIMAL(18,6)";
            case STRING -> "NVARCHAR(MAX)";
            case BOOLEAN -> "BIT";
            case DATE -> "DATE";
            case DATE_TIME -> "DATETIME2";
            default -> throw new IllegalArgumentException("Unsupported type: " + type);
        };
    }
}
```

**Usage:**

```java
// Same IR tree, different target
IRNode ir = analyzer.analyze(astNode);

// Generate Spark code
Column sparkColumn = ir.accept(new SparkCodeGenerator());

// Generate SQL Server code
String sqlServerExpression = ir.accept(new SqlServerCodeGenerator());
```

**Effort:** 1-2 weeks
**Risk:** Low (no changes to IR or existing code)

### 6.2 Target Comparison

| Feature | Spark | SQL Server | PostgreSQL |
|---------|-------|------------|------------|
| **Visitor Class** | SparkCodeGenerator | SqlServerCodeGenerator | PostgreSqlCodeGenerator |
| **Return Type** | Column | String | String |
| **Arithmetic** | Column.plus() | "(" + left + " + " + right + ")" | Same as SQL Server |
| **String Concat** | functions.concat() | "CONCAT(a, b)" | "a \|\| b" |
| **Substring** | functions.substr() | "SUBSTRING(str, pos+1, len)" | "SUBSTRING(str FROM pos+1 FOR len)" |
| **Abs** | functions.abs() | "ABS(x)" | "ABS(x)" |
| **Custom Types (Quantity)** | Custom UDF | Complex (may need JSON) | Complex (may need JSON) |

---

## 7. Comparison with Current Design

### 7.1 Side-by-Side Comparison

| Aspect | Current Design | Option 4 Design |
|--------|----------------|-----------------|
| **Operation Classes** | 18+ (Add, Abs, Exp, ...) | 1 (Operation) |
| **Infrastructure Classes** | 6 (Literal, Cast, ...) | 6 (unchanged) |
| **Total IR Classes** | 24+ | 7 |
| **Type Definition** | 3 places (signature, getType(), eval()) | 1 place (signature) |
| **Signature Storage** | Discarded after resolution | Stored in node |
| **Multi-Target Support** | Locked to Spark | Visitor per target |
| **Code Generation** | eval() in each class | Visitor methods |
| **Add New Function** | Create new class (~50 lines) | Add registry entry (~3 lines) |
| **Add New Target** | Duplicate all classes | Implement new visitor |
| **Lines of Code (est.)** | ~3000 | ~2400 (20% reduction) |
| **Testability** | Requires Spark runtime | Can test without Spark |

### 7.2 Concrete Example: Adding `pow(x, y)` Function

**Current Design:**

1. Create `src/main/java/com/example/fhirpath/ir/math/Pow.java`:
```java
package com.example.fhirpath.ir.math;

import com.example.fhirpath.analyzer.FunctionSignature;
import com.example.fhirpath.ir.IRNode;
import com.example.fhirpath.typing.Type;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.functions;

import java.util.List;

public record Pow(IRNode base, IRNode exponent, FunctionSignature signature) implements IRNode {

    public static final List<FunctionSignature> SIGNATURES = List.of(
        new FunctionSignature(List.of(Type.INTEGER, Type.INTEGER), Type.DECIMAL),
        new FunctionSignature(List.of(Type.DECIMAL, Type.DECIMAL), Type.DECIMAL)
    );

    @Override
    public Type getType() {
        return signature.resultType();
    }

    @Override
    public Column eval() {
        return functions.pow(base.eval(), exponent.eval());
    }
}
```

2. Register in `FunctionRegistry.java`:
```java
Map.entry("pow", forClass(Pow.class))
```

**Total:** ~30 lines, 1 new file, 1 file modified

**Option 4 Design:**

1. Add to `OperationRegistry.java`:
```java
// FHIRPath Spec: 6.4.X pow()
register("pow", List.of(
    new FunctionSignature(List.of(INTEGER, INTEGER), DECIMAL),
    new FunctionSignature(List.of(DECIMAL, DECIMAL), DECIMAL)
));
```

2. Add to `SparkCodeGenerator.java`:
```java
case "pow" -> functions.pow(args.get(0), args.get(1));
```

3. (Optional) Add to `SqlServerCodeGenerator.java`:
```java
case "pow" -> "POWER(" + args.get(0) + ", " + args.get(1) + ")";
```

**Total:** ~8 lines, 0 new files, 2-3 files modified

**Effort Reduction:** 75%

---

## 8. Risk Assessment

### 8.1 Technical Risks

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|------------|
| **Performance regression from visitor delegation** | Low | Medium | Benchmark at each phase; delegation is typically zero-cost |
| **Missed cases in operation dispatch** | Medium | High | Exhaustive testing; good error messages; runtime validation |
| **Debugging difficulty (generic Operation)** | Low | Low | Good toString(); IDE debugger works well with records |
| **Type safety loss (string-based dispatch)** | Low | Medium | Sealed interfaces; exhaustive switch; unit tests |
| **Migration takes longer than estimated** | Medium | Medium | Phased approach allows pause/reassess; incremental delivery |

### 8.2 Organizational Risks

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|------------|
| **Team unfamiliar with visitor pattern** | Medium | Low | Documentation; code review; pair programming |
| **Resistance to consolidating classes** | Low | Low | Show concrete benefits (easier to add functions, multi-target) |
| **Test updates take significant time** | Medium | Medium | Automate test updates where possible; prioritize critical tests |
| **Production issues during migration** | Low | High | Thorough testing; gradual rollout; feature flags; rollback plan |

### 8.3 Rollback Plan

Each phase has a clear rollback strategy:

- **Phase 1:** Revert commit (no breaking changes)
- **Phase 2:** Revert to Phase 1 (registry unused)
- **Phase 3:** Remove visitor code, keep eval() (backward compatible)
- **Phase 4:** Restore operation classes from git history

**Emergency Rollback:** If critical issue found in production:
1. Disable feature flag (if using phased rollout)
2. Revert to previous release
3. Hot-fix critical issue
4. Resume migration after fix

---

## 9. Discussion Points

### 9.1 Open Questions

1. **Should ComparisonOperator interface be kept?**
   - **Pro:** Provides good abstraction for 4 comparison classes
   - **Con:** Inconsistent with consolidated Operation approach
   - **Recommendation:** Consolidate into Operation for consistency

2. **Should infrastructure nodes (Count, Exists, Union) be consolidated too?**
   - **Pro:** Further reduces class count
   - **Con:** They have unique structure (no signatures)
   - **Recommendation:** Keep separate initially, consolidate later if beneficial

3. **How to handle operation-specific optimizations?**
   - Example: Constant folding `5 + 3` → `8`
   - **Option A:** Separate optimization visitor (IR → IR transformation)
   - **Option B:** In code generator (during evaluation)
   - **Recommendation:** Option A (more flexible, reusable across targets)

4. **Should signature be stored in all nodes or just Operation?**
   - **Current:** Only Operation stores signature
   - **Alternative:** Store in Literal, Traversal, etc. too
   - **Recommendation:** Only Operation (others have fixed types)

5. **How to handle target-specific features?**
   - Example: Spark UDFs, SQL Server CTEs
   - **Option A:** Extend Operation with target metadata
   - **Option B:** Target-specific IR node types
   - **Option C:** Handle in visitor (preferred)
   - **Recommendation:** Option C (keeps IR target-agnostic)

### 9.2 Alternative Approaches Considered

From ANALYSIS_01.md, we considered these alternatives:

1. **Option 1: Catalyst-Style Two-Phase** (ANALYSIS_01.md:53-113)
   - **Pros:** Clear separation, enables rule-based optimizations
   - **Cons:** High complexity, doubles node types
   - **Decision:** Defer until optimization needs are clear

2. **Option 2: Signature-Driven Only** (ANALYSIS_01.md:116-184)
   - **Pros:** Reduces classes, centralized registry
   - **Cons:** Still Spark-locked
   - **Decision:** Use as part of hybrid (this design)

3. **Option 3: Visitor Only** (ANALYSIS_01.md:186-257)
   - **Pros:** Multi-target support
   - **Cons:** Doesn't reduce class count
   - **Decision:** Use as part of hybrid (this design)

4. **Option 5: Intermediate IR with Lowering** (ANALYSIS_01.md:332-404)
   - **Pros:** Maximum flexibility for radically different targets
   - **Cons:** Overkill for SQL-like targets
   - **Decision:** Defer until non-SQL targets needed

### 9.3 Alignment with Industry Best Practices

This design aligns with established patterns:

- **LLVM:** Uses visitor pattern for multi-target code generation
- **Java Compiler:** Uses visitor (com.sun.source.tree.TreeVisitor) for AST processing
- **Spark Catalyst:** Uses similar registry for function resolution
- **SQL Optimizers:** Use visitor pattern for plan transformation
- **Compiler Textbooks:** Recommend visitor for multi-backend compilers

**Key References:**
- "Modern Compiler Implementation" by Andrew Appel (Chapter 7: Intermediate Representation)
- "Engineering a Compiler" by Cooper & Torczon (Chapter 5: IR Design)
- "Design Patterns" by Gang of Four (Visitor Pattern)

### 9.4 Success Metrics

After migration, we should measure:

**Quantitative:**
- Class count: 18 → 7 (61% reduction) ✓
- Type redundancy: 3 places → 1 place (67% reduction) ✓
- Lines of code: ~20% reduction ✓
- Test coverage: Maintained at current level ✓
- Performance: No regression ✓

**Qualitative:**
- Ease of adding new functions: Significantly improved ✓
- Code discoverability: Improved (centralized registry and visitor) ✓
- Multi-target readiness: Excellent (visitor per target) ✓
- Developer satisfaction: Survey team after migration

### 9.5 Next Steps for Discussion

1. **Review this document** with team and stakeholders
2. **Discuss open questions** (section 9.1) and make decisions
3. **Approve migration approach** or suggest modifications
4. **Assign ownership** for each phase
5. **Set timeline** for each phase (adjust 1-2 week estimates)
6. **Plan pilot** (pick 2-3 operations to migrate first)
7. **Schedule regular check-ins** during migration

---

## Appendix A: Code Examples

### A.1 Complete Example: `age + 5 where age > 18`

**FHIRPath Expression:**
```
Patient.age.where($this > 18) + 5
```

**AST:** (simplified)
```java
AstBinaryOperator("+",
    AstFunctionCall("where",
        target: AstTraversal(AstVariable("Patient"), "age"),
        args: [AstBinaryOperator(">", AstVariable("$this"), AstLiteral(18))]
    ),
    AstLiteral(5)
)
```

**IR Tree:**
```java
Operation("add",
    List.of(
        // Left: where(age, age > 18)
        // (where would be separate Operation or kept as specific class)

        // Right: 5
        Literal(5, INTEGER)
    ),
    biOperator(INTEGER)
)
```

**Spark Code Generation:**
```java
sparkCodeGen.visitOperation(add)
→ evaluateAdd([whereColumn, lit(5)], INTEGER)
→ whereColumn.plus(lit(5))
```

### A.2 Testing Example

**Test File:** `OperationTest.java`

```java
package com.example.fhirpath.ir;

import com.example.fhirpath.codegen.spark.SparkCodeGenerator;
import com.example.fhirpath.typing.Type;
import org.apache.spark.sql.Column;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.example.fhirpath.analyzer.FunctionSignature.biOperator;
import static org.junit.jupiter.api.Assertions.*;

class OperationTest extends IRNodeTestBase {

    @Test
    void testAddIntegersType() {
        IRNode left = new Literal(5, Type.INTEGER);
        IRNode right = new Literal(3, Type.INTEGER);
        Operation add = new Operation("add", List.of(left, right), biOperator(Type.INTEGER));

        assertEquals(Type.INTEGER, add.getType());
        assertEquals("add", add.name());
        assertEquals(2, add.arity());
    }

    @Test
    void testAddIntegersEvaluation() {
        IRNode left = new Literal(5, Type.INTEGER);
        IRNode right = new Literal(3, Type.INTEGER);
        Operation add = new Operation("add", List.of(left, right), biOperator(Type.INTEGER));

        Column result = add.accept(new com.example.fhirpath.codegen.spark.SparkCodeGenerator());

        // Execute with Spark and verify result
        Object value = evaluateColumn(result);
        assertEquals(8, value);
    }

    @Test
    void testStringConcatenation() {
        IRNode left = new Literal("Hello", Type.STRING);
        IRNode right = new Literal(" World", Type.STRING);
        Operation add = new Operation("add", List.of(left, right), biOperator(Type.STRING));

        Column result = add.accept(new SparkCodeGenerator());
        Object value = evaluateColumn(result);

        assertEquals("Hello World", value);
    }

    @Test
    void testNestedOperations() {
        // (5 + 3) * 2
        IRNode innerLeft = new Literal(5, Type.INTEGER);
        IRNode innerRight = new Literal(3, Type.INTEGER);
        Operation innerAdd = new Operation("add",
                List.of(innerLeft, innerRight),
                biOperator(Type.INTEGER));

        IRNode outerRight = new Literal(2, Type.INTEGER);
        Operation outerMul = new Operation("multiply",
                List.of(innerAdd, outerRight),
                biOperator(Type.INTEGER));

        Column result = outerMul.accept(new SparkCodeGenerator());
        Object value = evaluateColumn(result);

        assertEquals(16, value);  // (5 + 3) * 2 = 16
    }
}
```
**Document Status:** Ready for Review
**Next Step:** Team discussion and approval
**Questions:** Contact the architect or spark-expert agents for clarification