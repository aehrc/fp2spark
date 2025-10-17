prompt: Analyze the curent Type hierarchy type system in general and how it's being used by in AST->IR and code genration phases. In particular documen the current type adaptation rules in a consise notation,


# Type System Analysis

## Overview

This document analyzes the current type hierarchy, type system implementation, and type adaptation rules used throughout the FHIRPath-to-SQL compiler, specifically focusing on AST→IR transformation and code generation phases.

**Date**: October 16, 2025  
**Scope**: Type hierarchy, type resolution, and adaptation mechanisms

---

## Type Hierarchy

### Core Type Interface

```
Type (interface)
├── isPrimitive() : boolean
├── isComplex() : boolean
├── isCollection() : boolean
├── getName() : String
└── effectiveType() : Type
```

### Type Implementation Hierarchy

```
Type
├── PrimitiveType (enum)
│   ├── INTEGER
│   ├── DECIMAL
│   ├── QUANTITY
│   ├── DATE
│   ├── DATE_TIME
│   ├── TIME
│   ├── BOOLEAN
│   ├── STRING
│   ├── NULL (empty collection)
│   └── ANY (unknown/wildcard)
│
├── CollectionType(elementType: Type)
│   └── Flattening: Collection<Collection<T>> → Collection<T>
│
├── ComplexType(fields: Map<String, FieldSpec>)
│   └── ResourceType(name: String, fields) extends ComplexType
│
├── LambdaType(returnType: Type)
│   └── Used for lambda expressions in where(), select(), etc.
│
└── FhirType(systemType: PrimitiveType)
    └── Wrapper: FHIR types that map to system types
```

### Key Type Properties

- **effectiveType()**: Returns element type for `CollectionType`, self otherwise
- **CollectionType flattening**: Collections are automatically flattened (no nested collections)
- **NULL type**: Represents empty collections `{}`
- **ANY type**: Wildcard type for generic operations

---

## Type Adaptation Rules

### Notation

```
A ⇒ B         : A can be adapted to B (may insert Cast node)
A → B         : Direct cast available
A ≡ B         : Type equivalence
A ⊑ B         : A is subtype of B
[T]           : Collection<T>
T.eff         : effectiveType() of T
```

### Core Adaptation Rules (OverloadResolver.adapt)

#### 1. **Exact Match** (cost: 0)
```
A ⇒ B  if  A.eff ≡ B.eff
```

#### 2. **NULL Adaptation** (cost: 1)
```
NULL ⇒ B  always
// NULL is polymorphic - adapts to Literal(null, B)
// Special case: NULL.eff ≡ NULL ? NULL : B
```

#### 3. **ANY Wildcard** (cost: 1)
```
A ⇒ ANY  always
```

#### 4. **Collection Wildcard** (cost: 1-2)
```
NULL ⇒ [ANY]           // cost: 1
[T] ⇒ [ANY]            // cost: 1
T ⇒ [ANY]              // cost: 2 (implicit singleton)
```

#### 5. **Primitive Casts** (cost: 1)
```
INTEGER → DECIMAL
DECIMAL → QUANTITY
DATE → DATE_TIME

// Recursive for collections:
[A] → [B]  if  A → B
```

#### 6. **FHIR to System Type** (cost: 1)
```
FhirType(T) → T
// Inserts: Cast(CastToSystem(arg), T)
```

#### 7. **Lambda Type Matching** (cost: 0-1)
```
Lambda(bodyType: A) ⇒ LambdaType(returnType: B)
  if B ≡ ANY             // cost: 1 (wildcard)
  or A ≡ B               // cost: 0 (exact)
  or [ANY] ≡ B and A is collection  // cost: 1
  or A → B               // cost: 0 (castable)
```

**Lambda matching rules**:
- Lambda parameter types are **implicit** (bound via LambdaBindingStrategy)
- Only **return type** is checked for compatibility
- Lambda ⇏ non-LambdaType (always fails)
- Non-Lambda ⇏ LambdaType (always fails)

#### 8. **Adaptation Failures** (cost: ∞)
```
A ⇏ B  if  none of the above apply
```

---

## Type Resolution in AST→IR Phase

### Resolution Flow

