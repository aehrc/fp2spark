# OperationRegistry Design Options

**Date:** 2025-10-13
**Status:** Architecture Proposal
**Principal Architect Review**

---

## Executive Summary

This document presents **four distinct design options** for improving the OperationRegistry and Signatures infrastructure in the FHIRPath→SQL translator. Each option prioritizes **readability, immutability, single registration per operation, and explicit multi-type patterns** while maintaining the existing four-type ResultSpec system (Static, InputType, EffectiveInputType, FhirSystemType).

### Current State Analysis

**Strengths:**
- Clear separation between `SignatureDefinition` (registry) and `ResolvedSignature` (IR)
- Factory methods in `Signatures` class reduce boilerplate
- Well-defined `ResultSpec` sealed hierarchy captures four type resolution patterns
- Single registry class with static initialization

**Areas for Improvement:**
1. **Multi-registration issue:** `OPERATIONS.put("add", appendSignature(...))` updates existing entries, violating single-registration principle
2. **Type repetition:** Helper methods like `registerBinaryOp` iterate over types, making it unclear which types are supported at a glance
3. **Type set reuse:** No predefined type sets (NUMERIC_TYPES, COMPARABLE_TYPES) for consistency
4. **Readability:** Multi-step registration for special cases (e.g., DateTime + Quantity) is hard to follow
5. **Not truly immutable:** Uses HashMap that gets mutated during static initialization

---

## Design Principles (Requirements)

All proposed designs must satisfy:

1. **Readability over brevity** - Signature definitions should be self-documenting
2. **Immutable singleton** - Registry is truly immutable after construction
3. **Single registration** - All overloads for an operation registered in one call (no `appendSignature`)
4. **Stream-based composition** - Use `Stream<SignatureDefinition>` for composability
5. **Explicit multi-type patterns** - Make it obvious when same signature applies to multiple types
6. **Predefined type sets** - Reusable constants like `NUMERIC_TYPES`, `COMPARABLE_TYPES`
7. **Builder pattern consideration** - Use if it improves clarity
8. **Support all 4 ResultSpec types** - Static, InputType, EffectiveInputType, FhirSystemType

---

## Option 1: Fluent Registry Builder with Type Sets

### Design Approach

Use a **fluent builder pattern** to construct an immutable registry. Introduce **predefined type sets** and a **`forTypes()` method** to explicitly declare multi-type signatures. All registration happens in a single builder chain per operation.

### Architecture

```java
// TypeSets.java - Predefined type collections
public final class TypeSets {
    public static final Set<Type> NUMERIC = Set.of(INTEGER, DECIMAL);
    public static final Set<Type> NUMERIC_WITH_QUANTITY = Set.of(INTEGER, DECIMAL, QUANTITY);
    public static final Set<Type> COMPARABLE = Set.of(INTEGER, DECIMAL, STRING, QUANTITY,
                                                       DATE, DATE_TIME, TIME);
    public static final Set<Type> TEMPORAL = Set.of(DATE, DATE_TIME, TIME);
    public static final Set<Type> STRING_LIKE = Set.of(STRING);
}

// RegistryBuilder.java - Fluent builder for signatures
public final class RegistryBuilder {
    private final Map<String, List<SignatureDefinition>> operations = new HashMap<>();

    // Start building signatures for an operation
    public OperationBuilder operation(String name) {
        return new OperationBuilder(name, this);
    }

    // Seal and return immutable registry
    public OperationRegistry build() {
        return new OperationRegistry(Map.copyOf(operations));
    }

    void register(String name, List<SignatureDefinition> signatures) {
        if (operations.containsKey(name)) {
            throw new IllegalStateException("Already registered: " + name);
        }
        operations.put(name, List.copyOf(signatures));
    }
}

// OperationBuilder.java - Per-operation fluent builder
public final class OperationBuilder {
    private final String name;
    private final RegistryBuilder parent;
    private final List<SignatureDefinition> signatures = new ArrayList<>();

    // For multiple types with same signature pattern
    public OperationBuilder forTypes(Type... types) {
        return new TypedOperationBuilder(name, parent, signatures, Set.of(types));
    }

    // For type sets
    public OperationBuilder forTypes(Set<Type> typeSet) {
        return new TypedOperationBuilder(name, parent, signatures, typeSet);
    }

    // For single explicit signature
    public OperationBuilder signature(SignatureDefinition sig) {
        signatures.add(sig);
        return this;
    }

    // Complete this operation's registration
    public RegistryBuilder done() {
        parent.register(name, signatures);
        return parent;
    }
}

// TypedOperationBuilder.java - For multi-type patterns
public final class TypedOperationBuilder extends OperationBuilder {
    private final Set<Type> types;

    public OperationBuilder unaryOp() {
        types.forEach(t -> signatures.add(Signatures.unaryOp(t, t)));
        return this;
    }

    public OperationBuilder binaryOp() {
        types.forEach(t -> signatures.add(Signatures.binaryOp(t, t, t)));
        return this;
    }

    public OperationBuilder comparison() {
        types.forEach(t -> signatures.add(Signatures.comparisonOp(t, t)));
        return this;
    }

    public OperationBuilder with(Function<Type, SignatureDefinition> factory) {
        types.forEach(t -> signatures.add(factory.apply(t)));
        return this;
    }
}

// OperationRegistry.java - Immutable registry
public final class OperationRegistry {
    private static final OperationRegistry INSTANCE = buildRegistry();
    private final Map<String, List<SignatureDefinition>> operations;

    private OperationRegistry(Map<String, List<SignatureDefinition>> operations) {
        this.operations = operations; // Already immutable from builder
    }

    private static OperationRegistry buildRegistry() {
        return new RegistryBuilder()
            // Arithmetic - numeric types
            .operation("add")
                .forTypes(TypeSets.NUMERIC_WITH_QUANTITY).binaryOp()
                .forTypes(STRING).binaryOp()
                .signature(Signatures.binaryOp(DATE_TIME, QUANTITY, DATE_TIME))
                .done()

            // Math functions
            .operation("abs")
                .forTypes(TypeSets.NUMERIC_WITH_QUANTITY).unaryOp()
                .done()

            .operation("sqrt")
                .forTypes(TypeSets.NUMERIC).unaryOp()
                .done()

            // String functions
            .operation("substring")
                .signature(Signatures.variadic(
                    List.of(STRING, INTEGER, INTEGER), STRING, 2))
                .done()

            .operation("upper")
                .signature(Signatures.stringOp(STRING))
                .done()

            // Comparison operators
            .operation("gt")
                .forTypes(TypeSets.COMPARABLE).comparison()
                .done()

            // Collection operations
            .operation("first")
                .signature(Signatures.elementExtractor(ANY))
                .done()

            .operation("count")
                .signature(Signatures.collectionAggregator(ANY, INTEGER))
                .done()

            .build();
    }

    public static List<SignatureDefinition> getSignatures(String name) {
        return INSTANCE.operations.getOrDefault(name, List.of());
    }
}
```

