# FHIRPath Type System Design Options

**Date**: 2025-10-18
**Context**: Design options for implementing the element-first type system described in `TYPE_SYSTEM.md`
**Goal**: Elegant, simple Java implementation for use with the Analyzer (AST → IR resolution)

---

## Executive Summary

This document presents design options for implementing the FHIRPath type system in Java, aligned with the element-first model specified in `TYPE_SYSTEM.md`. The options include two variants of the Element-First approach (pure and with arity variables) and a hybrid pragmatic approach, balancing different tradeoffs between simplicity, spec alignment, and migration cost.

**Recommendation**: **Option 2 (Element-First Pure)** for long-term maintainability and spec alignment, with **Option 3 (Hybrid Pragmatic)** as a quick-win migration path.

---

## Design Principles

1. **Element-first typing**: Track element type and cardinality separately (as per `TYPE_SYSTEM.md`)
2. **Simple for common cases**: Most signatures should be concise and self-documenting
3. **Possible for complex cases**: Support polymorphic signatures with type variables
4. **Easy analyzer integration**: Natural fit with existing `Analyzer` and IR generation
5. **Minimal verbosity**: Signature definitions should be readable and maintainable

---

## Current System Analysis

### Package Organization

**Phase 1 implementation complete** with clear separation of concerns:

```
com.example.fhirpath/
├── analyzer/        - AST → IR transformation
│   └── Analyzer.java
├── operation/       - Operation resolution subsystem
│   ├── OperationResolver.java       (facade)
│   ├── OperationRegistry.java
│   ├── OverloadResolver.java
│   ├── OperatorNormalizer.java
│   └── InfrastructureOperationHandler.java
├── operation/signature/  - Type signature specifications
│   ├── SignatureDefinition.java
│   ├── ParamSpec.java
│   ├── ResultTypeSpec.java
│   ├── ResolvedSignature.java
│   ├── LambdaBindingStrategy.java
│   └── Signatures.java
├── typing/          - Type system (element-first model)
│   ├── Type.java (interface)
│   ├── PrimitiveType.java
│   ├── ComplexType.java
│   ├── Shape.java (Type + Cardinality)
│   ├── Cardinality.java (SINGLE/MANY)
│   └── TypeSets.java
└── ir/              - Intermediate representation nodes
```

### Implemented Type System (Phase 1)

```java
// Element-first type system (cardinality as metadata)
public sealed interface Type
    permits PrimitiveType, ComplexType, FhirType, LambdaType, NullType {
    String getName();
    boolean isPrimitive();
    boolean isComplex();
    // NO isCollection() - cardinality is separate!
}

// Shape combines Type + Cardinality
public sealed interface Shape {
    Type elementType();
    Cardinality cardinality();

    record Single(Type elementType) implements Shape { }
    record Many(Type elementType) implements Shape { }
}

// Cardinality as metadata (not in type hierarchy)
public enum Cardinality { SINGLE, MANY; }
```

### Signature System (Phase 1)

```java
// Simple parameter spec
public record ParamSpec(Type type, Cardinality cardinality) {
    public static ParamSpec single(Type t) { ... }
    public static ParamSpec many(Type t) { ... }
}

// Simple result spec
public record ResultTypeSpec(Type type, Cardinality cardinality) {
    public static ResultTypeSpec single(Type t) { ... }
    public static ResultTypeSpec many(Type t) { ... }
}

// Signature definition
public record SignatureDefinition(
    List<ParamSpec> parameters,
    ResultTypeSpec resultSpec,
    int minArity,
    LambdaBindingStrategy lambdaBinding
)
```

### Recent Architectural Improvements

**Phase 1 Implementation** (commits ad75725, ac14afd, 7f22e13):
- ✅ Extracted operation resolution to dedicated package hierarchy
- ✅ Created OperationResolver facade for clean API
- ✅ Implemented cardinality checking (FHIRPath spec compliance)
- ✅ Removed legacy FunctionSignature abstraction
- ✅ Extracted lambda binding strategy logic
- ✅ All 193 tests passing

### Current Capabilities & Limitations

**What Works (Phase 1 + 1.5)**:
- ✅ Arithmetic on same types: `2 + 3`, `2.5 + 1.5`
- ✅ Comparison on same types: `2 > 1`, `"a" < "b"`
- ✅ String functions: `"hello".substring(0, 2)`
- ✅ Collection functions: `items.count()`, `items.first()`
- ✅ **Cardinality enforcement**: `(1 | 2) + 3` → compile error
- ✅ Lambda operations: `where()`, `select()` with proper $this binding

**Phase 2 (Future - Type Variables)**:
- ❌ Mixed-type arithmetic: `2 + 2.5` (needs type variables)
- ❌ Polymorphic operators: `union`, `in`, `contains`
- ❌ Type constraints: `abs(?T)` where T ∈ Arithmetic

---


## Option 2: Element-First Pure (Cardinality as Metadata)

**Philosophy**: Align with TYPE_SYSTEM.md by tracking cardinality as metadata, not in type structure. Simplest type algebra.

### Core Type System

```java
// Simplified Type hierarchy - NO CollectionType!
public sealed interface Type
    permits PrimitiveType, ComplexType, FhirType,
            LambdaType, TypeVariable, Any, Bottom {
    String getName();
    boolean isPrimitive();
    boolean isComplex();
    // NO isCollection() - cardinality is separate!
}

// Type variable for polymorphism
public record TypeVariable(String name) implements Type {
    @Override public String getName() { return name; }
    @Override public boolean isPrimitive() { return false; }
    @Override public boolean isComplex() { return false; }

    @Override
    public String toString() { return name; }
}

// Bottom type (⊥) for empty collections
public enum Bottom implements Type {
    INSTANCE;
    @Override public String getName() { return "⊥"; }
    @Override public boolean isPrimitive() { return false; }
    @Override public boolean isComplex() { return false; }
}

// Cardinality tracked separately from type
public enum Cardinality {
    SINGLE,  // 0..1 (spec: ?)
    MANY;    // 0..* (spec: *)

    public Cardinality join(Cardinality other) {
        return (this == MANY || other == MANY) ? MANY : SINGLE;
    }

    @Override
    public String toString() {
        return this == SINGLE ? "?" : "*";
    }
}
```

### IRNode with Metadata

```java
// IRNode carries type and cardinality separately
public sealed interface IRNode {
    @Nonnull Type getType();           // Semantic type only
    @Nonnull Cardinality getCardinality();  // Cardinality metadata

    default boolean isSingular() {
        return getCardinality() == Cardinality.SINGLE;
    }

    @Nonnull <T> T accept(@Nonnull IRNodeVisitor<T> visitor);
}

// Concrete implementations carry both attributes
public record Operation(
    String name,
    List<IRNode> arguments,
    Type type,
    Cardinality cardinality  // Resolved from signature
) implements IRNode {
    @Override public Type getType() { return type; }
    @Override public Cardinality getCardinality() { return cardinality; }
}

public record Literal(
    Object value,
    Type type
) implements IRNode {
    @Override public Type getType() { return type; }
    @Override public Cardinality getCardinality() {
        return Cardinality.SINGLE;  // Literals are always singleton
    }
}

public record Traversal(
    IRNode target,
    String fieldName,
    Type type,
    Cardinality cardinality  // From FHIR schema
) implements IRNode {
    @Override public Type getType() { return type; }
    @Override public Cardinality getCardinality() { return cardinality; }
}
```

### Signature Definition