```
AST Node → Analyzer.analyze() → IR Node + Type
         ↓
    desugar() (syntactic)
         ↓
    resolveToIR() (semantic)
         ↓
    OverloadResolver.resolveCall()
         ↓
    ResolvedSignature (concrete type)
```

### Type Resolution Strategies (ResultSpec)

The `ResultSpec` sealed interface defines 5 type resolution patterns:

#### 1. **Static** - Statically known result type
```
abs(Integer) → Integer
substring(String, Integer) → String
```

#### 2. **InputType** - Preserve input type structure
```
[T].where(λ) → [T]
T.methodName(...) → T
```

#### 3. **EffectiveInputType** - Extract element type
```
[T].first() → T
[T].item[n] → T
```

#### 4. **FhirSystemType** - FHIR to system type conversion
```
FhirType(STRING).getValue() → STRING
```

#### 5. **ArgumentType(index)** - Type from specific argument
```
iif(criterion: Lambda, trueResult: Lambda) → typeof(trueResult.body)
// Unwraps Lambda nodes automatically
```

### Lambda Binding Strategies

```java
enum LambdaBindingStrategy {
    ELEMENT_WISE,      // $this = elementType(target)
    COLLECTION_WISE    // $this = target type (entire collection)
}
```

**Usage**:
- `where()`: ELEMENT_WISE - `$this` bound to each element
- `iif()`: COLLECTION_WISE - `$this` bound to entire collection

---

## Type System Rules

### Casting Rules (TypeSystem.canCast)

```
// Identity
∀T: T → T

// NULL is universal
NULL → T  ∀T

// Primitive widening
INTEGER → DECIMAL → QUANTITY
DATE → DATE_TIME

// Collection element casting
[A] → [B]  iff  A → B

// FHIR unwrapping (handled in OverloadResolver)
FhirType(T) → T
```

### Collection Semantics

1. **Flattening**: `Collection<Collection<T>>` automatically becomes `Collection<T>`
2. **Empty collections**: Represented as `NULL` type
3. **Singleton promotion**: Scalars can implicitly match `Collection<ANY>` (cost: 2)
4. **effectiveType**: Returns element type for analysis

---

## IR Nodes for Type Adaptation

### Cast Node
```java
record Cast(IRNode child, Type targetType)
```
**Purpose**: Type conversion between compatible types
**Examples**:
- `INTEGER → DECIMAL`
- `DATE → DATE_TIME`
- `[INTEGER] → [DECIMAL]`

### CastToSystem Node
```java
record CastToSystem(IRNode child)
```
**Purpose**: Convert FHIR types to system types (getValue())
**Type**: `child.type = FhirType(T) → result.type = T`

### Adaptation Insertion Pattern
```
FhirType(T) → PrimitiveType(T)
  ⇒ Cast(CastToSystem(arg), T)
```

---

## Code Generation Phase

### Type Mapping (SparkTypeMapper)

```
FHIRPath Type     → Spark DataType
─────────────────────────────────────
INTEGER           → IntegerType
DECIMAL           → DecimalType(38, 6)
BOOLEAN           → BooleanType
STRING            → StringType
NULL              → NullType
[T]               → ArrayType(T)
```

### Collection Handling in Codegen

1. **Singularity checking**: `IRNode.isSingular()` determines collection vs scalar
2. **Collection operations**:
   - Singular: Direct value operations
   - Collection: Spark array functions (`filter`, `transform`, `size`, etc.)

3. **Traversal flattening**:
   ```
   Collection traversal → filter(nulls) + flatten (if multi-valued)
   ```

### Lambda Evaluation

**Pattern**:
```java
SparkCodeGenerator.withThisColumn(column)
  → Evaluate lambda body with $this bound
  → Returns Column expression
```

**Usage**:
- `where()`: `filter(collection, elem => lambdaGen.visit(lambdaBody))`
- `iif()`: Bind $this to entire collection, evaluate criterion and result

---

## Type Safety Guarantees

### Compile-Time Guarantees

1. **Statically typed IR**: Every `IRNode` has a known `Type`
2. **Type-checked operations**: `OverloadResolver` ensures argument compatibility
3. **Lambda return type checking**: Lambda body type must match expected return type
4. **No dynamic type errors**: All type errors detected during AST→IR transformation