### Code Examples

#### Simple Signature (abs)
```java
.operation("abs")
    .forTypes(TypeSets.NUMERIC_WITH_QUANTITY).unaryOp()
    .done()

// Generated signatures:
// abs(Integer) → Integer
// abs(Decimal) → Decimal
// abs(Quantity) → Quantity
```

#### Multi-Type Signature (comparison)
```java
.operation("gt")
    .forTypes(TypeSets.COMPARABLE).comparison()
    .done()

// Generated signatures:
// gt(Integer, Integer) → Boolean
// gt(Decimal, Decimal) → Boolean
// gt(String, String) → Boolean
// gt(Quantity, Quantity) → Boolean
// gt(Date, Date) → Boolean
// gt(DateTime, DateTime) → Boolean
// gt(Time, Time) → Boolean
```

#### Complex Signature (substring with optional params)
```java
.operation("substring")
    .signature(Signatures.variadic(
        List.of(STRING, INTEGER, INTEGER),  // All param types
        STRING,                              // Result type
        2                                    // Min arity (length is optional)
    ))
    .done()

// Signature: substring(String, Integer, Integer?) → String
// Accepts 2 or 3 arguments
```

#### Mixed Signature Types (add)
```java
.operation("add")
    .forTypes(TypeSets.NUMERIC_WITH_QUANTITY).binaryOp()
    .forTypes(STRING).binaryOp()
    .signature(Signatures.binaryOp(DATE_TIME, QUANTITY, DATE_TIME))
    .done()

// Generated signatures:
// add(Integer, Integer) → Integer
// add(Decimal, Decimal) → Decimal
// add(Quantity, Quantity) → Quantity
// add(String, String) → String
// add(DateTime, Quantity) → DateTime
```

### ResultSpec Handling

All four ResultSpec types are supported through `Signatures` factory methods:

```java
// 1. Static - most signatures use this
Signatures.unaryOp(INTEGER, DECIMAL)  // Static(DECIMAL)

// 2. InputType - collection preservers
Signatures.collectionPreserver(ANY, BOOLEAN)  // InputType.INSTANCE

// 3. EffectiveInputType - element extractors
Signatures.elementExtractor(ANY)  // EffectiveInputType.INSTANCE

// 4. FhirSystemType - getValue()
Signatures.fhirValueExtractor(fhirStringType)  // FhirSystemType.INSTANCE
```

### Pros

1. **Extremely readable** - Builder chain reads like natural language
2. **Type sets are explicit** - `forTypes(TypeSets.COMPARABLE)` is self-documenting
3. **Single registration guaranteed** - Builder enforces it
4. **Truly immutable** - `Map.copyOf()` ensures immutability
5. **Flexible** - Mix `forTypes()` with explicit `signature()` calls
6. **Stream-ready** - Can convert type sets to streams internally
7. **Type safety** - Compile-time checks for builder methods

### Cons

1. **More classes** - Requires RegistryBuilder, OperationBuilder, TypedOperationBuilder
2. **Verbosity** - `.done()` calls add noise
3. **Learning curve** - Developers must understand builder pattern
4. **Complex implementation** - Builder state management

### Comparison with Current Design

| Aspect | Current | Option 1 |
|--------|---------|----------|
| **Multi-registration** | Possible via `appendSignature` | Prevented by builder |
| **Type visibility** | Hidden in loops | Explicit in type sets |
| **Immutability** | HashMap mutated | True immutability |
| **Readability** | Moderate | High |
| **Lines of code** | Fewer | More (but clearer) |

---

## Option 2: Declarative Stream-Based Registry

### Design Approach

Use **Java Streams** with **flatMap** to compose signatures declaratively. Type sets are expanded into streams of SignatureDefinitions. The registry is constructed from a single stream expression per operation.