```java
// Parameter specification: type + cardinality + optional constraint
public record ParamSpec(
    Type type,           // Can be TypeVariable
    Cardinality cardinality,
    @Nullable Predicate<Type> constraint
) {
    public ParamSpec(Type type, Cardinality cardinality) {
        this(type, cardinality, null);
    }

    // Fluent factory methods matching spec notation
    public static ParamSpec single(Type t) {
        return new ParamSpec(t, Cardinality.SINGLE);
    }

    public static ParamSpec many(Type t) {
        return new ParamSpec(t, Cardinality.MANY);
    }

    public static ParamSpec singleVar(String varName) {
        return new ParamSpec(new TypeVariable(varName), Cardinality.SINGLE);
    }

    public static ParamSpec manyVar(String varName) {
        return new ParamSpec(new TypeVariable(varName), Cardinality.MANY);
    }

    // With constraint for type sets
    public ParamSpec withConstraint(Predicate<Type> pred) {
        return new ParamSpec(type, cardinality, pred);
    }

    @Override
    public String toString() {
        return (cardinality == Cardinality.SINGLE ? "?" : "*") + type;
    }
}

// Result type specification: type + cardinality
public record ResultTypeSpec(
    Type type,
    Cardinality cardinality
) {
    public static ResultTypeSpec single(Type t) {
        return new ResultTypeSpec(t, Cardinality.SINGLE);
    }

    public static ResultTypeSpec many(Type t) {
        return new ResultTypeSpec(t, Cardinality.MANY);
    }

    public static ResultTypeSpec singleVar(String varName) {
        return new ResultTypeSpec(new TypeVariable(varName), Cardinality.SINGLE);
    }

    public static ResultTypeSpec manyVar(String varName) {
        return new ResultTypeSpec(new TypeVariable(varName), Cardinality.MANY);
    }

    @Override
    public String toString() {
        return (cardinality == Cardinality.SINGLE ? "?" : "*") + type;
    }
}

// Clean, minimal signature definition
public record SignatureDefinition(
    List<ParamSpec> parameters,
    ResultTypeSpec resultSpec,
    int minArity
) {
    public SignatureDefinition(List<ParamSpec> params, ResultTypeSpec result) {
        this(params, result, params.size());
    }

    public boolean canApplyToArgumentCount(int argCount) {
        return argCount >= minArity && argCount <= parameters.size();
    }

    @Override
    public String toString() {
        return parameters.stream()
            .map(ParamSpec::toString)
            .collect(Collectors.joining(", ", "(", ") → " + resultSpec));
    }
}
```

### Adaptation Rules (Dramatically Simpler!)

```java
public class TypeAdapter {
    /**
     * Compute adaptation cost from source to target type.
     * NO cardinality involved - purely semantic types!
     */
    public static int adaptationCost(Type from, Type to) {
        // Identity
        if (from.equals(to)) return 0;

        // Bottom adapts to anything
        if (from == Bottom.INSTANCE) return 0;

        // Anything adapts to Any
        if (to == PrimitiveType.ANY) return 0;

        // Type variable - handled by unification
        if (to instanceof TypeVariable) return 0;

        // Numeric widening (cost 1)
        if (from == PrimitiveType.INTEGER && to == PrimitiveType.DECIMAL) return 1;
        if (from == PrimitiveType.INTEGER && to == PrimitiveType.LONG) return 1;
        if (from == PrimitiveType.LONG && to == PrimitiveType.DECIMAL) return 1;

        // To quantity (cost 1)
        if (isNumeric(from) && to == PrimitiveType.QUANTITY) return 1;

        // Temporal (cost 1)
        if (from == PrimitiveType.DATE && to == PrimitiveType.DATE_TIME) return 1;

        // FHIR unwrap (cost 1 + recursive)
        if (from instanceof FhirType ft) {
            return 1 + adaptationCost(ft.systemType(), to);
        }

        // Lambda covariance
        if (from instanceof LambdaType lf && to instanceof LambdaType lt) {
            return adaptationCost(lf.returnType(), lt.returnType());
        }

        return Integer.MAX_VALUE;  // Impossible
    }

    /**
     * Find common type that both types can adapt to.
     * Implements LUB computation via adaptation closure.
     */
    public static Type commonType(Type a, Type b) {
        // Trivial cases
        if (a.equals(b)) return a;
        if (a == Bottom.INSTANCE) return b;
        if (b == Bottom.INSTANCE) return a;

        // Compute adaptation closures
        Map<Type, Integer> closureA = computeAdaptationClosure(a);
        Map<Type, Integer> closureB = computeAdaptationClosure(b);

        // Find best common target (minimum total cost)
        return closureA.keySet().stream()
            .filter(closureB::containsKey)
            .min(Comparator.comparingInt(t ->
                closureA.get(t) + closureB.get(t)))
            .orElse(null);
    }

    private static boolean isNumeric(Type t) {
        return t == PrimitiveType.INTEGER
            || t == PrimitiveType.LONG
            || t == PrimitiveType.DECIMAL;
    }
}
```

### Example Signatures (Extremely Clean!)

```java
import static ParamSpec.*;
import static ResultTypeSpec.*;
import static PrimitiveType.*;

// union: ∀ K, L, α, β. [LUB(K, L) defined] ⇒ union(α K, β L) → *LUB(K, L)
public static final SignatureDefinition UNION = new SignatureDefinition(
    List.of(manyVar("T"), manyVar("T")),
    manyVar("T")
);

// in: ∀ K, L. [LUB(K, L) defined] ⇒ in(?K, *L) → ?BOOLEAN
public static final SignatureDefinition IN = new SignatureDefinition(
    List.of(singleVar("X"), manyVar("X")),
    single(BOOLEAN)
);

// first: ∀ T, α. first(α T) → ?T
public static final SignatureDefinition FIRST = new SignatureDefinition(
    List.of(manyVar("T")),
    singleVar("T")
);

// where: ∀ T, α. where(α T, (?T ⇒ ?BOOLEAN)) → α T
public static final SignatureDefinition WHERE = new SignatureDefinition(
    List.of(
        manyVar("T"),
        single(new LambdaType(BOOLEAN))
    ),
    manyVar("T")
);

// count: ∀ T, α. count(α T) → ?INTEGER
public static final SignatureDefinition COUNT = new SignatureDefinition(
    List.of(many(ANY)),
    single(INTEGER)
);

// abs with constraint: [T ∈ Arithmetic] ⇒ abs(?T) → ?T
public static final SignatureDefinition ABS = new SignatureDefinition(
    List.of(singleVar("T").withConstraint(TypeSets.ARITHMETIC::contains)),
    singleVar("T")
);

// substring: substring(?STRING, ?INTEGER, [?INTEGER]) → ?STRING
public static final SignatureDefinition SUBSTRING = new SignatureDefinition(
    List.of(single(STRING), single(INTEGER), single(INTEGER)),
    single(STRING),
    2  // minArity for optional parameter
);
```

### Advanced Signature Examples

The basic signatures above show simple cases. Here are three important advanced patterns that require custom `ResultSpec` implementations or enhanced parameter matching.

#### Pattern 1: Multiple Overloads with Constraints (+ operator)

The `+` operator has three distinct overloads for different type categories:

```java
// 1. Arithmetic addition: ∀ T. [T ∈ Arithmetic] ⇒ +(?T, ?T) → ?T
public static final SignatureDefinition PLUS_ARITHMETIC = new SignatureDefinition(
    List.of(
        singleVar("T").withConstraint(TypeSets.ARITHMETIC::contains),
        singleVar("T")  // Same type variable - unifies to common type
    ),
    singleVar("T")  // Result is unified type
);

// 2. String concatenation: +(?STRING, ?STRING) → ?STRING
public static final SignatureDefinition PLUS_STRING = new SignatureDefinition(
    List.of(single(STRING), single(STRING)),
    single(STRING)
);

// 3. Temporal addition: ∀ T. [T ∈ Temporal] ⇒ +(?T, ?QUANTITY) → ?T
public static final SignatureDefinition PLUS_TEMPORAL = new SignatureDefinition(
    List.of(
        singleVar("T").withConstraint(TypeSets.TEMPORAL::contains),
        single(QUANTITY)
    ),
    singleVar("T")
);

// Register all three
registry.register("+", PLUS_ARITHMETIC);
registry.register("+", PLUS_STRING);
registry.register("+", PLUS_TEMPORAL);
```

**Resolution example** (`2 + 2.5`):
1. Try PLUS_STRING: INTEGER vs STRING → fails
2. Try PLUS_TEMPORAL: INTEGER not in Temporal → fails constraint
3. Try PLUS_ARITHMETIC:
   - Param 0: INTEGER, bind T=INTEGER, passes ARITHMETIC constraint ✓
   - Param 1: DECIMAL, need to unify with T=INTEGER
   - Common type: DECIMAL (INTEGER → DECIMAL, cost 1)
   - Update binding: T=DECIMAL
   - Adapt arg 0: `cast(2, DECIMAL)`
   - Result: (DECIMAL, SINGLE)

#### Pattern 2: Cardinality-Preserving Functions (where)

**Simple Approach**: Two signatures for MANY and SINGLE cardinalities:

```java
// where() for collections: *T → *T
public static final SignatureDefinition WHERE_MANY = new SignatureDefinition(
    List.of(
        manyVar("T"),
        single(new LambdaType(BOOLEAN))
    ),
    manyVar("T")
);

// where() for singletons: ?T → ?T (rarely used in practice)
public static final SignatureDefinition WHERE_SINGLE = new SignatureDefinition(
    List.of(
        singleVar("T"),
        single(new LambdaType(BOOLEAN))
    ),
    singleVar("T")
);
```

