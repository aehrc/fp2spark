# Static Type System Design for FHIRPath

This document summarizes the discussion and example implementation of a **static type system for FHIRPath**, focusing on **element-first typing** and cardinality tracking, along with examples of how to define and type-check FHIRPath operations and functions.

---

## 1. Overview

A **type system** defines the rules by which expressions in a language are assigned types, describing what kinds of values they produce and what operations are valid on them.

FHIRPath, being a path-based expression language, typically has **dynamic typing**, but we can design a **static type system** to catch mismatched types at compile-time and provide better tooling and validation.

There are two conceptual approaches to typing in FHIRPath:

### 🟦 Collection-first typing
Each expression is considered as producing a **collection of elements** (like FHIRPath semantics).
- Type: `Collection<ElementType>`
- Example: `Observation.value` → `Collection<Quantity>`
- Cardinality is implicit in the collection (0..*).

### 🟩 Element-first typing
Each expression is described by **element type** and **cardinality** separately.
- Type: `(ElementType, Cardinality)`
- Example: `Observation.value` → `(Quantity, 0..*)`

This approach makes **cardinality reasoning explicit**, which is useful for static analysis and validation.

---

## 2. Element-first Type Representation

```java
public enum FhirElementType {
    BOOLEAN, INTEGER, DECIMAL, STRING, DATE, RESOURCE, UNKNOWN
}

public enum Cardinality {
    ZERO,        // 0..0
    OPTIONAL,    // 0..1
    SINGLE,      // 1..1
    COLLECTION;  // 0..*

    public static Cardinality combine(Cardinality a, Cardinality b) {
        if (a == ZERO) return b;
        if (b == ZERO) return a;
        if (a == COLLECTION || b == COLLECTION) return COLLECTION;
        if (a == OPTIONAL || b == OPTIONAL) return OPTIONAL;
        return SINGLE;
    }
}

public class FhirPathType {
    private final FhirElementType elementType;
    private final Cardinality cardinality;

    public FhirPathType(FhirElementType elementType, Cardinality cardinality) {
        this.elementType = elementType;
        this.cardinality = cardinality;
    }

    public FhirElementType getElementType() { return elementType; }
    public Cardinality getCardinality() { return cardinality; }

    @Override
    public String toString() {
        return elementType + "[" + cardinality + "]";
    }
}
```

---

## 3. Operator Example — `+`

A simple example of type inference for the `+` operator:

```java
public class FhirPathOperator {
    private final String symbol;

    public FhirPathOperator(String symbol) {
        this.symbol = symbol;
    }

    public FhirPathType inferResultType(FhirPathType left, FhirPathType right) {
        switch (symbol) {
            case "+":
                return addTypes(left, right);
            default:
                return new FhirPathType(FhirElementType.UNKNOWN, Cardinality.ZERO);
        }
    }

    private FhirPathType addTypes(FhirPathType left, FhirPathType right) {
        if (left.getElementType() != FhirElementType.INTEGER ||
            right.getElementType() != FhirElementType.INTEGER) {
            throw new IllegalArgumentException("Both operands must be Integers");
        }

        Cardinality resultCard = Cardinality.combine(left.getCardinality(), right.getCardinality());
        return new FhirPathType(FhirElementType.INTEGER, resultCard);
    }
}
```

### Example Usage

```java
FhirPathType singleInt = new FhirPathType(FhirElementType.INTEGER, Cardinality.SINGLE);
FhirPathType collectionInt = new FhirPathType(FhirElementType.INTEGER, Cardinality.COLLECTION);

FhirPathOperator plus = new FhirPathOperator("+");

System.out.println(plus.inferResultType(singleInt, singleInt));     // INTEGER[SINGLE]
System.out.println(plus.inferResultType(singleInt, collectionInt)); // INTEGER[COLLECTION]
```

---

## 4. Functions with Cardinality Constraints

Functions can define argument cardinality requirements and result cardinality logic.

```java
public interface FhirFunction {
    String getName();
    FhirPathType inferResultType(List<FhirPathType> args);
}
```

Example — `exists()` and `where()` (simplified for illustration).

### `exists()` function