### Architecture

```java
// TypeSets.java - Same as Option 1
public final class TypeSets {
    public static final Set<Type> NUMERIC = Set.of(INTEGER, DECIMAL);
    public static final Set<Type> NUMERIC_WITH_QUANTITY = Set.of(INTEGER, DECIMAL, QUANTITY);
    public static final Set<Type> COMPARABLE = Set.of(INTEGER, DECIMAL, STRING, QUANTITY,
                                                       DATE, DATE_TIME, TIME);
    // ... other sets
}

// SignatureStreams.java - Stream-producing factory methods
public final class SignatureStreams {

    // Expand type set into stream of unary signatures
    public static Stream<SignatureDefinition> unaryOps(Set<Type> types) {
        return types.stream().map(t -> Signatures.unaryOp(t, t));
    }

    // Expand type set into stream of binary signatures
    public static Stream<SignatureDefinition> binaryOps(Set<Type> types) {
        return types.stream().map(t -> Signatures.binaryOp(t, t, t));
    }

    // Expand type set into stream of comparison signatures
    public static Stream<SignatureDefinition> comparisons(Set<Type> types) {
        return types.stream().map(t -> Signatures.comparisonOp(t, t));
    }

    // Single signature as stream
    public static Stream<SignatureDefinition> of(SignatureDefinition sig) {
        return Stream.of(sig);
    }

    // Multiple signatures as stream
    public static Stream<SignatureDefinition> of(SignatureDefinition... sigs) {
        return Stream.of(sigs);
    }

    // Custom mapping from type to signature
    public static Stream<SignatureDefinition> map(Set<Type> types,
                                                   Function<Type, SignatureDefinition> fn) {
        return types.stream().map(fn);
    }
}

// OperationRegistry.java - Declarative registry
public final class OperationRegistry {

    private static final Map<String, List<SignatureDefinition>> OPERATIONS =
        buildRegistry();

    private OperationRegistry() {
        // Singleton - no instantiation
    }

    private static Map<String, List<SignatureDefinition>> buildRegistry() {
        return Map.ofEntries(

            // Arithmetic operators
            register("add", Stream.of(
                SignatureStreams.binaryOps(TypeSets.NUMERIC_WITH_QUANTITY),
                SignatureStreams.binaryOps(TypeSets.STRING_LIKE),
                SignatureStreams.of(Signatures.binaryOp(DATE_TIME, QUANTITY, DATE_TIME))
            )),

            register("sub", Stream.of(
                SignatureStreams.binaryOps(TypeSets.NUMERIC_WITH_QUANTITY),
                SignatureStreams.of(Signatures.binaryOp(DATE_TIME, QUANTITY, DATE_TIME))
            )),

            register("multiply",
                SignatureStreams.binaryOps(TypeSets.NUMERIC_WITH_QUANTITY)
            ),

            register("divide",
                SignatureStreams.binaryOps(TypeSets.NUMERIC_WITH_QUANTITY)
            ),

            register("mod",
                SignatureStreams.binaryOps(TypeSets.NUMERIC)
            ),

            // Math functions
            register("abs",
                SignatureStreams.unaryOps(TypeSets.NUMERIC_WITH_QUANTITY)
            ),

            register("sqrt",
                SignatureStreams.unaryOps(TypeSets.NUMERIC)
            ),

            register("ceiling",
                SignatureStreams.unaryOps(TypeSets.NUMERIC)
            ),

            // String functions
            register("substring",
                SignatureStreams.of(
                    Signatures.variadic(List.of(STRING, INTEGER, INTEGER), STRING, 2)
                )
            ),

            register("upper",
                SignatureStreams.of(Signatures.stringOp(STRING))
            ),

            register("startsWith",
                SignatureStreams.of(Signatures.binaryOp(STRING, STRING, BOOLEAN))
            ),

            // Comparison operators
            register("gt",
                SignatureStreams.comparisons(TypeSets.COMPARABLE)
            ),

            register("lt",
                SignatureStreams.comparisons(TypeSets.COMPARABLE)
            ),

            // Collection operations
            register("first",
                SignatureStreams.of(Signatures.elementExtractor(ANY))
            ),

            register("count",
                SignatureStreams.of(Signatures.collectionAggregator(ANY, INTEGER))
            ),

            register("empty",
                SignatureStreams.of(Signatures.collectionAggregator(ANY, BOOLEAN))
            )
        );
    }

    // Helper: convert stream to Map.Entry
    private static Map.Entry<String, List<SignatureDefinition>> register(
            String name,
            Stream<SignatureDefinition> signatures) {
        return Map.entry(name, signatures.toList());
    }

    // Helper: flatten multiple streams
    private static Map.Entry<String, List<SignatureDefinition>> register(
            String name,
            Stream<Stream<SignatureDefinition>> streams) {
        return Map.entry(name, streams.flatMap(s -> s).toList());
    }

    public static List<SignatureDefinition> getSignatures(String name) {
        return OPERATIONS.getOrDefault(name, List.of());
    }
}
```

### Code Examples

#### Simple Signature (abs)
```java
register("abs",
    SignatureStreams.unaryOps(TypeSets.NUMERIC_WITH_QUANTITY)
)

// Stream produces:
// abs(Integer) → Integer
// abs(Decimal) → Decimal
// abs(Quantity) → Quantity
```