**Enhanced Approach**: Use `Cardinality.ANY` and dynamic ResultSpec:

```java
// Add to Cardinality enum
public enum Cardinality {
    SINGLE, MANY,
    ANY;  // Matches either SINGLE or MANY (for parameter specs only)

    public boolean matches(Cardinality actual) {
        return this == ANY || this == actual;
    }
}

// Enhanced ResultSpec that preserves input cardinality
public sealed interface ResultSpec {
    Type resolveType(List<IRNode> args, TypeBindings bindings);
    Cardinality resolveCardinality(List<IRNode> args);

    record PreserveInputShape(int argIndex) implements ResultSpec {
        public PreserveInputShape() { this(0); }

        @Override
        public Type resolveType(List<IRNode> args, TypeBindings bindings) {
            return bindings.substitute(args.get(argIndex).getType());
        }

        @Override
        public Cardinality resolveCardinality(List<IRNode> args) {
            return args.get(argIndex).getCardinality();
        }
    }
}

// Then where() becomes a single signature:
public static final SignatureDefinition WHERE = new SignatureDefinition(
    List.of(
        new ParamSpec(new TypeVariable("T"), Cardinality.ANY),  // Accepts any cardinality
        single(new LambdaType(BOOLEAN))
    ),
    new ResultTypeSpec.PreserveInputShape()  // Preserves input cardinality
);
```

**Resolution examples**:
- `Patient.name.where(use = 'official')` → input (HumanName, MANY) → result (HumanName, MANY)
- `Patient.birthDate.where($this != {})` → input (DATE, SINGLE) → result (DATE, SINGLE)

#### Pattern 3: Dynamic Type/Cardinality Resolution (iif)

The `iif()` function requires computing LUB of branch types and join of branch cardinalities:

```java
// Enhanced ResultSpec for iif
public sealed interface ResultSpec {
    // ... other specs ...

    /**
     * For iif: computes LUB of branch types and join of cardinalities.
     * Spec: iif(α T, (α T ⇒ ?BOOLEAN), (α T ⇒ b M), (α T ⇒ c K)) → (b ⊔ c) LUB(M, K)
     */
    record IifBranchLUB(int trueBranchIndex, int falseBranchIndex) implements ResultSpec {
        public IifBranchLUB() { this(2, 3); }

        @Override
        public Type resolveType(List<IRNode> args, TypeBindings bindings) {
            Type trueType = extractLambdaBodyType(args.get(trueBranchIndex));

            Type falseType;
            if (args.size() > falseBranchIndex) {
                falseType = extractLambdaBodyType(args.get(falseBranchIndex));
            } else {
                falseType = Bottom.INSTANCE;  // Two-arg form: else is {}
            }

            // Compute LUB
            Type lub = TypeAdapter.commonType(trueType, falseType);
            if (lub == null) {
                throw new IllegalArgumentException(
                    "Cannot find common type for iif branches: " +
                    trueType + " and " + falseType);
            }
            return bindings.substitute(lub);
        }

        @Override
        public Cardinality resolveCardinality(List<IRNode> args) {
            Cardinality trueCar = extractLambdaBodyCardinality(args.get(trueBranchIndex));

            Cardinality falseCar;
            if (args.size() > falseBranchIndex) {
                falseCar = extractLambdaBodyCardinality(args.get(falseBranchIndex));
            } else {
                falseCar = Cardinality.SINGLE;  // {} is singleton
            }

            return trueCar.join(falseCar);  // Cardinality join: ? ⊔ ? = ?, ? ⊔ * = *, etc.
        }

        private Type extractLambdaBodyType(IRNode node) {
            return (node instanceof Lambda l) ? l.body().getType() : node.getType();
        }

        private Cardinality extractLambdaBodyCardinality(IRNode node) {
            return (node instanceof Lambda l) ? l.body().getCardinality() : node.getCardinality();
        }
    }
}

// Signature definition (supports both 2-arg and 3-arg forms)
public static final SignatureDefinition IIF = new SignatureDefinition(
    List.of(
        many(ANY),                        // α T - input collection
        single(new LambdaType(BOOLEAN)),  // (α T ⇒ ?BOOLEAN) - condition
        single(new LambdaType(ANY)),      // (α T ⇒ b M) - true branch
        single(new LambdaType(ANY))       // (α T ⇒ c K) - false branch (optional)
    ),
    new ResultSpec.IifBranchLUB(),
    3  // minArity = 3 (false branch is optional)
);
```

**Resolution examples**:

1. Simple match: `Patient.name.iif(use='official', family, given.first())`
   - True: (STRING, MANY), False: (STRING, SINGLE)
   - Result: LUB(STRING, STRING) = STRING, MANY ⊔ SINGLE = MANY
   - Final: (STRING, MANY)

2. Type widening: `Patient.multipleBirth.iif(is(boolean), 1, 0.0)`
   - True: (INTEGER, SINGLE), False: (DECIMAL, SINGLE)
   - Result: LUB(INTEGER, DECIMAL) = DECIMAL, SINGLE ⊔ SINGLE = SINGLE
   - Final: (DECIMAL, SINGLE)

3. Two-arg form: `Patient.name.iif(use='official', family)`
   - True: (STRING, MANY), False: (⊥, SINGLE) [empty]
   - Result: LUB(STRING, ⊥) = STRING, MANY ⊔ SINGLE = MANY
   - Final: (STRING, MANY)

### Pros

✅ **Simplest type system** - only ~5 adaptation rules
✅ **Matches spec philosophy** - cardinality is metadata, not type
✅ **Extremely clean signatures** - minimal verbosity
✅ **No dual concepts** - single way to represent cardinality
✅ **Easy to understand** - clear separation of concerns
✅ **Type algebra is trivial** - no collection wrapper complexity
✅ **Best long-term** - aligns with TYPE_SYSTEM.md and expert analysis

### Cons

❌ **Breaking change** - removes CollectionType entirely
❌ **Migration effort** - need to update all IR node constructors
❌ **Two-attribute tracking** - must carry (type, cardinality) pairs
❌ **Existing code refactor** - wherever CollectionType was used
❌ **Larger initial investment** - 3-4 weeks of work

### Migration Effort

**High** (3-4 weeks)
- Remove CollectionType from Type hierarchy
- Add Cardinality enum
- Add getCardinality() to IRNode
- Update all IR node constructors
- Update all Type usage throughout codebase
- Migrate all signatures
- Update Analyzer type resolution logic
- Comprehensive testing

---

## Option 2b: Element-First with Arity Variables

**Philosophy**: Extend Option 2 with explicit arity variables (α, β) to perfectly mirror the spec's notation and eliminate signature duplication for cardinality-preserving functions.

This is a **refinement of Option 2** that adds ~250 LOC to directly support the spec's arity variable concept, enabling signatures like `where(α T, ...) → α T` that exactly match the mathematical notation in TYPE_SYSTEM.md.

### Core Additions to Option 2

```java
// NEW: Arity variable (mirrors type variable concept)
public record ArityVariable(String name) {
    // Common arity variables from spec
    public static final ArityVariable ALPHA = new ArityVariable("α");
    public static final ArityVariable BETA = new ArityVariable("β");

    @Override
    public String toString() { return name; }
}

// NEW: Shape specification combines type and arity (both can be variables!)
public sealed interface ShapeSpec {
    Type elementType();
    Object arity();  // Can be Cardinality or ArityVariable

    // Factory methods for fluent API
    static ShapeSpec of(Type type, Cardinality card) {
        return new Concrete(type, card);
    }

    static ShapeSpec of(Type type, ArityVariable arity) {
        return new WithArityVar(type, arity);
    }

    static ShapeSpec of(TypeVariable typeVar, Cardinality card) {
        return new WithTypeVar(typeVar, card);
    }

    static ShapeSpec of(TypeVariable typeVar, ArityVariable arity) {
        return new FullyPolymorphic(typeVar, arity);
    }

    // Concrete: both type and arity are known
    record Concrete(Type elementType, Cardinality arity) implements ShapeSpec {
        @Override
        public String toString() {
            return (arity == Cardinality.SINGLE ? "?" : "*") + elementType.getName();
        }
    }

    // Type variable with concrete arity: ?T or *T
    record WithTypeVar(TypeVariable elementType, Cardinality arity) implements ShapeSpec {
        @Override
        public String toString() {
            return (arity == Cardinality.SINGLE ? "?" : "*") + elementType.name();
        }
    }

    // Concrete type with arity variable: α STRING
    record WithArityVar(Type elementType, ArityVariable arity) implements ShapeSpec {
        @Override
        public String toString() {
            return arity.name() + elementType.getName();
        }
    }

    // Both type and arity are variables: α T (most general)
    record FullyPolymorphic(TypeVariable elementType, ArityVariable arity)
        implements ShapeSpec {
        @Override
        public String toString() {
            return arity.name() + elementType.name();
        }
    }
}
```

