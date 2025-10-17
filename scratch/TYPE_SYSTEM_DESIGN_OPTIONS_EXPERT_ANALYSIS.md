# Expert Type System Design Options for FHIRPath

**Date**: October 17, 2025  
**Context**: Expert language design analysis of type system alternatives for FHIRPath, specifically addressing the complexity of tracking cardinality (singleton vs. collection) and adaptation rules.

---

## Background

The current type system implementation uses `CollectionType[T]` to explicitly track whether a value is a singleton (0..1) or a collection (0..*). This design choice has led to:

- Complex adaptation rules (element promotion, collection wildcards)
- Signature explosion (need both `T` and `[T]` variants)
- Type variable complexity (capture types needed for polymorphism)
- Multiple wildcard types (`ANY`, `[ANY]`, `Lambda[ANY]`)

**Core Question**: Is explicitly tracking cardinality in the type system the right approach, or are there better alternatives used in established programming language design?

---

## Current Type System Analysis

### Type Hierarchy (As Implemented)

```
Type
├── OrdinaryType (non-functional types)
│   ├── ValueType (non-collection types)
│   │   ├── PrimitiveType: STRING, BOOLEAN, INTEGER, DECIMAL, etc.
│   │   ├── ComplexType (structured types with fields)
│   │   ├── ResourceType (extends ComplexType)
│   │   └── FhirType (FHIR-specific wrapper types)
│   ├── CollectionType[ValueType|ANY]
│   └── NULL (empty collection)
├── Lambda[OrdinaryType|ANY] (functional type)
└── ANY (wildcard matching both ValueType and CollectionType)
```

### Current Adaptation Rules

```
Identity:         T ⇒ T                           (cost: 0)
NULL Polymorphism: NULL ⇒ T                       (cost: 0)
Wildcard:         T ⇒ ANY, [T] ⇒ ANY             (cost: 0)
Element Promotion: T ⇒ [T]                        (cost: 0) ✓ FREE
Primitive Cast:   INTEGER ⇒ DECIMAL               (cost: 1)
FHIR Unwrap:      FhirType(T) ⇒ T                (cost: 1)
Lambda Covariance: Lambda[A] ⇒ Lambda[B]          (cost: cost(A⇒B))
Collection Adapt:  [T] ⇒ [S] if T ⇒ S           (cost: cost(T⇒S))
Cross-Category:    Lambda[T] ⇔ OrdinaryType       (cost: ∞)
```

### Implementation Details

```java
// CollectionType wrapper (with flattening)
record CollectionType(Type elementType) implements Type {
    public CollectionType(Type elementType) {
        // Flatten: Collection<Collection<T>> becomes Collection<T>
        if (elementType instanceof CollectionType ct) {
            this.elementType = ct.elementType();
        } else {
            this.elementType = elementType;
        }
    }
    
    @Override
    public Type effectiveType() {
        return elementType;
    }
}

// Lambda handling (correct design)
record LambdaType(Type returnType) implements Type {}  // Signature constraint
record Lambda(IRNode body) implements IRNode {}         // Runtime value

// OverloadResolver matches Lambda nodes against LambdaType signatures
```

**Assessment**: The current design is solid and well-thought-out. The key question is whether the complexity introduced by explicit `CollectionType` tracking is worth the benefits.

---

## Alternative Type System Designs

### Option 1: Cardinality-Agnostic Types (FHIRPath Native)

**Core Insight**: In FHIRPath, **everything is a collection**. There's no semantic difference between `5` and `{5}`.

#### Type Hierarchy

```
Type
├── ValueType (base semantic types)
│   ├── PrimitiveType: INTEGER, DECIMAL, STRING, BOOLEAN, DATE, etc.
│   ├── ComplexType: Patient, Observation, HumanName, etc.
│   ├── FhirType(systemType: ValueType)
│   └── ANY (wildcard)
├── LambdaType(returnType: ValueType)
└── NULL (empty collection)
```

**Key Change**: **No `CollectionType` wrapper**. Cardinality is tracked separately from type.

#### Cardinality Tracking

Track cardinality as **metadata** on IR nodes, not as part of the type system:

```java
interface IRNode {
    Type getType();           // Semantic type (what kind of value)
    Cardinality getCardinality();  // How many values (SINGLE or MANY)
}

enum Cardinality {
    SINGLE,  // 0..1 (singleton or empty)
    MANY     // 0..* (collection)
}

// Examples:
Literal(5, INTEGER) -> type=INTEGER, card=SINGLE
patient.name -> type=HumanName, card=MANY (repeating field)
patient.birthDate -> type=DATE, card=SINGLE (non-repeating field)
```

#### Adaptation Rules (Dramatically Simplified)

```
Identity:          T ⇒ T                    (cost: 0)
NULL:              NULL ⇒ T                 (cost: 0)
Wildcard:          T ⇒ ANY                  (cost: 0)
Primitive Cast:    INTEGER ⇒ DECIMAL        (cost: 1)
FHIR Unwrap:       FhirType(T) ⇒ T         (cost: 1)
Lambda Covariance: Lambda[A] ⇒ Lambda[B]   (cost: cost(A⇒B))
```

**No element promotion rule needed!** All operations implicitly work on collections.

#### Cardinality Inference Rules

```java
// Literals are always SINGLE
new Literal(5, INTEGER) -> SINGLE

// Field access inherits field cardinality from schema
patient.name -> MANY (name is 0..*)
patient.birthDate -> SINGLE (birthDate is 0..1)

// Operations define result cardinality
union(a, b) -> MANY (always produces collection)
where(coll, lambda) -> MANY (filters collection)
first(coll) -> SINGLE (extracts one element)
count(coll) -> SINGLE (scalar result)
```

#### Signature Definition

```java
signature("in",
    param(ANY, SINGLE),      // element (singleton)
    param(ANY, MANY),        // collection
    result(BOOLEAN, SINGLE)
)

signature("union",
    param(typeVar("T"), MANY),
    param(typeVar("T"), MANY),
    result(typeVar("T"), MANY)  // always returns collection
)

signature("first",
    param(ANY, MANY),
    result(ANY, SINGLE)       // always returns 0..1
)

signature("where",
    param(typeVar("T"), MANY),
    param(LambdaType(BOOLEAN)),
    result(typeVar("T"), MANY)  // preserves input type, returns collection
)
```

#### Benefits

✅ **Simplest type algebra**: Only semantic types, no wrapper  
✅ **Natural for FHIRPath**: Matches language semantics (everything is a collection)  
✅ **No element promotion complexity**: Operations just work on collections  
✅ **Fewer adaptation rules**: ~50% reduction in rule complexity  
✅ **Type variables easy**: `typeVar("T")` without collection variants  
✅ **Easier to implement**: Less code, fewer edge cases  

#### Drawbacks

❌ **Cardinality separate**: Need to track it alongside types  
❌ **Two-attribute tracking**: `(Type, Cardinality)` pairs everywhere  
❌ **Constraint expression**: Harder to say "must be singleton" in signatures (though we added it to param)  

#### Example Resolution

```fhirpath
2 in (2.0 | 3.2)
```

**Resolution:**
1. `2` → type=INTEGER, card=SINGLE
2. `(2.0 | 3.2)` → type=DECIMAL, card=MANY
3. Signature: `in(ANY:SINGLE, ANY:MANY) -> BOOLEAN:SINGLE`
4. Type matching: `INTEGER ⇒ ANY` (cost 0), `DECIMAL ⇒ ANY` (cost 0)
5. Cardinality matching: `SINGLE == SINGLE` ✓, `MANY == MANY` ✓
6. **But wait**: We need common type for correct semantics!
7. With type variables: `in(X:SINGLE, X:MANY) -> BOOLEAN:SINGLE`
8. Find common type: `commonType(INTEGER, DECIMAL) = DECIMAL`
9. Adapt: `cast(2, DECIMAL)` (cost 1)
10. Result: `in(cast(2, DECIMAL), [2.0, 3.2]) -> BOOLEAN`

**This still requires type variables or common type computation!**

---

### Option 2: Hybrid with Optional Cardinality Bounds

Keep explicit collection tracking but add **optional cardinality constraints**:

#### Type Hierarchy