#### Multi-Type Signature (comparison)
```java
register("gt",
    SignatureStreams.comparisons(TypeSets.COMPARABLE)
)

// Stream produces:
// gt(Integer, Integer) → Boolean
// gt(Decimal, Decimal) → Boolean
// gt(String, String) → Boolean
// ... (all COMPARABLE types)
```

#### Complex Signature (substring with optional params)
```java
register("substring",
    SignatureStreams.of(
        Signatures.variadic(List.of(STRING, INTEGER, INTEGER), STRING, 2)
    )
)

// Single signature stream:
// substring(String, Integer, Integer?) → String
```

#### Mixed Signature Types (add)
```java
register("add", Stream.of(
    SignatureStreams.binaryOps(TypeSets.NUMERIC_WITH_QUANTITY),
    SignatureStreams.binaryOps(TypeSets.STRING_LIKE),
    SignatureStreams.of(Signatures.binaryOp(DATE_TIME, QUANTITY, DATE_TIME))
))

// Flattened stream produces:
// add(Integer, Integer) → Integer
// add(Decimal, Decimal) → Decimal
// add(Quantity, Quantity) → Quantity
// add(String, String) → String
// add(DateTime, Quantity) → DateTime
```

#### Custom Type Mapping
```java
// If you need different result type for each input type
register("someOp",
    SignatureStreams.map(TypeSets.NUMERIC, t ->
        Signatures.unaryOp(t, STRING)  // All numeric → String
    )
)
```

### ResultSpec Handling

Same as Option 1 - all four types supported through `Signatures` factory methods.

### Pros

1. **Pure functional style** - Streams are composable and declarative
2. **Minimal ceremony** - No builders, just stream composition
3. **Type sets explicit** - Clear which types apply
4. **Truly immutable** - `Map.ofEntries()` and `toList()` are immutable
5. **Single registration** - Each operation is one `register()` call
6. **Flexible composition** - `Stream.of()` combines multiple streams
7. **Familiar to Java developers** - Standard Stream API

### Cons

1. **Stream nesting complexity** - `Stream.of(stream1, stream2)` requires flatMap
2. **Less readable for non-functional programmers** - Stream composition can be cryptic
3. **Debugging difficulty** - Stream pipelines harder to debug than imperative code
4. **No intermediate validation** - Errors surface only after stream materialization

### Comparison with Current Design

| Aspect | Current | Option 2 |
|--------|---------|----------|
| **Multi-registration** | Possible via `appendSignature` | Prevented by Map.ofEntries |
| **Type visibility** | Hidden in loops | Explicit in type sets |
| **Immutability** | HashMap mutated | True immutability |
| **Readability** | Moderate | Moderate (depends on familiarity) |
| **Functional style** | No | Yes |

---

## Option 3: Table-Based Registry with Type Groups

### Design Approach

Use a **tabular structure** where each operation's signatures are defined as a **list of type groups**. Each group specifies a set of types and a signature pattern. This makes the registry read like a **specification table** that mirrors FHIRPath documentation.

### Architecture