### Enhanced Signature System

```java
// Signature definition uses ShapeSpec instead of ParamSpec/ResultTypeSpec
public record SignatureDefinition(
    List<ShapeSpec> parameters,
    ShapeSpec resultSpec,
    int minArity
) {
    public SignatureDefinition(List<ShapeSpec> params, ShapeSpec result) {
        this(params, result, params.size());
    }

    @Override
    public String toString() {
        return parameters.stream()
            .map(ShapeSpec::toString)
            .collect(Collectors.joining(", ", "(", ") → " + resultSpec));
    }
}
```

### Unified Bindings for Type and Arity Variables

```java
// Tracks bindings for both type and arity variables during resolution
public class SignatureBindings {
    private final Map<String, Type> typeBindings = new HashMap<>();
    private final Map<String, Cardinality> arityBindings = new HashMap<>();

    // Bind type variable (with LUB computation)
    public void bindType(String varName, Type type) {
        Type existing = typeBindings.get(varName);
        if (existing == null) {
            typeBindings.put(varName, type);
        } else if (!existing.equals(type)) {
            // Compute LUB for multiple bindings
            Type common = TypeAdapter.commonType(existing, type);
            if (common == null) {
                throw new IllegalArgumentException(
                    "Cannot unify type variable " + varName +
                    ": " + existing + " vs " + type);
            }
            typeBindings.put(varName, common);
        }
    }

    // Bind arity variable (must be consistent)
    public void bindArity(String varName, Cardinality arity) {
        Cardinality existing = arityBindings.get(varName);
        if (existing == null) {
            arityBindings.put(varName, arity);
        } else if (existing != arity) {
            throw new IllegalArgumentException(
                "Arity variable " + varName + " bound to both " +
                existing + " and " + arity);
        }
        // If same, no problem (multiple uses of same arity variable)
    }

    // Resolve shape spec with current bindings
    public ResolvedShape resolve(ShapeSpec spec) {
        return switch (spec) {
            case ShapeSpec.Concrete(Type t, Cardinality c) ->
                new ResolvedShape(t, c);

            case ShapeSpec.WithTypeVar(TypeVariable tv, Cardinality c) -> {
                Type resolved = typeBindings.getOrDefault(tv.name(), tv);
                yield new ResolvedShape(resolved, c);
            }

            case ShapeSpec.WithArityVar(Type t, ArityVariable av) -> {
                Cardinality resolved = arityBindings.getOrDefault(
                    av.name(), Cardinality.MANY);  // Default if unbound
                yield new ResolvedShape(t, resolved);
            }

            case ShapeSpec.FullyPolymorphic(TypeVariable tv, ArityVariable av) -> {
                Type resolvedType = typeBindings.getOrDefault(tv.name(), tv);
                Cardinality resolvedArity = arityBindings.getOrDefault(
                    av.name(), Cardinality.MANY);
                yield new ResolvedShape(resolvedType, resolvedArity);
            }
        };
    }

    public record ResolvedShape(Type type, Cardinality cardinality) {}
}
```

### Signature Resolution with Arity Matching

```java
public class SignatureResolver {

    public ResolvedCall resolve(SignatureDefinition sig, List<IRNode> args) {
        if (!sig.canApplyToArgumentCount(args.size())) {
            return null;
        }

        SignatureBindings bindings = new SignatureBindings();
        List<IRNode> adaptedArgs = new ArrayList<>();
        int totalCost = 0;

        // Match each parameter
        for (int i = 0; i < args.size(); i++) {
            ShapeSpec paramSpec = sig.parameters().get(i);
            IRNode arg = args.get(i);

            // Match shape and bind variables
            MatchResult match = matchShape(arg, paramSpec, bindings);
            if (!match.success()) {
                return null;  // No match
            }

            totalCost += match.cost();
            adaptedArgs.add(match.adaptedNode());
        }

        // Resolve result shape using bindings
        SignatureBindings.ResolvedShape result = bindings.resolve(sig.resultSpec());

        return new ResolvedCall(
            result.type(),
            result.cardinality(),
            adaptedArgs,
            totalCost
        );
    }

    private MatchResult matchShape(
        IRNode arg,
        ShapeSpec spec,
        SignatureBindings bindings
    ) {
        Type argType = arg.getType();
        Cardinality argCard = arg.getCardinality();

        return switch (spec) {
            case ShapeSpec.Concrete(Type expectedType, Cardinality expectedCard) -> {
                // Both concrete - must match exactly (with adaptation)
                if (argCard != expectedCard) {
                    yield MatchResult.FAIL;
                }
                int cost = TypeAdapter.adaptationCost(argType, expectedType);
                if (cost == Integer.MAX_VALUE) {
                    yield MatchResult.FAIL;
                }
                IRNode adapted = adapt(arg, expectedType);
                yield new MatchResult(true, cost, adapted);
            }

            case ShapeSpec.WithTypeVar(TypeVariable tv, Cardinality expectedCard) -> {
                // Type variable, concrete arity
                if (argCard != expectedCard) {
                    yield MatchResult.FAIL;
                }
                bindings.bindType(tv.name(), argType);
                yield new MatchResult(true, 0, arg);
            }

            case ShapeSpec.WithArityVar(Type expectedType, ArityVariable av) -> {
                // Concrete type, arity variable
                bindings.bindArity(av.name(), argCard);
                int cost = TypeAdapter.adaptationCost(argType, expectedType);
                if (cost == Integer.MAX_VALUE) {
                    yield MatchResult.FAIL;
                }
                IRNode adapted = adapt(arg, expectedType);
                yield new MatchResult(true, cost, adapted);
            }

            case ShapeSpec.FullyPolymorphic(TypeVariable tv, ArityVariable av) -> {
                // Both variables - bind both
                bindings.bindType(tv.name(), argType);
                bindings.bindArity(av.name(), argCard);
                yield new MatchResult(true, 0, arg);
            }
        };
    }

    record MatchResult(boolean success, int cost, IRNode adaptedNode) {
        static final MatchResult FAIL = new MatchResult(false, 0, null);
    }
}
```

### Example Signatures (Perfect Spec Alignment!)

```java
import static ArityVariable.*;
import static ShapeSpec.*;

// where: ∀ T, α. where(α T, (?T ⇒ ?BOOLEAN)) → α T
public static final SignatureDefinition WHERE = new SignatureDefinition(
    List.of(
        of(new TypeVariable("T"), ALPHA),  // α T
        of(new LambdaType(BOOLEAN), Cardinality.SINGLE)  // ?Lambda(BOOLEAN)
    ),
    of(new TypeVariable("T"), ALPHA)  // → α T (same arity as input!)
);

// union: ∀ K, L, α, β. [LUB(K, L) defined] ⇒ union(α K, β L) → *LUB(K, L)
public static final SignatureDefinition UNION = new SignatureDefinition(
    List.of(
        of(new TypeVariable("K"), ALPHA),  // α K
        of(new TypeVariable("L"), BETA)    // β L (can be different arity!)
    ),
    of(new TypeVariable("K"), Cardinality.MANY)  // → *K (K unified to LUB)
);

// first: ∀ T, α. first(α T) → ?T
public static final SignatureDefinition FIRST = new SignatureDefinition(
    List.of(
        of(new TypeVariable("T"), ALPHA)  // α T (any cardinality)
    ),
    of(new TypeVariable("T"), Cardinality.SINGLE)  // → ?T (always single)
);

// in: ∀ K, L. in(?K, *L) → ?BOOLEAN
public static final SignatureDefinition IN = new SignatureDefinition(
    List.of(
        of(new TypeVariable("K"), Cardinality.SINGLE),  // ?K
        of(new TypeVariable("L"), Cardinality.MANY)     // *L
    ),
    of(PrimitiveType.BOOLEAN, Cardinality.SINGLE)  // → ?BOOLEAN
);

// skip: ∀ T, α. skip(α T, ?INTEGER) → α T
public static final SignatureDefinition SKIP = new SignatureDefinition(
    List.of(
        of(new TypeVariable("T"), ALPHA),  // α T
        of(INTEGER, Cardinality.SINGLE)    // ?INTEGER
    ),
    of(new TypeVariable("T"), ALPHA)  // → α T (preserves input cardinality)
);

// repeat: ∀ T, α. repeat(α T, (?T ⇒ α T)) → α T
public static final SignatureDefinition REPEAT = new SignatureDefinition(
    List.of(
        of(new TypeVariable("T"), ALPHA),  // α T
        of(new LambdaType(new TypeVariable("T")), Cardinality.SINGLE)  // ?Lambda(T)
    ),
    of(new TypeVariable("T"), ALPHA)  // → α T
);
```