```java
public class ExistsFunction implements FhirFunction {
    @Override
    public String getName() {
        return "exists";
    }

    @Override
    public FhirPathType inferResultType(List<FhirPathType> args) {
        if (args.size() != 1)
            throw new IllegalArgumentException("exists() takes one argument");

        FhirPathType input = args.get(0);
        return new FhirPathType(FhirElementType.BOOLEAN, Cardinality.SINGLE);
    }
}
```

### `where()` function with lambda argument

For `where()` the second argument is a lambda that operates on **each element** of the input collection.

```java
public class WhereFunction implements FhirFunction {
    @Override
    public String getName() { return "where"; }

    @Override
    public FhirPathType inferResultType(List<FhirPathType> args) {
        if (args.size() != 2)
            throw new IllegalArgumentException("where() takes two arguments");

        FhirPathType input = args.get(0);
        FhirPathType predicateResult = args.get(1);

        if (predicateResult.getElementType() != FhirElementType.BOOLEAN)
            throw new IllegalArgumentException("Predicate must return Boolean");

        return new FhirPathType(input.getElementType(), Cardinality.COLLECTION);
    }
}
```

---

## 5. Conditional Function — `iif()`

FHIRPath `iif(condition, then, else)` performs conditional evaluation.

```java
public class FhirTypeSystem {

    public static FhirElementType lub(FhirElementType a, FhirElementType b) {
        if (a == b) return a;
        if ((a == FhirElementType.INTEGER && b == FhirElementType.DECIMAL) ||
            (a == FhirElementType.DECIMAL && b == FhirElementType.INTEGER))
            return FhirElementType.DECIMAL;

        if (a == FhirElementType.RESOURCE || b == FhirElementType.RESOURCE)
            return FhirElementType.RESOURCE;

        return FhirElementType.UNKNOWN;
    }
}
```

```java
public class IifFunction implements FhirFunction {
    @Override
    public String getName() { return "iif"; }

    @Override
    public FhirPathType inferResultType(List<FhirPathType> args) {
        if (args.size() < 2 || args.size() > 3)
            throw new IllegalArgumentException("iif() requires 2 or 3 arguments");

        FhirPathType condition = args.get(0);
        FhirPathType thenType = args.get(1);
        FhirPathType elseType = args.size() == 3
                ? args.get(2)
                : new FhirPathType(FhirElementType.UNKNOWN, Cardinality.ZERO);

        if (condition.getElementType() != FhirElementType.BOOLEAN)
            throw new IllegalArgumentException("Condition must be Boolean");

        FhirElementType resultElement = FhirTypeSystem.lub(
                thenType.getElementType(), elseType.getElementType());
        Cardinality resultCard = Cardinality.combine(
                thenType.getCardinality(), elseType.getCardinality());

        return new FhirPathType(resultElement, resultCard);
    }
}
```

Example:

```java
FhirPathType condition = new FhirPathType(FhirElementType.BOOLEAN, Cardinality.SINGLE);
FhirPathType thenInt = new FhirPathType(FhirElementType.INTEGER, Cardinality.SINGLE);
FhirPathType elseDec = new FhirPathType(FhirElementType.DECIMAL, Cardinality.OPTIONAL);

IifFunction iif = new IifFunction();
FhirPathType result = iif.inferResultType(List.of(condition, thenInt, elseDec));

System.out.println(result); // DECIMAL[OPTIONAL]
```

---

## 6. Summary

| Concept | Description |
|----------|--------------|
| **Element-first typing** | Each expression’s type is represented as (elementType, cardinality). |
| **Cardinality** | Encodes 0..0, 0..1, 1..1, or 0..*; determines how results combine. |
| **Operator inference** | Defined as rules combining element types and cardinalities. |
| **Function inference** | Functions define argument requirements and resulting types. |
| **Lambda functions** | Represent element-wise transformations (`where()`, `select()`). |
| **Conditional typing** | `iif()` merges branch types via least-upper-bound (LUB). |

---

This prototype demonstrates how a **statically checkable type system** for FHIRPath could be designed and implemented in Java. It provides the foundation for:
- compile-time validation of expressions,  
- IDE support and tooling,  
- optimization opportunities for pre-validated FHIRPath expressions.