```java
// TypeGroup.java - Associates types with a signature pattern
public sealed interface TypeGroup {
    Stream<SignatureDefinition> expand();

    // Unary: T → T
    record Unary(Set<Type> types) implements TypeGroup {
        public Stream<SignatureDefinition> expand() {
            return types.stream().map(t -> Signatures.unaryOp(t, t));
        }
    }

    // Binary: (T, T) → T
    record Binary(Set<Type> types) implements TypeGroup {
        public Stream<SignatureDefinition> expand() {
            return types.stream().map(t -> Signatures.binaryOp(t, t, t));
        }
    }

    // Comparison: (T, T) → Boolean
    record Comparison(Set<Type> types) implements TypeGroup {
        public Stream<SignatureDefinition> expand() {
            return types.stream().map(t -> Signatures.comparisonOp(t, t));
        }
    }

    // Mixed: (T1, T2) → T3 (different types)
    record Mixed(Type left, Type right, Type result) implements TypeGroup {
        public Stream<SignatureDefinition> expand() {
            return Stream.of(Signatures.binaryOp(left, right, result));
        }
    }

    // Single signature
    record Single(SignatureDefinition sig) implements TypeGroup {
        public Stream<SignatureDefinition> expand() {
            return Stream.of(sig);
        }
    }

    // Custom mapping
    record Custom(Set<Type> types, Function<Type, SignatureDefinition> mapper)
            implements TypeGroup {
        public Stream<SignatureDefinition> expand() {
            return types.stream().map(mapper);
        }
    }
}

// TypeSets.java - Same as Options 1 & 2
public final class TypeSets {
    public static final Set<Type> NUMERIC = Set.of(INTEGER, DECIMAL);
    public static final Set<Type> NUMERIC_WITH_QUANTITY = Set.of(INTEGER, DECIMAL, QUANTITY);
    public static final Set<Type> COMPARABLE = Set.of(INTEGER, DECIMAL, STRING, QUANTITY,
                                                       DATE, DATE_TIME, TIME);
    // ... other sets
}

// OperationTable.java - Tabular operation specifications
public final class OperationTable {

    // Each operation defined as a list of TypeGroups
    private static final Map<String, List<TypeGroup>> TABLE = Map.ofEntries(

        // ARITHMETIC OPERATORS (FHIRPath Spec 6.2)
        entry("add", List.of(
            new TypeGroup.Binary(TypeSets.NUMERIC_WITH_QUANTITY),
            new TypeGroup.Binary(TypeSets.STRING_LIKE),
            new TypeGroup.Mixed(DATE_TIME, QUANTITY, DATE_TIME)
        )),

        entry("sub", List.of(
            new TypeGroup.Binary(TypeSets.NUMERIC_WITH_QUANTITY),
            new TypeGroup.Mixed(DATE_TIME, QUANTITY, DATE_TIME)
        )),

        entry("multiply", List.of(
            new TypeGroup.Binary(TypeSets.NUMERIC_WITH_QUANTITY)
        )),

        entry("divide", List.of(
            new TypeGroup.Binary(TypeSets.NUMERIC_WITH_QUANTITY)
        )),

        entry("mod", List.of(
            new TypeGroup.Binary(TypeSets.NUMERIC)
        )),

        // MATH FUNCTIONS (FHIRPath Spec 6.4)
        entry("abs", List.of(
            new TypeGroup.Unary(TypeSets.NUMERIC_WITH_QUANTITY)
        )),

        entry("sqrt", List.of(
            new TypeGroup.Unary(TypeSets.NUMERIC)
        )),

        entry("ceiling", List.of(
            new TypeGroup.Unary(TypeSets.NUMERIC)
        )),

        entry("floor", List.of(
            new TypeGroup.Unary(TypeSets.NUMERIC)
        )),

        // STRING FUNCTIONS (FHIRPath Spec 6.5)
        entry("substring", List.of(
            new TypeGroup.Single(
                Signatures.variadic(List.of(STRING, INTEGER, INTEGER), STRING, 2)
            )
        )),

        entry("upper", List.of(
            new TypeGroup.Single(Signatures.stringOp(STRING))
        )),

        entry("lower", List.of(
            new TypeGroup.Single(Signatures.stringOp(STRING))
        )),

        entry("startsWith", List.of(
            new TypeGroup.Single(Signatures.binaryOp(STRING, STRING, BOOLEAN))
        )),

        // COMPARISON OPERATORS (FHIRPath Spec 6.3)
        entry("gt", List.of(
            new TypeGroup.Comparison(TypeSets.COMPARABLE)
        )),

        entry("lt", List.of(
            new TypeGroup.Comparison(TypeSets.COMPARABLE)
        )),

        entry("geq", List.of(
            new TypeGroup.Comparison(TypeSets.COMPARABLE)
        )),

        entry("leq", List.of(
            new TypeGroup.Comparison(TypeSets.COMPARABLE)
        )),

        // COLLECTION OPERATIONS (FHIRPath Spec 6.6)
        entry("first", List.of(
            new TypeGroup.Single(Signatures.elementExtractor(ANY))
        )),

        entry("count", List.of(
            new TypeGroup.Single(Signatures.collectionAggregator(ANY, INTEGER))
        )),

        entry("empty", List.of(
            new TypeGroup.Single(Signatures.collectionAggregator(ANY, BOOLEAN))
        ))
    );

    public static List<SignatureDefinition> getSignatures(String name) {
        return TABLE.getOrDefault(name, List.of())
                    .stream()
                    .flatMap(TypeGroup::expand)
                    .toList();
    }
}

// OperationRegistry.java - Thin wrapper around table
public final class OperationRegistry {
    private OperationRegistry() {}

    public static List<SignatureDefinition> getSignatures(String name) {
        return OperationTable.getSignatures(name);
    }
}
```

### Code Examples

#### Simple Signature (abs)
```java
entry("abs", List.of(
    new TypeGroup.Unary(TypeSets.NUMERIC_WITH_QUANTITY)
))

// Expands to:
// abs(Integer) → Integer
// abs(Decimal) → Decimal
// abs(Quantity) → Quantity
```

#### Multi-Type Signature (comparison)
```java
entry("gt", List.of(
    new TypeGroup.Comparison(TypeSets.COMPARABLE)
))

// Expands to:
// gt(Integer, Integer) → Boolean
// gt(Decimal, Decimal) → Boolean
// ... (all COMPARABLE types)
```

#### Complex Signature (substring with optional params)
```java
entry("substring", List.of(
    new TypeGroup.Single(
        Signatures.variadic(List.of(STRING, INTEGER, INTEGER), STRING, 2)
    )
))

// Single signature:
// substring(String, Integer, Integer?) → String
```

#### Mixed Signature Types (add)
```java
entry("add", List.of(
    new TypeGroup.Binary(TypeSets.NUMERIC_WITH_QUANTITY),
    new TypeGroup.Binary(TypeSets.STRING_LIKE),
    new TypeGroup.Mixed(DATE_TIME, QUANTITY, DATE_TIME)
))

// Expands to:
// add(Integer, Integer) → Integer
// add(Decimal, Decimal) → Decimal
// add(Quantity, Quantity) → Quantity
// add(String, String) → String
// add(DateTime, Quantity) → DateTime
```

#### Custom Type Mapping
```java
entry("someOp", List.of(
    new TypeGroup.Custom(TypeSets.NUMERIC, t ->
        Signatures.unaryOp(t, BOOLEAN)  // All numeric → Boolean
    )
))
```

### ResultSpec Handling

All four ResultSpec types supported through `Signatures` factory methods called from TypeGroup implementations.

### Pros