### Resolution Examples

#### Example 1: where() Preserves Cardinality

```fhirpath
Patient.name.where(use = 'official')  // Input: (HumanName, MANY)
Patient.birthDate.where($this != {})   // Input: (DATE, SINGLE)
```

**MANY case:**
1. Signature: `where(α T, ?Lambda(BOOLEAN)) → α T`
2. Arg 0: (HumanName, MANY)
3. Match `α T` vs (HumanName, MANY):
   - Bind T = HumanName
   - Bind α = MANY
4. Result: resolve `α T` → (HumanName, MANY) ✓

**SINGLE case:**
1. Same signature
2. Arg 0: (DATE, SINGLE)
3. Match `α T` vs (DATE, SINGLE):
   - Bind T = DATE
   - Bind α = SINGLE
4. Result: resolve `α T` → (DATE, SINGLE) ✓

**One signature handles both cases!**

#### Example 2: union() with Different Arities

```fhirpath
1 | (2 | 3)  // First arg SINGLE, second arg MANY
```

**Resolution:**
1. Signature: `union(α K, β L) → *K`
2. Arg 0: (INTEGER, SINGLE)
   - Bind K = INTEGER, α = SINGLE
3. Arg 1: (INTEGER, MANY)
   - Bind L = INTEGER, β = MANY
   - L unifies with K: both INTEGER ✓
4. Result: `*K` → (INTEGER, MANY) ✓

**Different arity variables (α, β) allow different input cardinalities!**

### Comparison to Option 2 Basic

| Aspect | Option 2 Basic | Option 2b with Arity Variables |
|--------|----------------|--------------------------------|
| **where() signature** | 2 signatures (MANY + SINGLE) | 1 signature with α T |
| **Spec alignment** | Good | Perfect |
| **Signature verbosity** | Low | Very Low |
| **Implementation LOC** | Base | +250 LOC |
| **Learning curve** | Low | Medium (new concept) |
| **Maintainability** | Good | Excellent |
| **Cardinality preservation** | ResultSpec or duplication | Explicit arity variables |

### Complexity Assessment

**What's needed:**
1. `ArityVariable` record (~10 LOC)
2. `ShapeSpec` sealed interface (4 variants, ~80 LOC)
3. Enhanced `SignatureBindings` (~100 LOC)
4. `matchShape()` method (~60 LOC)

**Total: ~250 LOC**

**Is it complex?** No - it's mostly straightforward pattern matching across 4 `ShapeSpec` variants. The logic is simple and systematic.

### When to Choose Option 2b

**Choose arity variables if:**
- ✅ You want perfect spec alignment
- ✅ You have many cardinality-preserving functions (where, skip, take, repeat, etc.)
- ✅ You value elegant, concise signatures
- ✅ Team is comfortable with slightly more abstract concepts
- ✅ You want to eliminate signature duplication

**Stick with Option 2 basic if:**
- ✅ You prefer simpler implementation (fewer concepts)
- ✅ Team prefers concrete over abstract
- ✅ Two signatures per function is acceptable
- ✅ Custom ResultSpecs are sufficient for complex cases

### Recommended Migration

**If adopting Option 2:**

**Phase 1**: Start with basic Option 2 (no arity variables)
- Implement core type system changes
- Use two signatures for cardinality-preserving functions
- Get system working and tested

**Phase 2** (Optional): Add arity variables
- Implement `ArityVariable` and `ShapeSpec`
- Migrate cardinality-preserving functions
- Reduce signature count by ~40%
- Improved spec alignment

This incremental approach lets you validate the element-first model before adding the arity variable abstraction.

---

## Option 3: Hybrid Pragmatic (Simple + Custom Resolvers)

**Philosophy**: Keep type system simple for common cases, allow custom Java resolvers for complex polymorphic signatures. Get working code fast, migrate incrementally.

### Core Types (Keep Current!)

```java
// Keep existing Type hierarchy EXACTLY as is
// No changes to PrimitiveType, CollectionType, LambdaType

// Only add TypeVariable for future extensibility
public record TypeVariable(String name) implements Type {
    @Override public String getName() { return name; }
    @Override public boolean isPrimitive() { return false; }
    @Override public boolean isComplex() { return false; }
    @Override public boolean isCollection() { return false; }
}
```

### Two-Tier Signature System

```java
// Simple signatures for 90% of cases (keep existing)
public record SimpleSignature(
    List<Type> parameterTypes,
    ResultSpec resultSpec,
    int minArity
) implements SignatureDefinition {
    // Uses existing Type system
    // No type variables needed
}

// Custom resolver interface for complex polymorphic cases
public interface CustomSignatureResolver {
    /**
     * Attempt to resolve call with custom logic.
     * Returns null if cannot resolve.
     */
    @Nullable
    ResolvedSignature resolve(List<IRNode> args);
}

// Wrapper for custom resolvers
public record CustomSignature(
    String name,
    CustomSignatureResolver resolver
) implements SignatureDefinition {
    // Delegates to custom Java code
}
```

### Custom Resolver Examples

```java
// Custom resolver for 'in' operator: X in Collection<X>
public class InOperatorResolver implements CustomSignatureResolver {
    @Override
    public ResolvedSignature resolve(List<IRNode> args) {
        if (args.size() != 2) return null;

        Type elementType = args.get(0).getType();
        Type collectionType = args.get(1).getType();

        // Must be collection
        if (!(collectionType instanceof CollectionType ct)) {
            return null;
        }

        // Find common type between element and collection element
        Type common = findCommonType(elementType, ct.elementType());
        if (common == null) return null;

        // Build adapted arguments
        List<IRNode> adapted = List.of(
            adapt(args.get(0), common),
            adapt(args.get(1), new CollectionType(common))
        );

        return new ResolvedSignature(PrimitiveType.BOOLEAN, adapted);
    }

    private Type findCommonType(Type a, Type b) {
        // Simple common type logic
        if (TypeSystem.canCast(a, b)) return b;
        if (TypeSystem.canCast(b, a)) return a;

        // Try numeric widening
        if (a == PrimitiveType.INTEGER && b == PrimitiveType.DECIMAL) return b;
        if (a == PrimitiveType.DECIMAL && b == PrimitiveType.INTEGER) return a;

        return null;
    }
}

// Custom resolver for 'union' operator
public class UnionOperatorResolver implements CustomSignatureResolver {
    @Override
    public ResolvedSignature resolve(List<IRNode> args) {
        if (args.size() != 2) return null;

        Type type1 = args.get(0).getType();
        Type type2 = args.get(1).getType();

        // Extract element types if collections
        Type elem1 = type1 instanceof CollectionType ct ? ct.elementType() : type1;
        Type elem2 = type2 instanceof CollectionType ct ? ct.elementType() : type2;

        // Find common type
        Type common = findCommonType(elem1, elem2);
        if (common == null) return null;

        // Result is always a collection of common type
        Type resultType = new CollectionType(common);

        // Adapt arguments if needed
        List<IRNode> adapted = List.of(
            adapt(args.get(0), resultType),
            adapt(args.get(1), resultType)
        );

        return new ResolvedSignature(resultType, adapted);
    }

    private Type findCommonType(Type a, Type b) {
        // Same logic as InOperatorResolver
        // Could extract to shared utility
    }
}

// Custom resolver for 'iif' conditional
public class IifResolver implements CustomSignatureResolver {
    @Override
    public ResolvedSignature resolve(List<IRNode> args) {
        if (args.size() < 2 || args.size() > 3) return null;

        // First arg must be boolean lambda
        IRNode condition = args.get(0);
        if (!(condition instanceof Lambda lambda)) return null;
        if (lambda.body().getType() != PrimitiveType.BOOLEAN) return null;

        // Get true branch type
        IRNode trueBranch = args.get(1);
        Type trueType = trueBranch instanceof Lambda l
            ? l.body().getType()
            : trueBranch.getType();

        // Get false branch type (or empty if not provided)
        Type falseType = PrimitiveType.NULL;
        if (args.size() == 3) {
            IRNode falseBranch = args.get(2);
            falseType = falseBranch instanceof Lambda l
                ? l.body().getType()
                : falseBranch.getType();
        }

        // Find common type for branches
        Type resultType = findCommonType(trueType, falseType);
        if (resultType == null) resultType = PrimitiveType.ANY;

        return new ResolvedSignature(resultType, args);
    }

    private Type findCommonType(Type a, Type b) {
        // Same as above
    }
}
```