```
Type
├── GroundType (concrete types)
│   ├── PrimitiveType
│   ├── ComplexType  
│   ├── FhirType(systemType)
│   └── NULL
├── CollectionType(elementType: GroundType, card: CardinalityBound)
├── LambdaType(returnType: Type)
└── ANY (matches any GroundType or CollectionType)
```

#### Cardinality Bounds (Optional Precision)

```java
sealed interface CardinalityBound {
    record Exactly(int n) implements CardinalityBound {}      // [T]{5}
    record Range(int min, int max) implements CardinalityBound {} // [T]{1..3}
    record AtLeast(int min) implements CardinalityBound {}    // [T]{1..*}
    record Unbounded() implements CardinalityBound {}         // [T] (default)
}

// Examples:
CollectionType(INTEGER, Unbounded())        // [INTEGER] - any count
CollectionType(STRING, AtLeast(1))          // [STRING]{1..*} - non-empty
CollectionType(DATE, Exactly(1))            // [DATE]{1} - exactly one
```

#### Adaptation Rules

```
T ⇒ [T]                (free, cost: 0) - singleton promotion
[T]{n} ⇒ [T]{m}        (free if n ⊆ m, cost: 0) - widening
T ⇒ S                  (if cast exists, cost: 1)
[T] ⇒ [S]              (if T ⇒ S, cost: cost(T⇒S))
```

#### Benefits

✅ **Precise cardinality**: Can express "exactly 1" vs "1 or more"  
✅ **Optional**: Can use simple `[T]` when precision not needed  
✅ **Type safety**: Catches cardinality errors at compile time  

#### Drawbacks

❌ **Complexity**: More complex than current system  
❌ **Overkill**: FHIRPath rarely needs this precision  
❌ **Implementation burden**: More cases to handle  

#### Assessment

**Too complex for FHIRPath's needs.** The language only distinguishes singleton (0..1) vs collection (0..*), not arbitrary cardinality bounds.

---

### Option 3: Type + Constraint System (Recommended)

**Separate types from validation constraints:**

#### Type System (Simple)

```
Type (sealed interface)
├── PrimitiveType (enum): INTEGER, DECIMAL, STRING, BOOLEAN, etc.
├── ComplexType (record): name, fields
├── FhirType (record): systemType
├── LambdaType (record): returnType
├── TypeVariable (record): name  // NEW for polymorphism
├── NULL (singleton)
└── ANY (singleton)
```

**No `CollectionType` in type system!** Types represent **semantic categories** only.

#### Constraint Language (Separate)

```java
sealed interface Constraint {
    record IsSingleton(Type type) implements Constraint {}
    record IsCollection(Type type) implements Constraint {}
    record HasCardinality(Type type, int min, int max) implements Constraint {}
    record And(List<Constraint> constraints) implements Constraint {}
    record Or(List<Constraint> constraints) implements Constraint {}
}
```

#### Usage: Two-Phase Checking

**Phase 1: Type Resolution (Simple)**

```java
Type resolvedType = adapt(actualType, expectedType);
// Only checks semantic types: INTEGER, STRING, etc.
// No cardinality involved
```

**Phase 2: Constraint Validation (Optional)**

```java
validate(node, IsSingleton(INTEGER));  // requires exactly one int
validate(node, HasCardinality(STRING, 0, 5));  // 0-5 strings
```

#### Adaptation Rules (Very Simple)

```
Identity:       T ⇒ T                (cost: 0)
NULL:           NULL ⇒ T             (cost: 0)
Wildcard:       T ⇒ ANY              (cost: 0)
TypeVar:        T ⇒ TypeVariable     (cost: 0, unifies)
Cast:           INTEGER ⇒ DECIMAL    (cost: 1)
FHIR Unwrap:    FhirType(T) ⇒ T     (cost: 1)
Lambda Covar:   Lambda[A] ⇒ Lambda[B] (cost: cost(A⇒B))
```

**That's it!** No element promotion, no collection element adaptation, no collection wildcards.

#### Signature Definition with Constraints