1. **Table-like structure** - Reads like a specification document
2. **Sealed TypeGroup hierarchy** - Type-safe pattern matching
3. **Self-documenting** - Each TypeGroup variant explains its pattern
4. **Easy to audit** - Compare against FHIRPath spec section by section
5. **Truly immutable** - Map.ofEntries + lazy expansion
6. **Single registration** - Each operation is one table entry
7. **Extensible** - Add new TypeGroup variants for new patterns
8. **Lazy expansion** - Signatures generated on-demand

### Cons

1. **More abstractions** - TypeGroup sealed hierarchy adds complexity
2. **Two-phase resolution** - Table → TypeGroups → SignatureDefinitions
3. **Performance overhead** - Stream expansion on every getSignatures() call (can be cached)
4. **Verbose for single signatures** - `new TypeGroup.Single()` wrapper needed

### Comparison with Current Design

| Aspect | Current | Option 3 |
|--------|---------|----------|
| **Multi-registration** | Possible via `appendSignature` | Prevented by table structure |
| **Type visibility** | Hidden in loops | Explicit in TypeGroups |
| **Immutability** | HashMap mutated | True immutability |
| **Readability** | Moderate | Very high (table format) |
| **Spec alignment** | Partial | Excellent (mirrors FHIRPath spec) |

---

## Option 4: Hybrid - Enhanced Current Design with Type Sets

### Design Approach

**Minimize disruption** to the current design while addressing its weaknesses. Add **type sets**, replace **helper methods with explicit type sets**, and ensure **single registration** by pre-composing all signatures before `register()`.

### Architecture

```java
// TypeSets.java - Same as all options
public final class TypeSets {
    public static final Set<Type> NUMERIC = Set.of(INTEGER, DECIMAL);
    public static final Set<Type> NUMERIC_WITH_QUANTITY = Set.of(INTEGER, DECIMAL, QUANTITY);
    public static final Set<Type> COMPARABLE = Set.of(INTEGER, DECIMAL, STRING, QUANTITY,
                                                       DATE, DATE_TIME, TIME);
    public static final Set<Type> TEMPORAL = Set.of(DATE, DATE_TIME, TIME);
    public static final Set<Type> STRING_LIKE = Set.of(STRING);
}

// SignatureExpansion.java - Helper for expanding type sets
public final class SignatureExpansion {

    // Expand type set to list of unary signatures
    public static List<SignatureDefinition> unaryOps(Set<Type> types) {
        return types.stream()
                    .map(t -> Signatures.unaryOp(t, t))
                    .toList();
    }

    // Expand type set to list of binary signatures
    public static List<SignatureDefinition> binaryOps(Set<Type> types) {
        return types.stream()
                    .map(t -> Signatures.binaryOp(t, t, t))
                    .toList();
    }

    // Expand type set to list of comparison signatures
    public static List<SignatureDefinition> comparisons(Set<Type> types) {
        return types.stream()
                    .map(t -> Signatures.comparisonOp(t, t))
                    .toList();
    }

    // Combine multiple signature lists
    @SafeVarargs
    public static List<SignatureDefinition> combine(List<SignatureDefinition>... lists) {
        return Stream.of(lists)
                     .flatMap(List::stream)
                     .toList();
    }
}

// OperationRegistry.java - Enhanced with type sets
public final class OperationRegistry {

    private static final Map<String, List<SignatureDefinition>> OPERATIONS;

    static {
        OPERATIONS = new HashMap<>();

        // ARITHMETIC OPERATORS (FHIRPath Spec 6.2)

        // Addition: numeric types, string, and DateTime+Quantity
        register("add", SignatureExpansion.combine(
            SignatureExpansion.binaryOps(TypeSets.NUMERIC_WITH_QUANTITY),
            SignatureExpansion.binaryOps(TypeSets.STRING_LIKE),
            List.of(Signatures.binaryOp(DATE_TIME, QUANTITY, DATE_TIME))
        ));

        // Subtraction: numeric types and DateTime-Quantity
        register("sub", SignatureExpansion.combine(
            SignatureExpansion.binaryOps(TypeSets.NUMERIC_WITH_QUANTITY),
            List.of(Signatures.binaryOp(DATE_TIME, QUANTITY, DATE_TIME))
        ));

        // Multiplication: numeric types only
        register("multiply",
            SignatureExpansion.binaryOps(TypeSets.NUMERIC_WITH_QUANTITY)
        );

        // Division: numeric types only
        register("divide",
            SignatureExpansion.binaryOps(TypeSets.NUMERIC_WITH_QUANTITY)
        );

        // Modulo: Integer and Decimal only
        register("mod",
            SignatureExpansion.binaryOps(TypeSets.NUMERIC)
        );

        // MATH FUNCTIONS (FHIRPath Spec 6.4)

        register("abs",
            SignatureExpansion.unaryOps(TypeSets.NUMERIC_WITH_QUANTITY)
        );

        register("sqrt",
            SignatureExpansion.unaryOps(TypeSets.NUMERIC)
        );

        register("ceiling",
            SignatureExpansion.unaryOps(TypeSets.NUMERIC)
        );

        register("floor",
            SignatureExpansion.unaryOps(TypeSets.NUMERIC)
        );

        register("truncate",
            SignatureExpansion.unaryOps(TypeSets.NUMERIC)
        );

        register("exp",
            SignatureExpansion.unaryOps(TypeSets.NUMERIC)
        );

        register("ln",
            SignatureExpansion.unaryOps(TypeSets.NUMERIC)
        );

        register("log",
            SignatureExpansion.unaryOps(TypeSets.NUMERIC)
        );

        // STRING FUNCTIONS (FHIRPath Spec 6.5)

        register("substring", List.of(
            Signatures.variadic(List.of(STRING, INTEGER, INTEGER), STRING, 2)
        ));

        register("startsWith", List.of(
            Signatures.binaryOp(STRING, STRING, BOOLEAN)
        ));

        register("endsWith", List.of(
            Signatures.binaryOp(STRING, STRING, BOOLEAN)
        ));

        register("contains", List.of(
            Signatures.binaryOp(STRING, STRING, BOOLEAN)
        ));

        register("upper", List.of(
            Signatures.stringOp(STRING)
        ));

        register("lower", List.of(
            Signatures.stringOp(STRING)
        ));

        register("replace", List.of(
            Signatures.stringOp(STRING, STRING, STRING)
        ));

        register("matches", List.of(
            Signatures.binaryOp(STRING, STRING, BOOLEAN)
        ));

        register("length", List.of(
            Signatures.unaryOp(STRING, INTEGER)
        ));

        // BOOLEAN OPERATORS (FHIRPath Spec 6.8)

        register("and", List.of(
            Signatures.binaryOp(BOOLEAN, BOOLEAN, BOOLEAN)
        ));

        register("or", List.of(
            Signatures.binaryOp(BOOLEAN, BOOLEAN, BOOLEAN)
        ));

        register("xor", List.of(
            Signatures.binaryOp(BOOLEAN, BOOLEAN, BOOLEAN)
        ));

        register("implies", List.of(
            Signatures.binaryOp(BOOLEAN, BOOLEAN, BOOLEAN)
        ));

        register("not", List.of(
            Signatures.unaryOp(BOOLEAN, BOOLEAN)
        ));

        // COMPARISON OPERATORS (FHIRPath Spec 6.3)

        register("gt",
            SignatureExpansion.comparisons(TypeSets.COMPARABLE)
        );

        register("lt",
            SignatureExpansion.comparisons(TypeSets.COMPARABLE)
        );

        register("geq",
            SignatureExpansion.comparisons(TypeSets.COMPARABLE)
        );

        register("leq",
            SignatureExpansion.comparisons(TypeSets.COMPARABLE)
        );

        // COLLECTION OPERATIONS (FHIRPath Spec 6.6)

        register("first", List.of(
            Signatures.elementExtractor(ANY)
        ));

        register("count", List.of(
            Signatures.collectionAggregator(ANY, INTEGER)
        ));

        register("exists", List.of(
            Signatures.collectionAggregator(ANY, BOOLEAN)
        ));

        register("empty", List.of(
            Signatures.collectionAggregator(ANY, BOOLEAN)
        ));
    }

    private OperationRegistry() {
        // Singleton - no instantiation
    }

    private static void register(String name, List<SignatureDefinition> signatures) {
        if (OPERATIONS.containsKey(name)) {
            throw new IllegalStateException(
                "Operation already registered: " + name +
                ". All overloads must be registered in a single call."
            );
        }
        OPERATIONS.put(name, List.copyOf(signatures));  // Make immutable
    }

    @Nonnull
    public static List<SignatureDefinition> getSignatures(@Nonnull String name) {
        return OPERATIONS.getOrDefault(name, List.of());
    }

    public static boolean isRegistered(@Nonnull String name) {
        return OPERATIONS.containsKey(name);
    }
}
```