### Registry Integration

```java
public class OperationRegistry {
    private final Map<String, List<SignatureDefinition>> signatures;
    private final Map<String, CustomSignatureResolver> customResolvers;

    public void registerSimple(String name, SignatureDefinition sig) {
        signatures.computeIfAbsent(name, k -> new ArrayList<>()).add(sig);
    }

    public void registerCustom(String name, CustomSignatureResolver resolver) {
        customResolvers.put(name, resolver);
    }

    public ResolvedSignature resolve(String name, List<IRNode> args) {
        // Try custom resolver first (for problematic operators)
        CustomSignatureResolver custom = customResolvers.get(name);
        if (custom != null) {
            ResolvedSignature result = custom.resolve(args);
            if (result != null) return result;
        }

        // Fall back to standard signature matching
        return resolveSimple(name, args);
    }

    private ResolvedSignature resolveSimple(String name, List<IRNode> args) {
        // Existing overload resolution logic
        // Uses cost-based matching with existing Type system
    }
}
```

### Example Usage

```java
// Initialize registry
OperationRegistry registry = new OperationRegistry();

// Simple cases - use existing signature system (90% of operators)
registry.registerSimple("first", new SignatureDefinition(
    List.of(new CollectionType(PrimitiveType.ANY)),
    ResultSpec.EffectiveInputType.INSTANCE
));

registry.registerSimple("count", new SignatureDefinition(
    List.of(new CollectionType(PrimitiveType.ANY)),
    new ResultSpec.Static(PrimitiveType.INTEGER)
));

registry.registerSimple("substring", new SignatureDefinition(
    List.of(
        PrimitiveType.STRING,
        PrimitiveType.INTEGER,
        PrimitiveType.INTEGER
    ),
    new ResultSpec.Static(PrimitiveType.STRING),
    2  // minArity for optional parameter
));

// Complex polymorphic cases - use custom resolvers (10% of operators)
registry.registerCustom("in", new InOperatorResolver());
registry.registerCustom("union", new UnionOperatorResolver());
registry.registerCustom("iif", new IifResolver());
registry.registerCustom("contains", new ContainsOperatorResolver());
```

### Incremental Migration Path

```java
// Phase 1 (Week 1): Use custom resolvers for problematic operators
// - Implement InOperatorResolver, UnionOperatorResolver, IifResolver
// - Zero breaking changes, gets polymorphic operators working today
// - Validates approach with real code

// Phase 2 (Weeks 2-3): Add TypeVariable support to SimpleSignature
// - Add TypeVariable to Type hierarchy
// - Enhance signature matching to handle type variables
// - Add TypeBindings and unification to resolver
// - Migrate some custom resolvers to declarative signatures

// Phase 3 (Week 4+): Add common type computation to registry
// - Extract findCommonType to TypeAdapter utility
// - Implement adaptation closure computation
// - Migrate remaining custom resolvers

// Phase 4 (Optional): Migrate to Option 1 or 2 if needed
// - If custom resolvers become too complex
// - If more operators need polymorphism
// - If type system evolution is desired
```

### Pros

✅ **Zero breaking changes** - works with existing code
✅ **Immediate solution** - can implement today (1 week)
✅ **Pragmatic** - simple for simple, powerful for complex
✅ **Incremental** - can evolve toward Option 1 or 2
✅ **Flexible** - custom logic for edge cases
✅ **Easy to understand** - clear when to use each approach
✅ **Low risk** - existing system continues to work
✅ **Quick wins** - solves immediate polymorphic signature needs

### Cons

❌ **Two mechanisms** - signatures + custom resolvers
❌ **Less declarative** - polymorphic signatures in Java code
❌ **Maintenance** - custom resolvers are code, not data
❌ **Less type safe** - custom resolvers can have bugs
❌ **Code duplication** - findCommonType repeated
❌ **Temporary solution** - will want to migrate eventually

### Migration Effort

**Low** (1 week)
- Add CustomSignatureResolver interface
- Implement 3-4 custom resolvers
- Update OperationRegistry to support both mechanisms
- No changes to existing Type system or IRNode

---

## Comparison Matrix

| Aspect | Option 2: Element-First Pure | Option 2b: Element-First with Arity Variables | Option 3: Hybrid Pragmatic |
|--------|------------------------------|----------------------------------------------|----------------------------|
| **Type System Complexity** | Low | Low | Low (reuses existing) |
| **Signature Clarity** | Excellent (minimal) | Excellent (minimal) | Good (simple cases) |
| **Signature Verbosity** | Low (direct construction) | Very Low (with arity variables) | Low (existing style) |
| **Adaptation Rules** | 5 rules | 5 rules | 6 rules (existing) |
| **Breaking Changes** | Remove CollectionType | Remove CollectionType | None |
| **Migration Effort** | High (3-4 weeks) | High + 1 week for arity variables | Minimal (1 week) |
| **Polymorphic Signatures** | Declarative (static) | Declarative (static, more concise) | Programmatic (Java) |
| **Spec Alignment** | Excellent | Excellent | Moderate |
| **Extensibility** | Excellent | Excellent | Good |
| **Common Case Simplicity** | Excellent | Excellent | Excellent |
| **Complex Case Support** | Excellent | Excellent | Very Good |
| **Maintenance Burden** | Low | Low | Medium |
| **Type Safety** | Excellent | Excellent | Good |
| **Learning Curve** | Low (simple records) | Low (simple records) | Low (familiar) |
| **Implementation Risk** | High | High | Low |
| **Code Quality** | Excellent | Excellent | Good |
| **Long-Term Viability** | Excellent | Excellent | Temporary |
| **Testing Impact** | High | High | Low |
| **Documentation Needs** | High | High | Low |

---

## Recommendation

### Primary: **Option 2 (Element-First Pure)** - Best Long-Term Solution

**Why Option 2 is the best choice:**

1. **Truest to spec** - TYPE_SYSTEM.md explicitly uses element-first model with separate cardinality
2. **Simplest type algebra** - No CollectionType complexity, minimal adaptation rules
3. **Cleanest signatures** - `List.of(manyVar("T"), manyVar("T"))` is beautifully concise
4. **Best maintainability** - Clear separation between semantic types and cardinality
5. **Matches expert analysis** - Aligns with Option 3 recommendation from TYPE_SYSTEM_DESIGN_OPTIONS_EXPERT_ANALYSIS.md
6. **Future-proof** - Extensible for future FHIRPath evolution

**When to choose Option 2:**
- You can afford 3-4 weeks for migration
- Breaking changes to IRNode are acceptable
- You want the cleanest long-term design
- You value spec alignment and simplicity

### Secondary: **Option 3 (Hybrid Pragmatic)** - Quick Win Migration Path

**Why Option 3 is valuable:**

1. **Immediate results** - Working polymorphic operators in 1 week
2. **Zero risk** - No breaking changes
3. **Validation** - Proves approach before full commitment
4. **Incremental** - Can evolve toward Option 2 later

**When to choose Option 3:**
- You need polymorphic operators working immediately
- Cannot afford breaking changes right now
- Want to validate approach before larger investment
- Need time to plan full migration

---

## Adopted Approach: Option 2 (Element-First Pure) with Phased Implementation

**Decision Date**: 2025-10-18

After analysis of all options and working through concrete examples (`+`, `where`, `iif`, `union`), the decision is to adopt **Option 2: Element-First Pure** for the following reasons:

1. **Best spec alignment**: Matches TYPE_SYSTEM.md's element-first model exactly
2. **Simplest type algebra**: Only ~5 adaptation rules, no CollectionType complexity
3. **Clean signatures**: Minimal verbosity, self-documenting
4. **Long-term maintainability**: Clear separation between semantic types and cardinality
5. **Proven approach**: Advanced signature examples demonstrate it handles all cases

### Key Design Decisions

**Type System Architecture**:
- **Remove `CollectionType`** from Type hierarchy entirely
- **Cardinality as metadata**: IRNode carries both Type and Cardinality separately
- **Shape abstraction**: Convenient wrapper combining (Type, Cardinality)
- **Type hierarchy**: `PrimitiveType | ComplexType | FhirType | LambdaType | Bottom` only