```java
signature("in", 
    param(typeVar("X"), constraint: IsSingleton()),     // single value
    param(typeVar("X"), constraint: IsCollection()),    // collection
    result(BOOLEAN, constraint: IsSingleton())
)

signature("union",
    param(typeVar("T"), constraint: IsCollection()),
    param(typeVar("T"), constraint: IsCollection()),
    result(typeVar("T"), constraint: IsCollection())
)

signature("first",
    param(ANY, constraint: IsCollection()),
    result(ANY, constraint: IsSingleton())  // extracts one element
)
```

#### How Cardinality is Tracked

**Metadata on IR nodes** (like Option 1):

```java
interface IRNode {
    Type getType();
    Cardinality getCardinality();
}

enum Cardinality {
    SINGLE,  // 0..1
    MANY     // 0..*
}
```

**Cardinality inference rules** (same as Option 1):

```java
// Literals
Literal(5, INTEGER) -> type=INTEGER, card=SINGLE

// Field access (from schema)
patient.name -> type=HumanName, card=MANY
patient.birthDate -> type=DATE, card=SINGLE

// Operations (from signature)
union(a, b) -> card=MANY (signature specifies IsCollection result)
first(coll) -> card=SINGLE (signature specifies IsSingleton result)
where(coll, f) -> card=MANY (preserves collection)
```

#### Benefits

✅ **Simplest type system**: No collection wrapper, minimal rules  
✅ **Clear separation**: Types vs. validation  
✅ **Flexible constraints**: Can add without changing type algebra  
✅ **Type variables**: Natural support for polymorphism  
✅ **Incremental adoption**: Can start with just types, add constraints later  
✅ **Matches FHIRPath**: Cardinality is metadata, not a fundamental type distinction  

#### Drawbacks

❌ **Two-phase checking**: Types then constraints (but this is actually cleaner!)  
❌ **More machinery**: Need constraint system (but simpler overall)  

#### Example Resolution

```fhirpath
2 in (2.0 | 3.2)
```

**Type Resolution:**
1. `2` → type=INTEGER, card=SINGLE
2. `(2.0 | 3.2)` → type=DECIMAL, card=MANY
3. Signature: `in(typeVar("X"), typeVar("X")) -> BOOLEAN`
4. Unify: `X = commonType(INTEGER, DECIMAL) = DECIMAL`
5. Adapt: `cast(2, DECIMAL)` (cost 1)
6. Result type: BOOLEAN
7. Result cardinality: SINGLE (from signature constraint)

**Constraint Checking:**
1. Param 1 constraint: `IsSingleton(DECIMAL)` ✓ (card=SINGLE)
2. Param 2 constraint: `IsCollection(DECIMAL)` ✓ (card=MANY)
3. All constraints satisfied ✓

---

### Option 4: Subtyping with Bounded Quantification (Academic)

Use formal **subtyping relation** instead of adaptation:

#### Type System

```
Type
├── PrimitiveType
├── ComplexType
├── FhirType
├── ForAll(TypeVar, Bound, Body)  // ∀T. Body
├── TypeVariable
└── ...
```

#### Subtyping Rules

```
Reflexive:     T <: T
Transitive:    T <: U, U <: V  ⟹  T <: V
Top:           T <: ANY
Bottom:        NULL <: T
Widening:      INTEGER <: DECIMAL
Unwrap:        FhirType(T) <: T
Covariance:    Lambda[A] <: Lambda[B]  if  A <: B
```

#### Polymorphism with Quantification

```
union : ∀T. Collection[T] -> Collection[T] -> Collection[T]
in    : ∀T. T -> Collection[T] -> Boolean
equal : ∀T. T -> T -> Boolean
```

#### Benefits

✅ **Theoretically clean**: Well-understood formal properties  
✅ **Type inference**: Hindley-Milner or bidirectional checking  
✅ **Academic rigor**: Provable soundness  

#### Drawbacks

❌ **Very complex**: Requires full type inference engine  
❌ **Overkill**: Far exceeds FHIRPath's needs  
❌ **Implementation cost**: Weeks/months of work  
❌ **Debugging**: Hard to understand error messages  

#### Assessment

**Not recommended for FHIRPath.** This is how ML/Haskell work, but FHIRPath is a much simpler domain-specific language. The implementation complexity isn't justified.

---

## Detailed Comparison Matrix