### Type Adaptation Guarantees

1. **Adaptation or failure**: `OverloadResolver.adapt()` either:
   - Returns adapted node with cost
   - Returns failure (cost: ∞)
2. **Best match selection**: Lowest cost adaptation wins
3. **No ambiguity**: Lambda signatures cannot be overloaded (enforced)

---

## Type Adaptation Examples

### Example 1: Implicit Cast
```
FHIRPath: Patient.birthDate < @2000-01-01
Types:    DATE              DATE_TIME

Adaptation:
  birthDate: DATE
  → Cast(birthDate, DATE_TIME)  // cost: 1
  
Result: Cast(birthDate, DATE_TIME) < @2000-01-01
```

### Example 2: NULL Adaptation
```
FHIRPath: Patient.name.where(use = null)
Types:    [HumanName]      STRING = NULL

Adaptation:
  null: NULL
  → Literal(null, STRING)  // cost: 1
  
Result: where(λ: $this.use = Literal(null, STRING))
```

### Example 3: FHIR Type Conversion
```
FHIRPath: observation.value.getValue()
Types:    FhirType(DECIMAL) → getValue() → DECIMAL

IR:
  CastToSystem(observation.value) → DECIMAL
```

### Example 4: Lambda Type Matching
```
FHIRPath: [1, 2, 3].where($this > 2)
Signature: Collection<T>.where(λ: Lambda(Boolean)) → Collection<T>

Lambda analysis:
  $this: INTEGER (bound to element type)
  body: $this > 2
  body type: BOOLEAN
  expected: Lambda(BOOLEAN)
  ✓ Match (cost: 0)
```

### Example 5: Collection Wildcard
```
FHIRPath: combine([1, 2], ['a', 'b'])
Signature: combine(Collection<ANY>, Collection<ANY>) → Collection<ANY>

Adaptations:
  [INTEGER] ⇒ [ANY]  // cost: 1
  [STRING] ⇒ [ANY]   // cost: 1
  Total cost: 2
```

---

## Design Observations

### Strengths

1. **Clean separation**: Type hierarchy independent of IR structure
2. **Explicit adaptation**: All type conversions visible in IR as Cast nodes
3. **Cost-based resolution**: Best match selection via adaptation costs
4. **Lambda hygiene**: Parameter types implicit, only return type checked
5. **Collection flattening**: Prevents deeply nested collection types

### Areas for Consideration

1. **Limited cast rules**: Only 3 primitive casts currently defined
2. **FHIR type integration**: FhirType feels like a wrapper - could be more integrated
3. **ANY type usage**: Wildcard matching might hide type errors
4. **NULL semantics**: NULL as both "empty collection" and "null value"
5. **Singleton promotion**: Implicit scalar→collection has highest cost but allowed

---

## Summary: Type Adaptation Rules (Concise Notation)

```
# Exact Match (0)
A ⇒ B  :  A.eff ≡ B.eff

# NULL Polymorphism (1)
NULL ⇒ T  :  ∀T

# Wildcards (1-2)
T ⇒ ANY
T ⇒ [ANY]
NULL ⇒ [ANY]  (1)
[T] ⇒ [ANY]   (1)
T ⇒ [ANY]     (2, implicit singleton)

# Primitive Casts (1)
INTEGER → DECIMAL → QUANTITY
DATE → DATE_TIME
[A] → [B]  iff  A → B

# FHIR Conversion (1)
FhirType(T) → T  via Cast(CastToSystem(...), T)

# Lambda Matching (0-1)
Lambda(A) ⇒ LambdaType(B)  iff:
  - B ≡ ANY (1)
  - A ≡ B (0)
  - A → B (0)
  - B ≡ [ANY] ∧ A is collection (1)

# No Cross-Category Matching (∞)
Lambda ⇏ non-LambdaType
non-Lambda ⇏ LambdaType
```

---

## Conclusion

The type system implements a **statically-typed, cost-based type adaptation mechanism** with explicit IR nodes for all type conversions. The separation between type resolution (AST→IR) and code generation (IR→Spark) provides clean boundaries, with type safety guaranteed at IR construction time. The adaptation rules balance flexibility (wildcards, NULL polymorphism) with type safety (explicit casts, lambda return type checking).