**Constraint Handling** (deferred to Phase 2):
- TypeVariable will carry optional constraint predicate
- Constraint checked on binding AND unification
- Unified LUB computation ensures result satisfies constraints

### Phased Implementation Strategy

To reduce risk and enable incremental delivery, implementation is split into phases:

#### Phase 1: Simple Signatures (Foundation)

**Scope: Implement core type system without type variables or constraints**

**Core Type System Changes**:
```java
// New: Cardinality enum
public enum Cardinality {
    SINGLE,  // 0..1
    MANY;    // 0..*

    public Cardinality join(Cardinality other) {
        return (this == MANY || other == MANY) ? MANY : SINGLE;
    }
}

// New: Shape abstraction (Type + Cardinality)
public sealed interface Shape {
    Type elementType();
    Cardinality cardinality();

    record Single(Type elementType) implements Shape {
        @Override public Cardinality cardinality() { return Cardinality.SINGLE; }
    }

    record Many(Type elementType) implements Shape {
        @Override public Cardinality cardinality() { return Cardinality.MANY; }
    }
}

// REMOVED: CollectionType (no longer exists!)
// Type hierarchy now: PrimitiveType | ComplexType | FhirType | LambdaType | Bottom
```

**IRNode Changes**:
```java
public sealed interface IRNode {
    @Nonnull Shape getShape();  // Primary method

    // Convenience helpers
    default Type getType() { return getShape().elementType(); }
    default Cardinality getCardinality() { return getShape().cardinality(); }
}

// All concrete nodes updated:
public record Literal(Object value, Type type) implements IRNode {
    @Override public Shape getShape() {
        return new Shape.Single(type);  // Literals always SINGLE
    }
}

public record Operation(
    String name,
    List<IRNode> arguments,
    Shape resultShape  // Resolved from signature
) implements IRNode {
    @Override public Shape getShape() { return resultShape; }
}
```

**Signature System** (simplified for Phase 1):
```java
// Simple parameter spec: just type + cardinality
public record ParamSpec(Type type, Cardinality cardinality) {
    public static ParamSpec single(Type t) {
        return new ParamSpec(t, Cardinality.SINGLE);
    }
    public static ParamSpec many(Type t) {
        return new ParamSpec(t, Cardinality.MANY);
    }
}

// Simple result spec: just type + cardinality
public record ResultTypeSpec(Type type, Cardinality cardinality) {
    public static ResultTypeSpec single(Type t) {
        return new ResultTypeSpec(t, Cardinality.SINGLE);
    }
    public static ResultTypeSpec many(Type t) {
        return new ResultTypeSpec(t, Cardinality.MANY);
    }
}

// Signature definition
public record SignatureDefinition(
    List<ParamSpec> parameters,
    ResultTypeSpec resultSpec,
    int minArity
) {
    public SignatureDefinition(List<ParamSpec> params, ResultTypeSpec result) {
        this(params, result, params.size());
    }
}
```

**Signature Definitions** (enumerate types explicitly):
```java
import static ParamSpec.*;
import static ResultTypeSpec.*;
import static PrimitiveType.*;

// Arithmetic: separate signature for each type
public static final SignatureDefinition PLUS_INTEGER = new SignatureDefinition(
    List.of(single(INTEGER), single(INTEGER)),
    single(INTEGER)
);

public static final SignatureDefinition PLUS_DECIMAL = new SignatureDefinition(
    List.of(single(DECIMAL), single(DECIMAL)),
    single(DECIMAL)
);

public static final SignatureDefinition PLUS_QUANTITY = new SignatureDefinition(
    List.of(single(QUANTITY), single(QUANTITY)),
    single(QUANTITY)
);

// Register all overloads
registry.register("+", PLUS_INTEGER);
registry.register("+", PLUS_DECIMAL);
registry.register("+", PLUS_QUANTITY);

// Comparison: enumerate each comparable type
public static final SignatureDefinition GT_INTEGER = new SignatureDefinition(
    List.of(single(INTEGER), single(INTEGER)),
    single(BOOLEAN)
);

public static final SignatureDefinition GT_DECIMAL = new SignatureDefinition(
    List.of(single(DECIMAL), single(DECIMAL)),
    single(BOOLEAN)
);

// String functions (already simple)
public static final SignatureDefinition SUBSTRING = new SignatureDefinition(
    List.of(single(STRING), single(INTEGER), single(INTEGER)),
    single(STRING),
    2  // minArity for optional parameter
);

// Simple collection functions
public static final SignatureDefinition COUNT = new SignatureDefinition(
    List.of(many(ANY)),
    single(INTEGER)
);

public static final SignatureDefinition FIRST = new SignatureDefinition(
    List.of(many(ANY)),
    single(ANY)
);
```

**What Works in Phase 1**:
- ✅ Arithmetic on same types: `2 + 3`, `2.5 + 1.5`
- ✅ Comparison on same types: `2 > 1`, `"a" < "b"`
- ✅ String functions: `"hello".substring(0, 2)`
- ✅ Simple collection functions: `items.count()`, `items.first()`
- ✅ Literals and traversals with correct shapes

**What's Deferred to Phase 2**:
- ❌ Mixed-type arithmetic: `2 + 2.5` (needs type variables)
- ❌ Type constraints: `abs(?T)` where T ∈ Arithmetic
- ❌ Polymorphic operators: `union`, `in`, `contains`
- ❌ Cardinality-preserving: `where`, `skip`, `take`
- ❌ Dynamic resolution: `iif`
- ❌ Custom ResultSpecs

**Testing Strategy for Phase 1**:
- Update all existing tests to use Shape API
- Comment out tests requiring Phase 2 features with `// TODO: Phase 2 - type variables`
- Add tests for simple signatures to validate foundation
- Document test coverage gaps

### Phase 1.5: Cardinality Enforcement (Implemented)

After implementing the core Phase 1 type system, we added **compile-time cardinality checking** to enforce FHIRPath specification requirements for math and comparison operators.

**FHIRPath Specification Requirements**:
- **Section 3559-3566**: Math operators require each operand to be a **single element**. If there is more than one item, the evaluator will signal an error.
- **Section 3196-3197**: Comparison operators require collections with **single values**. The evaluator will throw an error if either collection has more than one item.

**Implementation**:

*Exception Hierarchy*:
- `AnalysisException`: Base exception for analysis-time errors
- `CardinalityMismatchException`: Thrown when argument cardinality doesn't match parameter requirements

*Cardinality Checking*:
- Added `checkCardinality()` to `OverloadResolver`
- Validates that SINGLE-cardinality parameters reject MANY-cardinality arguments
- Skips Lambda arguments (have special matching logic)
- Provides detailed error messages referencing FHIRPath spec sections

**Examples**:
```java
// ERRORS - Cardinality violations detected at analysis time:
(1 | 2) + 2              // CardinalityMismatchException: add requires SINGLE
(1 | 2) > 5              // CardinalityMismatchException: gt requires SINGLE
name + 'suffix'          // Error if name is MANY-valued field

// VALID - Proper cardinality usage:
1 + 2                    // Both operands SINGLE ✓
(1 | 2).first() + 3      // first() extracts SINGLE element ✓
(1 | 2).count()          // count() accepts MANY ✓
```

**Test Coverage**:
- 50 parameterized test cases in `CardinalityErrorTest`
- 40 error cases (math + comparison operators)
- 10 valid cases (collection operations, single operands)

**Capabilities**:

*Phase 1 (Before cardinality checking)*:
- ✅ Type checking (INTEGER vs STRING)
- ❌ No cardinality enforcement - MANY accepted where SINGLE required

*Phase 1.5 (With cardinality checking)*:
- ✅ Type checking
- ✅ Cardinality enforcement per FHIRPath spec
- ✅ Clear error messages for violations
- ❌ Still no type variables or polymorphism (Phase 2)

### Phase 1.6: Architectural Refinements (Implemented)

**Date**: 2025-10-19 - 2025-10-20

Following Phase 1 implementation, we conducted architectural review and implemented HIGH IMPACT/LOW RISK refactorings to improve code organization and maintainability.