### Code Examples

#### Simple Signature (abs)
```java
register("abs",
    SignatureExpansion.unaryOps(TypeSets.NUMERIC_WITH_QUANTITY)
);

// Expands to:
// abs(Integer) → Integer
// abs(Decimal) → Decimal
// abs(Quantity) → Quantity
```

#### Multi-Type Signature (comparison)
```java
register("gt",
    SignatureExpansion.comparisons(TypeSets.COMPARABLE)
);

// Expands to:
// gt(Integer, Integer) → Boolean
// gt(Decimal, Decimal) → Boolean
// ... (all COMPARABLE types)
```

#### Complex Signature (substring with optional params)
```java
register("substring", List.of(
    Signatures.variadic(List.of(STRING, INTEGER, INTEGER), STRING, 2)
));

// Single signature:
// substring(String, Integer, Integer?) → String
```

#### Mixed Signature Types (add)
```java
register("add", SignatureExpansion.combine(
    SignatureExpansion.binaryOps(TypeSets.NUMERIC_WITH_QUANTITY),
    SignatureExpansion.binaryOps(TypeSets.STRING_LIKE),
    List.of(Signatures.binaryOp(DATE_TIME, QUANTITY, DATE_TIME))
));

// Combined list contains:
// add(Integer, Integer) → Integer
// add(Decimal, Decimal) → Decimal
// add(Quantity, Quantity) → Quantity
// add(String, String) → String
// add(DateTime, Quantity) → DateTime
```

### ResultSpec Handling

Same as all other options - uses existing `Signatures` factory methods.

### Pros

1. **Minimal disruption** - Preserves current structure
2. **Type sets explicit** - Clear which types apply
3. **Single registration enforced** - Exception thrown on duplicate
4. **Immutability via List.copyOf()** - Signatures can't be modified
5. **No new abstractions** - Just helper methods
6. **Easy migration** - Can refactor incrementally
7. **Familiar to team** - Similar to current approach
8. **Less learning curve** - No builders or complex streams