| Aspect | Current System | Option 1: Cardinality-Agnostic | Option 3: Type + Constraints | Option 4: Subtyping |
|--------|---------------|-------------------------------|----------------------------|---------------------|
| **Type Complexity** | Medium | Low | Low | Very High |
| **Adaptation Rules** | 9 rules | 5 rules | 6 rules | Subtyping relation |
| **Cardinality Tracking** | In type | Metadata | Metadata + Constraints | Not explicit |
| **Type Variables** | Need capture types | Need type vars | Native support | Quantification |
| **Implementation Effort** | Done ✓ | Medium refactor | Medium refactor | Large rewrite |
| **FHIRPath Match** | Good | Excellent | Excellent | Mismatch |
| **Maintainability** | Medium | High | High | Low |
| **Error Messages** | Good | Good | Good | Complex |
| **Extensibility** | Medium | High | Very High | Low |

---

## Expert Recommendation: Option 3 (Type + Constraint System)

### Why Option 3 is Best for FHIRPath

1. **Semantic Clarity**: Types represent "what kind of value" (INTEGER, STRING, Patient), constraints represent "how many" (singleton, collection)

2. **FHIRPath Philosophy**: The language treats everything as collections conceptually, making cardinality a secondary concern

3. **Simplest Type Algebra**: Only ~6 adaptation rules vs. 9+ in current system

4. **Type Variables**: Natural support without capture complexity

5. **Incremental**: Can implement in phases:
   - Phase 1: Remove `CollectionType`, add `Cardinality` metadata
   - Phase 2: Add `TypeVariable` to type system
   - Phase 3: Add constraint system (optional, for better validation)

6. **Extensible**: Easy to add new constraints without touching type system

7. **Practical**: Balances theoretical soundness with implementation simplicity

### Migration Path from Current System

#### Step 1: Add Cardinality to IRNode

```java
interface IRNode {
    Type getType();
    Cardinality getCardinality();  // NEW
}

enum Cardinality {
    SINGLE,  // 0..1
    MANY     // 0..*
}
```

#### Step 2: Compute Cardinality from CollectionType

```java
// During migration, infer cardinality from existing types
default Cardinality getCardinality() {
    Type type = getType();
    if (type instanceof CollectionType) {
        return Cardinality.MANY;
    } else {
        return Cardinality.SINGLE;
    }
}
```

#### Step 3: Update Signatures to Track Cardinality

```java
record SignatureDefinition(
    List<ParameterSpec> parameters,
    ResultSpec resultSpec
)

record ParameterSpec(
    Type type,           // Semantic type
    Cardinality card     // Expected cardinality
)
```

#### Step 4: Simplify Adaptation Rules

```java
// Remove collection-specific adaptation
// Keep only semantic type adaptation
Cost adapt(Type from, Type to) {
    if (from == to) return 0;
    if (to == ANY) return 0;
    if (from == NULL) return 0;
    if (to instanceof TypeVariable tv) {
        unify(tv, from);
        return 0;
    }
    if (canCast(from, to)) return 1;
    return INFINITE;
}
```

#### Step 5: Remove CollectionType (Optional)

Once cardinality is fully tracked in metadata, `CollectionType` can be removed from the type system entirely.

---

## Addressing Concerns

### "But we still need to track cardinality somewhere!"

**Yes!** Option 3 doesn't eliminate cardinality tracking—it moves it from the type system to metadata. This is actually **cleaner** because:

- Cardinality is orthogonal to type (INTEGER can be singleton or collection)
- Adaptation rules only care about semantic types, not cardinality
- Cardinality constraints can be checked separately

### "Won't we lose type safety?"

**No!** Constraints provide the same safety:

```java
// Current system: enforced in type system
CollectionType(INTEGER) // must be collection

// Option 3: enforced in constraints
param(INTEGER, IsCollection()) // must be collection
```

Same guarantees, cleaner separation of concerns.

### "What about operations that care about cardinality?"

**Signatures specify both type and cardinality:**

```java
signature("first",
    param(ANY, MANY),      // input must be collection
    result(ANY, SINGLE)    // output is singleton
)

signature("count",
    param(ANY, MANY),      // input must be collection
    result(INTEGER, SINGLE) // always returns one integer
)
```