**Package Reorganization** (commit ad75725):
```
Extracted operation resolution to dedicated package hierarchy:

com.example.fhirpath/operation/
├── OperationResolver.java          # Facade providing clean API
├── OperationRegistry.java          # Maps operation names to signatures
├── OverloadResolver.java           # Selects best signature for arguments
├── OperatorNormalizer.java         # Normalizes operator symbols
└── InfrastructureOperationHandler.java  # Special handling for equals/union

com.example.fhirpath/operation/signature/
├── SignatureDefinition.java        # Core signature specification
├── ParamSpec.java                  # Parameter type + cardinality
├── ResultTypeSpec.java             # Result type + cardinality
├── ResolvedSignature.java          # Post-resolution signature
├── LambdaBindingStrategy.java      # ELEMENT_WISE vs COLLECTION_WISE
├── TypeGroup.java                  # Type constraint predicates
├── TypeGroups.java                 # Standard type group definitions
├── TypeMapping.java                # Type variable substitution
└── Signatures.java                 # Signature definitions for operators
```

**Benefits**:
- ✅ Clear separation: operation resolution is now a distinct subsystem
- ✅ Facade pattern: OperationResolver hides implementation complexity
- ✅ Better SRP: Each class has focused responsibility
- ✅ Easier to extend: Adding new operations requires changes to isolated files

**Code Quality Improvements** (commits ac14afd, 7f22e13):

1. **Removed legacy FunctionSignature class** (42 LOC)
   - Superseded by SignatureDefinition
   - Eliminated conceptual confusion (two signature abstractions)
   - Cleaned up unused imports in IR package

2. **Extracted lambda binding logic** (Analyzer.java)
   - New method: `createLambdaAnalyzer(sig, targetIR)`
   - Reduced `resolveFunctionCall` from 77 → 62 lines
   - Improved readability and separation of concerns

3. **Created reusable slash commands**
   - `/review-branch` - Comprehensive branch review with code-reviewer agent
   - `/analyze-architecture` - SOLID principle evaluation with code-refactoring agent
   - `/high-impact-refactorings` - Execute safe improvements systematically

**Architectural Quality** (post-refactoring):
- ✅ Excellent SOLID compliance (4.6/5 rating)
- ✅ Clean package boundaries
- ✅ All 193 tests passing
- ✅ Zero behavioral changes
- ✅ Net reduction: -36 lines of code

**Status**: Phase 1 implementation is **COMPLETE** with production-ready architecture.

#### Phase 2: Type Variables and Constraints (Future)

**Scope: Add polymorphism and constraints to enable full spec compliance**

Will add:
- `TypeVariable` carrying optional constraints
- `TypeBindings` with unification and LUB computation
- Enhanced `ParamSpec` and `ResultTypeSpec` supporting type variables
- Custom `ResultSpec` implementations (PreserveInputShape, IifBranchLUB)
- Migration of enumerated signatures to use type variables

This phase enables all deferred operators and achieves full TYPE_SYSTEM.md compliance.

#### Phase 3: Arity Variables (Optional)

**Scope: Add arity variables for perfect spec alignment (Option 2b)**

If desired, can later add:
- `ArityVariable` and `ShapeSpec` abstractions
- Cardinality-polymorphic signatures
- Further reduction in signature count

This is optional - Phase 2 is sufficient for full functionality.

### Implementation Timeline

**Completed:**
- **Phase 1**: ✅ COMPLETE (core type system + simple signatures)
- **Phase 1.5**: ✅ COMPLETE (cardinality enforcement)
- **Phase 1.6**: ✅ COMPLETE (architectural refinements)

**Future:**
- **Phase 2**: 2-3 weeks (type variables + advanced signatures)
- **Phase 3**: 1 week (optional arity variables)

**Current Status**: Phase 1 implementation complete with production-ready architecture. All 193 tests passing. Ready for Phase 2 implementation when needed.

---

## Implementation Examples

### Option 2: Complete Example

```java
// ============================================================================
// Type System
// ============================================================================
public enum PrimitiveType implements Type {
    INTEGER, DECIMAL, STRING, BOOLEAN, DATE, DATE_TIME, TIME, QUANTITY, ANY;
    // ... implementation
}

public record TypeVariable(String name) implements Type { }

public enum Bottom implements Type { INSTANCE; }

public enum Cardinality {
    SINGLE, MANY;
    public Cardinality join(Cardinality other) {
        return (this == MANY || other == MANY) ? MANY : SINGLE;
    }
}

// ============================================================================
// Signatures
// ============================================================================
public record ParamSpec(
    Type type,
    Cardinality cardinality,
    Predicate<Type> constraint
) {
    public static ParamSpec singleVar(String name) {
        return new ParamSpec(new TypeVariable(name), Cardinality.SINGLE, null);
    }

    public static ParamSpec manyVar(String name) {
        return new ParamSpec(new TypeVariable(name), Cardinality.MANY, null);
    }

    public static ParamSpec single(Type t) {
        return new ParamSpec(t, Cardinality.SINGLE, null);
    }

    public static ParamSpec many(Type t) {
        return new ParamSpec(t, Cardinality.MANY, null);
    }
}

public record ResultTypeSpec(Type type, Cardinality cardinality) {
    public static ResultTypeSpec singleVar(String name) {
        return new ResultTypeSpec(new TypeVariable(name), Cardinality.SINGLE);
    }

    public static ResultTypeSpec manyVar(String name) {
        return new ResultTypeSpec(new TypeVariable(name), Cardinality.MANY);
    }

    public static ResultTypeSpec single(Type t) {
        return new ResultTypeSpec(t, Cardinality.SINGLE);
    }

    public static ResultTypeSpec many(Type t) {
        return new ResultTypeSpec(t, Cardinality.MANY);
    }
}

public record SignatureDefinition(
    List<ParamSpec> parameters,
    ResultTypeSpec resultSpec,
    int minArity
) { }

// ============================================================================
// Example Signatures
// ============================================================================
import static ParamSpec.*;
import static ResultTypeSpec.*;
import static PrimitiveType.*;

public class Signatures {
    // union: ∀ K, L, α, β. [LUB(K, L) defined] ⇒ union(α K, β L) → *LUB(K, L)
    public static final SignatureDefinition UNION = new SignatureDefinition(
        List.of(manyVar("T"), manyVar("T")),
        manyVar("T")
    );

    // in: ∀ X. in(?X, *X) → ?BOOLEAN
    public static final SignatureDefinition IN = new SignatureDefinition(
        List.of(singleVar("X"), manyVar("X")),
        single(BOOLEAN)
    );

    // first: ∀ T. first(*T) → ?T
    public static final SignatureDefinition FIRST = new SignatureDefinition(
        List.of(manyVar("T")),
        singleVar("T")
    );

    // where: ∀ T. where(*T, Lambda(?BOOLEAN)) → *T
    public static final SignatureDefinition WHERE = new SignatureDefinition(
        List.of(manyVar("T"), single(new LambdaType(BOOLEAN))),
        manyVar("T")
    );

    // count: ∀ T. count(*T) → ?INTEGER
    public static final SignatureDefinition COUNT = new SignatureDefinition(
        List.of(many(ANY)),
        single(INTEGER)
    );
}

// ============================================================================
// Type Adapter (Simplified!)
// ============================================================================
public class TypeAdapter {
    public static int adaptationCost(Type from, Type to) {
        if (from.equals(to)) return 0;
        if (from == Bottom.INSTANCE) return 0;
        if (to == PrimitiveType.ANY) return 0;
        if (to instanceof TypeVariable) return 0;

        // Numeric widening
        if (from == INTEGER && to == DECIMAL) return 1;
        if (from == INTEGER && to == LONG) return 1;
        if (from == LONG && to == DECIMAL) return 1;

        // To quantity
        if (isNumeric(from) && to == QUANTITY) return 1;

        // Temporal
        if (from == DATE && to == DATE_TIME) return 1;

        // FHIR unwrap
        if (from instanceof FhirType ft) {
            return 1 + adaptationCost(ft.systemType(), to);
        }

        // Lambda covariance
        if (from instanceof LambdaType lf && to instanceof LambdaType lt) {
            return adaptationCost(lf.returnType(), lt.returnType());
        }

        return Integer.MAX_VALUE;
    }

    public static Type commonType(Type a, Type b) {
        if (a.equals(b)) return a;
        if (a == Bottom.INSTANCE) return b;
        if (b == Bottom.INSTANCE) return a;

        // Compute closures and find best common type
        Map<Type, Integer> closureA = computeAdaptationClosure(a);
        Map<Type, Integer> closureB = computeAdaptationClosure(b);

        return closureA.keySet().stream()
            .filter(closureB::containsKey)
            .min(Comparator.comparingInt(t -> closureA.get(t) + closureB.get(t)))
            .orElse(null);
    }
}
```