### Cons

1. **Still uses static block** - Not as clean as builder
2. **HashMap still mutable during init** - Not fully immutable (but no external access)
3. **SignatureExpansion.combine() is verbose** - More ceremony than streams
4. **Less elegant** - Not as clean as functional approaches

### Comparison with Current Design

| Aspect | Current | Option 4 |
|--------|---------|----------|
| **Multi-registration** | Possible via `appendSignature` | Prevented by register() check |
| **Type visibility** | Hidden in loops | Explicit in type sets |
| **Immutability** | HashMap mutated | List.copyOf() per entry |
| **Readability** | Moderate | Good |
| **Migration effort** | N/A | Lowest (incremental) |

---

## Comparative Analysis

### Summary Table

| Criterion | Option 1 (Builder) | Option 2 (Streams) | Option 3 (Table) | Option 4 (Hybrid) |
|-----------|-------------------|-------------------|-----------------|------------------|
| **Readability** | Excellent (9/10) | Good (7/10) | Excellent (9/10) | Good (7/10) |
| **Immutability** | Perfect | Perfect | Perfect | Good (List.copyOf) |
| **Single Registration** | Enforced | Enforced | Enforced | Enforced |
| **Type Set Visibility** | Explicit | Explicit | Explicit | Explicit |
| **Complexity** | High (builders) | Medium (streams) | High (TypeGroups) | Low (helpers) |
| **Migration Effort** | High (rewrite) | High (rewrite) | High (rewrite) | Low (incremental) |
| **FHIRPath Spec Alignment** | Good | Good | Excellent | Good |
| **Learning Curve** | Medium | Medium | Medium | Low |
| **Extensibility** | High | Medium | High | Medium |
| **Code Volume** | +30% | +15% | +40% | +10% |

### Recommendation by Use Case

#### Choose **Option 1 (Fluent Builder)** if:
- You prioritize **maximum readability**
- You want **strong compile-time guarantees**
- You're willing to invest in **builder infrastructure**
- Team is comfortable with **fluent APIs**

#### Choose **Option 2 (Streams)** if:
- You prefer **functional programming**
- You want **minimal abstractions**
- Team is familiar with **Java Streams**
- You value **conciseness**

#### Choose **Option 3 (Table)** if:
- You need **maximum spec alignment**
- You want **easy auditing** against FHIRPath spec
- You prefer **declarative data structures**
- **Documentation as code** is a priority

#### Choose **Option 4 (Hybrid)** if:
- You want **minimal disruption**
- **Quick implementation** is important
- Team prefers **familiar patterns**
- You need **incremental migration path**

---

## Architect's Recommendation

### Primary Recommendation: **Option 3 (Table-Based Registry)**

**Rationale:**

1. **Specification Alignment** - The table structure mirrors the FHIRPath specification exactly, making it trivial to audit and maintain
2. **Self-Documenting** - TypeGroup sealed hierarchy makes patterns explicit
3. **Extensibility** - Easy to add new TypeGroup variants for new patterns
4. **Readability** - Reads like a reference manual, not code
5. **Future-Proof** - Table structure can be externalized to JSON/YAML if needed

**Implementation Priority:**
1. Implement TypeSets.java (common to all options)
2. Implement TypeGroup sealed hierarchy
3. Create OperationTable with core operations
4. Migrate existing operations incrementally
5. Add caching if performance becomes an issue

### Secondary Recommendation: **Option 4 (Hybrid)** for immediate improvement

If Option 3 is deemed too disruptive, implement **Option 4** as an **intermediate step**:

1. **Week 1:** Add TypeSets.java
2. **Week 2:** Add SignatureExpansion helper methods
3. **Week 3:** Refactor existing operations to use type sets
4. **Week 4:** Add validation to prevent multi-registration

This provides **80% of the benefits with 20% of the effort**.

---

## Implementation Notes

### For Developers

1. **Type Sets** - Always use predefined TypeSets rather than inline Set.of()
2. **Single Registration** - Pre-compose all signatures before calling register()
3. **Comments** - Add FHIRPath spec section references (e.g., "// FHIRPath Spec 6.4.1")
4. **Validation** - Test that all operations in spec are registered
5. **Documentation** - Keep DESIGN.md updated with registry structure

### For Testers

1. **Coverage** - Ensure each type in a TypeSet has test coverage
2. **Edge Cases** - Test optional parameters (minArity vs arity)
3. **ResultSpec Types** - Test all four ResultSpec variants
4. **Spec Examples** - Use FHIRPath spec examples as test cases
5. **Negative Tests** - Verify multi-registration throws exceptions

---

## Conclusion

All four options successfully address the core requirements:
- ✅ Readability over brevity
- ✅ Immutable singleton
- ✅ Single registration per operation
- ✅ Explicit multi-type patterns
- ✅ Predefined type sets
- ✅ Support all 4 ResultSpec types

The choice depends on team preferences, migration constraints, and long-term maintainability goals.

**Option 3 (Table)** provides the best long-term architecture for specification alignment and maintainability.

**Option 4 (Hybrid)** provides the fastest path to improvement with minimal disruption.

---

**Document Status:** Architecture Proposal
**Next Steps:** Team review and selection
**Questions:** Contact Principal Architect