The signature tells you both **what type** (ANY, INTEGER) and **what cardinality** (SINGLE, MANY).

### "How do type variables work with cardinality?"

**Type variables bind to semantic types, cardinality is checked separately:**

```java
signature("union",
    param(typeVar("T"), MANY),    // Collection[T]
    param(typeVar("T"), MANY),    // Collection[T]
    result(typeVar("T"), MANY)    // -> Collection[T]
)

// Resolution:
// 1. Unify types: T = commonType(arg1.type, arg2.type)
// 2. Check cardinality: arg1.card == MANY ✓, arg2.card == MANY ✓
// 3. Result: type=T, card=MANY
```

---

## Comparison to Your Current System

### What You Have Now (Good!)

Your current implementation is actually **very close** to Option 3! You have:

✅ `CollectionType` wrapper (equivalent to `Many(T)` in my examples)  
✅ Free singleton promotion (`T ⇒ [T]` cost 0)  
✅ Proper lambda handling (`LambdaType` vs `Lambda` split)  
✅ Cost-based overload resolution  
✅ `ResultSpec` for dynamic type computation  

### What's Missing

❌ **Type variables**: Can't express "both parameters same type"  
❌ **Common type computation**: Ad-hoc handling in special cases  
❌ **Systematic unification**: Manual type checking instead  

### Actual Difference from Option 3

The difference is **smaller than you think**:

| Current System | Option 3 |
|----------------|----------|
| `CollectionType[T]` | `Type + Cardinality.MANY` |
| `T` (non-collection) | `Type + Cardinality.SINGLE` |
| Type in type system | Type in type system (same!) |
| Cardinality in type wrapper | Cardinality in metadata |
| No type variables | `TypeVariable` in type system |
| `ResultSpec` computes types | Type variables + constraints |

**Key insight**: You're already doing most of what Option 3 suggests! The main change would be:

1. Move cardinality from type wrapper to IR node metadata
2. Add `TypeVariable` type
3. Add unification to `OverloadResolver`
4. Optionally add explicit constraint system

This is an **incremental improvement**, not a rewrite.

---

## Final Recommendation

**Keep your current system and add type variables incrementally.**

Your system is well-designed and close to Option 3 already. Don't throw it away! Instead:

### Incremental Enhancement Plan

1. **Add `TypeVariable` type** (small change)
2. **Add unification to `OverloadResolver`** (moderate change)
3. **Add common type computation** via `AdaptationEngine` (moderate change)
4. **Update problematic signatures** (`in`, `contains`, `union`) to use type variables
5. **Optionally**: Refactor to separate cardinality (larger change, optional)

This gives you **all the benefits** of Option 3 while preserving your existing investment.

### Why Not Switch to Pure Option 1 or 3?

- **Not worth the rewrite**: Your current system works
- **Minimal benefit**: The problems you face (polymorphic signatures) are solved by type variables, not by removing `CollectionType`
- **Risk**: Large refactoring could introduce bugs
- **Cost**: Weeks of work for marginal improvement

### The Real Problem is Solvable Without Major Changes

Your struggle with `in`, `contains`, `union` operators is **not** caused by having `CollectionType`. It's caused by **lacking type variables**.

Add type variables → problem solved. No need to restructure the entire type system.

---

## Conclusion

After analyzing your type system from a programming language design perspective:

1. **Your current design is sound** - You've made good choices that align with FHIRPath semantics

2. **Option 3 (Type + Constraints) is theoretically cleanest** - But you're already 90% there

3. **The real gap is type variables** - Not the presence of `CollectionType`

4. **Recommended path**: Incremental enhancement, not rewrite

5. **Avoid Option 4 (Subtyping)** - Massive overkill for FHIRPath

6. **Option 1 (Cardinality-Agnostic) is elegant** - But not worth migrating to

The best path forward is **adding type variables and common type computation** to your existing system. This solves your immediate problems (polymorphic signatures) without requiring a fundamental redesign.

Your instinct that the system was getting complex is correct, but the complexity comes from working around the lack of type variables, not from the type system design itself. Fix that root cause, and the system becomes much simpler.

