# Code Generator Architecture Analysis

**Date**: 2025-01-22
**Scope**: `com.example.fhirpath.codegen.spark` package
**Purpose**: Analyze current design, identify issues, evaluate against SOLID principles

---

## Table of Contents

1. [Executive Summary](#executive-summary)
2. [Current Architecture](#current-architecture)
3. [Design Issues](#design-issues)
4. [SOLID Principle Evaluation](#solid-principle-evaluation)
5. [Metrics](#metrics)
6. [Recommendations Summary](#recommendations-summary)

---

## Executive Summary

The current code generator successfully implements FHIRPath to SparkSQL translation with clean separation between IR and code generation. However, the architecture exhibits several design issues that impact extensibility and maintainability:

**Key Strengths:**
- ✅ Clean visitor pattern separating IR from code generation
- ✅ Generic Operation IR node (eliminates 18+ operation classes)
- ✅ Type information resolved once during analysis
- ✅ Target independence maintained

**Critical Issues:**
- ❌ 56-line switch statement coupling operations to implementations
- ❌ Type-specific logic scattered across 15+ methods
- ❌ No strategy for choosing between direct mapping/complex expression/UDF
- ❌ Complex type wrappers (Quantity, DateTime) mostly empty stubs
- ❌ N×M complexity: operations × types

**Impact**: Adding new operations or types requires changes across multiple locations, violating Open/Closed Principle.

---

## Current Architecture

### Component Overview

```
┌─────────────────────────────────────────────────────────┐
│ SparkCodeGenerator (618 lines)                         │
│                                                          │
│  ┌────────────────────────────────────────────┐        │
│  │ visitOperation(Operation op)               │        │
│  │   ↓                                          │        │
│  │ evaluateOperation(name, args, type, nodes) │        │
│  │   ↓                                          │        │
│  │ switch(name) { // 56 lines, 35+ cases       │        │
│  │   case "add" → evaluateAdd()                │        │
│  │   case "sub" → evaluateSub()                │        │
│  │   case "multiply" → evaluateMultiply()      │        │
│  │   ...                                        │        │
│  │ }                                            │        │
│  └────────────────────────────────────────────┘        │
│                                                          │
│  ┌────────────────────────────────────────────┐        │
│  │ Type-Specific Evaluation Methods            │        │
│  │                                              │        │
│  │ evaluateAdd(args, resultType)               │        │
│  │   switch(resultType) {                      │        │
│  │     case INTEGER/DECIMAL → left.plus(right) │        │
│  │     case STRING → concat(left, right)       │        │
│  │     case DATE_TIME → dateTime(left).plus()  │        │
│  │     case QUANTITY → quantity(left).plus()   │        │
│  │   }                                          │        │
│  │                                              │        │
│  │ evaluateGreaterThan(args, inputType)        │        │
│  │   switch(inputType) { ... }                 │        │
│  │                                              │        │
│  │ ... (15+ similar methods)                   │        │
│  └────────────────────────────────────────────┘        │
└─────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────┐
│ Type Wrappers (Quantity, DateTime, Date, Time)         │
│                                                          │
│  record Quantity(Column target) {                       │
│    Column abs() { /* implemented */ }                   │
│    Column plus(Quantity q) { throw UnsupportedOp }     │
│    Column gt(Quantity q) { throw UnsupportedOp }       │
│    Column divide(Quantity q) { throw UnsupportedOp }   │
│  }                                                      │
│                                                          │
│  record DateTime(Column target) {                       │
│    Column gt(DateTime dt) { throw UnsupportedOp }      │
│    Column plus(Quantity q) { throw UnsupportedOp }     │
│  }                                                      │
│                                                          │
│  ... (similar for Date, Time)                           │
└─────────────────────────────────────────────────────────┘
```

### Code Generation Flow

**Step 1: IR Traversal**
```java
// SparkCodeGenerator.java:59-75
@Override
public Column visitOperation(@Nonnull Operation op) {
    // Recursively generate columns for arguments
    List<Column> argColumns = op.args().stream()
        .map(arg -> arg instanceof Lambda ? null : arg.accept(this))
        .toList();

    // Dispatch to operation evaluator
    return evaluateOperation(op.name(), argColumns, op.getType(), op.args());
}
```

**Step 2: Operation Dispatch** (Lines 80-139)
```java
private Column evaluateOperation(String name, List<Column> args,
                                  Type resultType, List<IRNode> argNodes) {
    return switch (name) {
        // Arithmetic (5 operations)
        case "add" -> evaluateAdd(args, resultType);
        case "sub" -> evaluateSub(args, resultType);
        case "multiply" -> evaluateMultiply(args, resultType);
        case "divide" -> evaluateDivide(args, resultType);
        case "mod" -> evaluateMod(args, resultType);

        // Comparison (4 operations)
        case "gt" -> evaluateGreaterThan(args, argNodes.get(0).getType());
        case "lt" -> evaluateLessThan(args, argNodes.get(0).getType());
        case "geq" -> evaluateGreaterEqual(args, argNodes.get(0).getType());
        case "leq" -> evaluateLessEqual(args, argNodes.get(0).getType());

        // Math functions (8 operations)
        case "abs" -> evaluateAbs(args, resultType);
        case "ceiling", "floor", "truncate", "exp", "ln", "log", "sqrt"...

        // String functions (9 operations)
        case "substring", "startsWith", "endsWith", "contains"...
        case "upper" -> upper(args.get(0));  // Direct mapping!
        case "lower" -> lower(args.get(0));  // Direct mapping!

        // Boolean operators (5 operations)
        case "and" -> args.get(0).and(args.get(1));  // Direct mapping!
        case "or" -> args.get(0).or(args.get(1));
        ...

        // Collection functions (4 operations)
        case "count", "exists", "empty", "first"...

        // Special operations
        case "where", "iif"...

        default -> throw new UnsupportedOperationException(...)
    };
}
```

**Step 3: Type-Specific Evaluation**
```java
// Example: evaluateAdd (Lines 143-156)
private Column evaluateAdd(List<Column> args, Type resultType) {
    Column left = args.get(0);
    Column right = args.get(1);

    return switch ((PrimitiveType) resultType) {
        case INTEGER, DECIMAL -> left.plus(right);           // Direct Spark
        case STRING -> concat(left, right);                  // Direct Spark
        case DATE_TIME -> dateTime(left).plus(quantity(right)); // Wrapper
        case QUANTITY -> quantity(left).plus(quantity(right));  // Wrapper (stub!)
        default -> throw new IllegalArgumentException(...)
    };
}

// Example: evaluateGreaterThan (Lines 204-220)
private Column evaluateGreaterThan(List<Column> args, Type inputType) {
    Column left = args.get(0);
    Column right = args.get(1);

    return switch ((PrimitiveType) inputType) {
        case INTEGER, DECIMAL, STRING -> left.gt(right);      // Direct Spark
        case QUANTITY -> quantity(left).gt(quantity(right));  // Wrapper (stub!)
        case DATE_TIME -> dateTime(left).gt(dateTime(right)); // Wrapper (stub!)
        case DATE -> date(left).gt(date(right));              // Wrapper (stub!)
        case TIME -> time(left).gt(time(right));              // Wrapper (stub!)
        default -> throw new IllegalArgumentException(...)
    };
}
```

### Type Wrapper Implementation

**Current State**: Mostly empty stubs

```java
// Quantity.java:20-79
public record Quantity(@Nonnull Column target) {
    // IMPLEMENTED: Only abs() has actual logic
    @Nonnull
    public Column abs() {
        return functions.struct(
            functions.abs(value()).alias("value"),
            unit().alias("unit"),
            system().alias("system"),
            code().alias("code")
        );
    }

    // STUBS: All other operations throw exceptions
    public Column gt(@Nonnull final Quantity quantity) {
        throw new UnsupportedOperationException("Not supported yet: Quantity::gt");
    }

    public Column plus(Quantity quantity) {
        throw new UnsupportedOperationException("Not supported yet: Quantity::plus");
    }

    public Column divide(Quantity quantity) {
        throw new UnsupportedOperationException("Not supported yet: Quantity::divide");
    }

    // ... similar stubs for lt, geq, etc.
}
```

**Pattern**: All type wrappers (DateTime, Date, Time) follow same stub pattern.

---

## Design Issues

### Issue 1: Tight Coupling via Switch Statement

**Location**: `SparkCodeGenerator.java:82-138` (56 lines)

**Problem**: Central switch statement creates tight coupling between operation names and implementation methods.

**Evidence**:
```java
case "add" -> evaluateAdd(args, resultType);
case "sub" -> evaluateSub(args, resultType);
case "multiply" -> evaluateMultiply(args, resultType);
// ... 32+ more cases
```

**Impact**:
- ❌ **Violates Open/Closed Principle**: Every new operation requires modifying this switch
- ❌ **High cyclomatic complexity**: 35+ branches in single method
- ❌ **Difficult to test**: Cannot test operation dispatch in isolation
- ❌ **Poor extensibility**: No way to add operations without editing core class

**Example**: Adding a new operation `round()` requires:
1. Update `OperationRegistry` (1 line)
2. Add case to switch (1 line)
3. Implement `evaluateRound()` method (5-20 lines)
4. Update type wrappers if needed (0-4 files)

### Issue 2: Type-Specific Logic Fragmentation

**Location**: Scattered across 15+ evaluate methods

**Problem**: Similar type-switching logic repeated in multiple methods.

**Evidence**:
```java
// Pattern repeated in: evaluateAdd, evaluateSub, evaluateMultiply,
// evaluateDivide, evaluateAbs, evaluateGreaterThan, evaluateLessThan, etc.

switch ((PrimitiveType) type) {
    case INTEGER, DECIMAL -> /* simple Spark operation */
    case STRING -> /* string-specific logic */
    case QUANTITY -> quantity(...).operation(...)  // Often stub!
    case DATE_TIME -> dateTime(...).operation(...) // Often stub!
    case DATE -> date(...).operation(...)          // Often stub!
    case TIME -> time(...).operation(...)          // Often stub!
}
```

**Counts**:
- `evaluateAdd`: 5 type cases
- `evaluateGreaterThan`: 6 type cases
- `evaluateLessThan`: 6 type cases
- `evaluateGreaterEqual`: 6 type cases
- `evaluateLessEqual`: 6 type cases
- Total: **40+ type switches** across methods

**Impact**:
- ❌ **N×M Complexity**: N operations × M types = fragmentation
- ❌ **Code duplication**: Type switching logic repeated
- ❌ **Difficult to find**: "Where is Quantity addition implemented?" → Search through evaluateAdd
- ❌ **Hard to extend**: Adding new type requires updating all relevant evaluate methods

### Issue 3: No Implementation Strategy Pattern

**Problem**: No explicit strategy for choosing between:
1. **Direct mapping**: `upper(args.get(0))` → single SparkSQL function
2. **Complex expression**: `evaluateSubstring()` → multi-step logic with null handling
3. **Type wrapper**: `quantity(left).plus(quantity(right))` → delegated to wrapper
4. **UDF**: No infrastructure exists

**Evidence**:

**Case A: Direct Mapping** (Lines 111-113)
```java
case "upper" -> upper(args.get(0));
case "lower" -> lower(args.get(0));
case "replace" -> regexp_replace(args.get(0), args.get(1), args.get(2));
```

**Case B: Complex Expression** (Lines 348-369)
```java
private Column evaluateSubstring(List<Column> args) {
    final Column targetColumn = args.get(0);
    final Column posColumn = args.get(1).plus(lit(1));  // 0-based → 1-based
    final Column nonNullLengthColumn = coalesce(args.get(2), lit(Integer.MAX_VALUE));

    // FHIRPath null propagation
    final Column nullPropagationCondition = targetColumn.isNull()
        .or(posColumn.isNull());
    final Column posOutOfBoundsCondition = posColumn.leq(0)
        .or(posColumn.gt(length(targetColumn)));
    final Column nullCondition = nullPropagationCondition
        .or(posOutOfBoundsCondition);

    return when(not(nullCondition),
        substr(targetColumn, posColumn, nonNullLengthColumn));
}
```

**Case C: Type Wrapper Delegation** (Lines 144-155)
```java
private Column evaluateAdd(List<Column> args, Type resultType) {
    return switch ((PrimitiveType) resultType) {
        case INTEGER, DECIMAL -> left.plus(right);            // Direct
        case STRING -> concat(left, right);                   // Direct
        case DATE_TIME -> dateTime(left).plus(quantity(right)); // Wrapper
        case QUANTITY -> quantity(left).plus(quantity(right));  // Wrapper (STUB!)
        default -> throw new IllegalArgumentException(...)
    };
}
```

**Case D: UDF** (No examples - infrastructure missing!)
```java
// Comment in Quantity.java:23
public Column abs() {
    // either call a UDF or construct the expression
    return functions.struct(...);  // Currently: inline expression
}
```

**Impact**:
- ❌ **Ad-hoc decisions**: No clear criteria for choosing strategy
- ❌ **Inconsistent patterns**: Some operations inline, some delegate
- ❌ **No UDF support**: Complex types can't use UDFs (performance impact)
- ❌ **Unclear responsibility**: Should logic be in SparkCodeGenerator or wrappers?

### Issue 4: Empty Type Wrapper Stubs

**Problem**: Type wrappers (Quantity, DateTime, Date, Time) are mostly unimplemented.

**Statistics**:

| Wrapper   | Total Methods | Implemented | Stubbed | % Complete |
|-----------|---------------|-------------|---------|------------|
| Quantity  | 7             | 1 (abs)     | 6       | 14%        |
| DateTime  | 4             | 0           | 4       | 0%         |
| Date      | 3             | 0           | 3       | 0%         |
| Time      | 3             | 0           | 3       | 0%         |
| **Total** | **17**        | **1**       | **16**  | **6%**     |

**Evidence**: Every stub follows same pattern:
```java
public Column operation(...) {
    throw new UnsupportedOperationException("Not supported yet: Type::operation");
}
```

**Impact**:
- ❌ **Runtime failures**: Operations compile but fail at runtime
- ❌ **Incomplete feature**: Quantities, DateTimes can't be compared or manipulated
- ❌ **False sense of security**: Type wrappers exist but don't work
- ❌ **Unclear roadmap**: No plan for implementing stubs

### Issue 5: No UDF Infrastructure

**Problem**: No mechanism for registering, discovering, or using Spark UDFs.

**Requirements for UDF Support**:
1. ✅ UDF implementation (user code)
2. ❌ UDF registration with Spark
3. ❌ UDF discovery/lookup mechanism
4. ❌ Strategy for choosing UDF vs inline expression
5. ❌ UDF lifecycle management
6. ❌ Testing infrastructure for UDFs

**Impact on Complex Types**:

**Quantity Arithmetic** requires:
- Unit conversion (e.g., mg → g, km → m)
- UCUM standard compliance
- Precision handling

**Options**:
1. ❌ **Inline SQL**: Extremely complex, unreadable
2. ✅ **UDF**: Clean, testable, reusable
3. ❌ **Current**: Stub (throws exception)

**DateTime Operations** require:
- Precision handling (year, month, day, hour, minute, second)
- Timezone conversions
- Calendar arithmetic

**Options**:
1. ⚠️ **Spark Date Functions**: Limited precision support
2. ✅ **UDF**: Full FHIRPath semantics
3. ❌ **Current**: Stub (throws exception)

**Impact**:
- ❌ **Incomplete implementation**: Complex types unusable
- ❌ **Performance**: Cannot leverage Spark UDF optimizations
- ❌ **Correctness**: Can't implement FHIRPath spec accurately without UDFs

### Issue 6: Extension Point Ambiguity

**Problem**: Unclear where to add new functionality.

**Scenario**: Implement Quantity addition with unit conversion.

**Option 1: Inline in SparkCodeGenerator**
```java
// SparkCodeGenerator.evaluateAdd
case QUANTITY -> {
    Column leftValue = args.get(0).getField("value");
    Column leftUnit = args.get(0).getField("unit");
    Column rightValue = args.get(1).getField("value");
    Column rightUnit = args.get(1).getField("unit");

    // Unit conversion logic (20+ lines)...
    // UCUM normalization...
    // Value arithmetic...

    return functions.struct(...);
}
```
❌ **Problems**: Clutters evaluateAdd, mixes concerns, hard to test

**Option 2: Implement in Quantity wrapper**
```java
// Quantity.java
public Column plus(Quantity other) {
    // Unit conversion logic...
    return functions.struct(...);
}
```
✅ **Better**: Cohesive, testable
❌ **But**: Still no UDF support, logic hard to share

**Option 3: Extract to UDF**
```java
// No infrastructure exists!
// Would need: QuantityUDFs class, registration mechanism, discovery
```
✅ **Ideal**: Clean, performant, testable
❌ **Blocked**: No UDF infrastructure

**Impact**:
- ❌ **Inconsistent implementations**: Developers choose different approaches
- ❌ **Hard to review**: No clear pattern to verify
- ❌ **Technical debt**: Quick fixes in wrong places

---

## SOLID Principle Evaluation

### Single Responsibility Principle (SRP)

**Grade: ⚠️ MODERATE VIOLATION**

**SparkCodeGenerator Responsibilities**:
1. ✅ Traverse IR tree
2. ✅ Coordinate code generation
3. ❌ Dispatch operations (should be registry)
4. ❌ Implement type-specific logic (should be handlers)
5. ❌ Handle complex expressions (should be strategies)

**Evidence**: 618-line class with multiple concerns:
- IR traversal (visitOperation, visitLiteral, visitTraversal, etc.)
- Operation dispatch (evaluateOperation switch)
- Type-specific evaluation (15+ evaluate methods)
- Complex expression logic (evaluateSubstring, evaluateWhere, etc.)

**Recommendation**: Extract operation dispatch and type-specific logic.

### Open/Closed Principle (OCP)

**Grade: ❌ STRONG VIOLATION**

**Problem**: Cannot extend without modification.

**Evidence**:
- Adding operation → **Modify** switch statement (line 82)
- Adding type support → **Modify** multiple evaluate methods
- Adding UDF → **No extension point exists**

**Test**: Can we add support for `Duration` type without editing existing classes?
- ❌ **No**: Must modify switch statements in evaluateAdd, evaluateGreaterThan, etc.

**Recommendation**: Use registry + strategy pattern for extensibility.

### Liskov Substitution Principle (LSP)

**Grade: ✅ NO VIOLATION**

Type wrappers (Quantity, DateTime) are simple records, not polymorphic hierarchies. LSP not applicable.

### Interface Segregation Principle (ISP)

**Grade: ⚠️ MINOR VIOLATION**

**Issue**: IRNodeVisitor forces implementation of all visit methods.

**Current**:
```java
interface IRNodeVisitor<T> {
    T visitOperation(Operation op);
    T visitLiteral(Literal lit);
    T visitTraversal(Traversal trav);
    T visitCast(Cast cast);
    T visitResource(Resource res);
    T visitCastToSystem(CastToSystem cast);
    T visitUnion(Union union);
    T visitEquals(Equals equals);
    T visitLambda(Lambda lambda);
    T visitThisReference(ThisReference ref);
}
```

**Impact**: Minimal - all methods needed for complete code generation.

### Dependency Inversion Principle (DIP)

**Grade: ⚠️ MODERATE VIOLATION**

**Problem**: SparkCodeGenerator depends on concrete type wrappers.

**Evidence**:
```java
import static com.example.fhirpath.codegen.spark.Quantity.quantity;
import static com.example.fhirpath.codegen.spark.DateTime.dateTime;
import static com.example.fhirpath.codegen.spark.Date.date;
import static com.example.fhirpath.codegen.spark.Time.time;
```

**Recommended**: Depend on abstractions (e.g., `TypeHandler` interface).

---

## Metrics

### Complexity Metrics

| Metric                          | Value | Threshold | Status |
|---------------------------------|-------|-----------|--------|
| Lines of Code (SparkCodeGenerator) | 618   | < 400     | ⚠️     |
| Cyclomatic Complexity (evaluateOperation) | 35+ | < 10 | ❌     |
| Method Count (SparkCodeGenerator) | 30+   | < 25      | ⚠️     |
| Average Method Length           | 20    | < 15      | ⚠️     |
| Type Switches per Method (avg)  | 2.5   | < 1       | ❌     |

### Code Duplication

| Pattern                        | Occurrences | LOC | Impact |
|--------------------------------|-------------|-----|--------|
| Type switch (QUANTITY/DATE...) | 15+         | 120 | HIGH   |
| Wrapper delegation pattern     | 20+         | 40  | MEDIUM |
| Null handling                  | 10+         | 30  | MEDIUM |

### Stub Analysis

| Category           | Declared | Implemented | Stubbed | % Complete |
|--------------------|----------|-------------|---------|------------|
| Type Wrapper Methods | 17       | 1           | 16      | 6%         |
| Complex Operations   | 35       | 22          | 13      | 63%        |
| **Total**           | **52**   | **23**      | **29**  | **44%**    |

**Critical Gaps**:
- ❌ Quantity arithmetic (5 operations stubbed)
- ❌ DateTime comparison (4 operations stubbed)
- ❌ Date comparison (3 operations stubbed)
- ❌ Time comparison (3 operations stubbed)

---

## Recommendations Summary

### Priority 1: High Impact, Low Risk

1. **Extract Operation Registry**
   - Replace switch statement with registry lookup
   - Estimated effort: 2-3 hours
   - Benefit: Eliminates central coupling point

2. **Define Type Handler Interface**
   - Create abstraction for type-specific logic
   - Estimated effort: 1-2 hours
   - Benefit: Clear extension point for new types

### Priority 2: High Impact, Medium Risk

3. **Implement Strategy Pattern for Operation Dispatch**
   - Separate DirectMapping, ComplexExpression, UDF strategies
   - Estimated effort: 4-6 hours
   - Benefit: Clear implementation choices, better testability

4. **Consolidate Type-Specific Logic**
   - Move scattered type switches into dedicated handlers
   - Estimated effort: 6-8 hours
   - Benefit: Cohesive type logic, easier to maintain

### Priority 3: Medium Impact, High Value

5. **Build UDF Infrastructure**
   - UDF registration, discovery, lifecycle management
   - Estimated effort: 8-12 hours
   - Benefit: Enables complex type operations

6. **Implement Complex Type Operations**
   - Complete Quantity, DateTime, Date, Time wrappers
   - Estimated effort: 12-16 hours
   - Benefit: Full FHIRPath feature support

---

## Next Steps

See **CODEGEN_REFACTORING_PROPOSAL.md** for:
- Detailed architectural design
- Code examples for proposed abstractions
- Step-by-step migration plan
- Before/after comparisons
