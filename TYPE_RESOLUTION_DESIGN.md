# Type Resolution Design for Option 4

## Overview

This document describes the type resolution system for the refined Option 4 architecture, which separates signature definitions (for registry) from resolved signatures (stored in IR nodes).

## Core Components

### 1. ResultSpec - Type Resolution Specification

```java
package com.example.fhirpath.analyzer;

import com.example.fhirpath.ir.IRNode;
import com.example.fhirpath.typing.Type;
import com.example.fhirpath.typing.fhir.FhirType;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * Specifies how to determine the result type of a FHIRPath operation.
 *
 * This is a sealed hierarchy with exactly four implementations corresponding
 * to the four type resolution patterns in FHIRPath.
 */
public sealed interface ResultSpec
    permits ResultSpec.Static,
            ResultSpec.InputType,
            ResultSpec.EffectiveInputType,
            ResultSpec.FhirSystemType {

    /**
     * Resolve the actual result type given resolved argument nodes.
     * Called during Operation construction to produce statically-typed IR.
     */
    @Nonnull
    Type resolve(@Nonnull List<IRNode> resolvedArgs);

    /**
     * Result type is statically known at signature definition time.
     * This is the most common case.
     *
     * Examples: abs(Integer) → Integer, substring(String, Integer) → String
     */
    record Static(@Nonnull Type type) implements ResultSpec {
        @Override
        @Nonnull
        public Type resolve(@Nonnull List<IRNode> resolvedArgs) {
            return type;
        }
    }

    /**
     * Result type is the same as the input type (first argument).
     * Used for operations that preserve structure.
     *
     * Examples: Collection<T>.where(...) → Collection<T>
     */
    record InputType() implements ResultSpec {
        public static final InputType INSTANCE = new InputType();

        @Override
        @Nonnull
        public Type resolve(@Nonnull List<IRNode> resolvedArgs) {
            if (resolvedArgs.isEmpty()) {
                throw new IllegalArgumentException(
                    "InputType requires at least one argument");
            }
            return resolvedArgs.get(0).getType();
        }
    }

    /**
     * Result type is the effective type (element type) of the input collection.
     * Used for operations that extract elements from collections.
     *
     * Examples: Collection<T>.first() → T, Collection<T>.item[n] → T
     */
    record EffectiveInputType() implements ResultSpec {
        public static final EffectiveInputType INSTANCE = new EffectiveInputType();

        @Override
        @Nonnull
        public Type resolve(@Nonnull List<IRNode> resolvedArgs) {
            if (resolvedArgs.isEmpty()) {
                throw new IllegalArgumentException(
                    "EffectiveInputType requires at least one argument");
            }
            return resolvedArgs.get(0).getType().effectiveType();
        }
    }

    /**
     * Result type is the system type corresponding to a FHIR type.
     * Used exclusively by getValue().
     *
     * Examples: FhirType(STRING).getValue() → String
     */
    record FhirSystemType() implements ResultSpec {
        public static final FhirSystemType INSTANCE = new FhirSystemType();

        @Override
        @Nonnull
        public Type resolve(@Nonnull List<IRNode> resolvedArgs) {
            if (resolvedArgs.isEmpty()) {
                throw new IllegalArgumentException(
                    "FhirSystemType requires at least one argument");
            }
            Type inputType = resolvedArgs.get(0).getType();
            if (!(inputType instanceof FhirType fhirType)) {
                throw new IllegalArgumentException(
                    "FhirSystemType requires FhirType input, got: " + inputType);
            }
            return fhirType.systemType();
        }
    }
}
```

### 2. SignatureDefinition - For Registry

```java
package com.example.fhirpath.analyzer;

import com.example.fhirpath.typing.Type;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * Signature definition for registration purposes.
 * Contains parameter types and a ResultSpec for determining result type.
 *
 * This is used in the registry to define available function overloads.
 * During resolution, this is converted to a ResolvedSignature with concrete type.
 */
public record SignatureDefinition(
    @Nonnull List<Type> parameterTypes,
    @Nonnull ResultSpec resultSpec,
    int minArity
) {
    /**
     * Constructor for fixed arity signatures.
     */
    public SignatureDefinition(
        @Nonnull List<Type> parameterTypes,
        @Nonnull ResultSpec resultSpec
    ) {
        this(parameterTypes, resultSpec, parameterTypes.size());
    }

    /**
     * Convenience constructor for static result types (most common case).
     */
    public SignatureDefinition(
        @Nonnull List<Type> parameterTypes,
        @Nonnull Type resultType,
        int minArity
    ) {
        this(parameterTypes, new ResultSpec.Static(resultType), minArity);
    }

    public SignatureDefinition(
        @Nonnull List<Type> parameterTypes,
        @Nonnull Type resultType
    ) {
        this(parameterTypes, new ResultSpec.Static(resultType));
    }

    public int arity() {
        return parameterTypes.size();
    }
}
```

### 3. ResolvedSignature - For Operation Nodes

```java
package com.example.fhirpath.analyzer;

import com.example.fhirpath.typing.Type;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * A resolved signature with concrete, statically-known result type.
 *
 * This is stored in Operation nodes after type resolution is complete.
 * All ResultSpecs have been evaluated, and the result type is concrete.
 */
public record ResolvedSignature(
    @Nonnull List<Type> parameterTypes,
    @Nonnull Type resultType,
    int minArity
) {
    public ResolvedSignature(
        @Nonnull List<Type> parameterTypes,
        @Nonnull Type resultType
    ) {
        this(parameterTypes, resultType, parameterTypes.size());
    }

    public int arity() {
        return parameterTypes.size();
    }

    /**
     * Create a resolved signature from a definition and resolved arguments.
     */
    @Nonnull
    public static ResolvedSignature resolve(
        @Nonnull SignatureDefinition definition,
        @Nonnull List<com.example.fhirpath.ir.IRNode> resolvedArgs
    ) {
        Type concreteResultType = definition.resultSpec().resolve(resolvedArgs);
        return new ResolvedSignature(
            definition.parameterTypes(),
            concreteResultType,
            definition.minArity()
        );
    }
}
```

### 4. Factory Methods - Signatures Helper Class

```java
package com.example.fhirpath.analyzer;

import com.example.fhirpath.typing.Type;
import com.example.fhirpath.typing.CollectionType;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static com.example.fhirpath.typing.Type.*;

/**
 * Factory methods for creating common signature patterns.
 * Simplifies registry definitions and reduces boilerplate.
 */
public class Signatures {

    // ========== Unary Operations ==========

    /**
     * Unary operation where input type = output type.
     * Example: abs(Integer) → Integer, abs(Decimal) → Decimal
     *
     * @param types The types this operation supports
     * @return List of signature definitions, one per type
     */
    @Nonnull
    public static List<SignatureDefinition> unaryOp(@Nonnull Type... types) {
        return Stream.of(types)
            .map(t -> new SignatureDefinition(List.of(t), t))
            .toList();
    }

    /**
     * Unary operation with a different result type.
     * Example: length(String) → Integer
     */
    @Nonnull
    public static SignatureDefinition unaryOp(@Nonnull Type inputType, @Nonnull Type resultType) {
        return new SignatureDefinition(List.of(inputType), resultType);
    }

    // ========== Binary Operations ==========

    /**
     * Binary operator where both operands and result have the same type.
     * Example: +(Integer, Integer) → Integer
     *
     * @param types The types this operator supports
     * @return List of signature definitions, one per type
     */
    @Nonnull
    public static List<SignatureDefinition> binaryOp(@Nonnull Type... types) {
        return Stream.of(types)
            .map(t -> new SignatureDefinition(List.of(t, t), t))
            .toList();
    }

    /**
     * Binary operator with different operand types and result type.
     * Example: /(Integer, Integer) → Decimal
     */
    @Nonnull
    public static SignatureDefinition binaryOp(
        @Nonnull Type leftType,
        @Nonnull Type rightType,
        @Nonnull Type resultType
    ) {
        return new SignatureDefinition(List.of(leftType, rightType), resultType);
    }

    /**
     * Binary operator where result type is the same as left operand.
     * Example: -(DateTime, Quantity) → DateTime
     */
    @Nonnull
    public static SignatureDefinition binaryOpLeft(
        @Nonnull Type leftType,
        @Nonnull Type rightType
    ) {
        return new SignatureDefinition(List.of(leftType, rightType), leftType);
    }

    // ========== Comparison Operations ==========

    /**
     * Comparison operator: takes two operands of the same type, returns Boolean.
     * Example: >(Integer, Integer) → Boolean
     *
     * @param types The types this comparison supports
     * @return List of signature definitions, one per type
     */
    @Nonnull
    public static List<SignatureDefinition> comparisonOp(@Nonnull Type... types) {
        return Stream.of(types)
            .map(t -> new SignatureDefinition(List.of(t, t), BOOLEAN))
            .toList();
    }

    // ========== Collection Operations ==========

    /**
     * Collection operation that extracts elements: Collection<T> → T
     * Examples: first(), last(), single()
     */
    @Nonnull
    public static SignatureDefinition elementExtractor() {
        return new SignatureDefinition(
            List.of(new CollectionType(ANY)),
            ResultSpec.EffectiveInputType.INSTANCE,
            0  // Can accept empty collections
        );
    }

    /**
     * Collection operation that preserves type: Collection<T> → Collection<T>
     * Examples: where(criteria), select(projection)
     */
    @Nonnull
    public static SignatureDefinition collectionPreserver(int minArity) {
        return new SignatureDefinition(
            List.of(new CollectionType(ANY)),
            ResultSpec.InputType.INSTANCE,
            minArity
        );
    }

    /**
     * Collection aggregation: Collection<T> → R
     * Example: count() → Integer
     */
    @Nonnull
    public static SignatureDefinition collectionAggregator(@Nonnull Type resultType) {
        return new SignatureDefinition(
            List.of(new CollectionType(ANY)),
            resultType,
            0
        );
    }

    /**
     * Collection aggregation with specific element type: Collection<T> → R
     * Example: allTrue(Collection<Boolean>) → Boolean
     */
    @Nonnull
    public static SignatureDefinition collectionAggregator(
        @Nonnull Type elementType,
        @Nonnull Type resultType
    ) {
        return new SignatureDefinition(
            List.of(new CollectionType(elementType)),
            resultType,
            0
        );
    }

    // ========== Variadic Operations ==========

    /**
     * Variadic operation with minimum arity.
     * Example: substring(String, Integer, Integer) with minArity=2
     */
    @Nonnull
    public static SignatureDefinition variadic(
        @Nonnull List<Type> paramTypes,
        @Nonnull Type resultType,
        int minArity
    ) {
        return new SignatureDefinition(paramTypes, resultType, minArity);
    }

    // ========== Helper for Multiple Signatures ==========

    /**
     * Combine multiple signature definitions.
     * Example: For operations with heterogeneous overloads
     */
    @Nonnull
    public static List<SignatureDefinition> overloads(SignatureDefinition... sigs) {
        return Arrays.asList(sigs);
    }
}
```

### 5. Example Registry Usage

```java
package com.example.fhirpath.ir;

import com.example.fhirpath.analyzer.SignatureDefinition;
import com.example.fhirpath.typing.CollectionType;

import java.util.*;

import static com.example.fhirpath.analyzer.Signatures.*;
import static com.example.fhirpath.typing.Type.*;

/**
 * Central registry of FHIRPath function and operator signatures.
 */
public class OperationRegistry {

    private static final Map<String, List<SignatureDefinition>> REGISTRY = new HashMap<>();

    static {
        // ========== Arithmetic Operations ==========
        register("add", binaryOp(INTEGER, DECIMAL, QUANTITY, STRING));
        register("subtract", binaryOp(INTEGER, DECIMAL, QUANTITY));
        register("multiply", binaryOp(INTEGER, DECIMAL, QUANTITY));

        // Division always produces Decimal
        register("divide", List.of(
            binaryOp(INTEGER, INTEGER, DECIMAL),
            binaryOp(DECIMAL, DECIMAL, DECIMAL),
            binaryOp(QUANTITY, QUANTITY, DECIMAL)
        ));

        // DateTime arithmetic
        register("add", List.of(
            binaryOpLeft(DATE_TIME, QUANTITY),
            binaryOpLeft(DATE, QUANTITY)
        ));

        // ========== Math Operations ==========
        register("abs", unaryOp(INTEGER, DECIMAL, QUANTITY));
        register("ceiling", unaryOp(DECIMAL));
        register("floor", unaryOp(DECIMAL));
        register("exp", unaryOp(DECIMAL));
        register("ln", unaryOp(DECIMAL));
        register("sqrt", unaryOp(DECIMAL));

        // ========== Comparison Operations ==========
        register("=", comparisonOp(INTEGER, DECIMAL, STRING, DATE, DATE_TIME, TIME, BOOLEAN, QUANTITY));
        register(">", comparisonOp(INTEGER, DECIMAL, STRING, DATE, DATE_TIME, TIME, QUANTITY));

        // ========== String Operations ==========
        register("indexOf", new SignatureDefinition(List.of(STRING, STRING), INTEGER));
        register("substring", variadic(List.of(STRING, INTEGER, INTEGER), STRING, 2));
        register("length", unaryOp(STRING, INTEGER));

        // ========== Collection Operations - Element Extraction ==========
        register("first", List.of(elementExtractor()));
        register("last", List.of(elementExtractor()));

        // ========== Collection Operations - Preserving Type ==========
        register("where", List.of(collectionPreserver(1)));
        register("select", List.of(collectionPreserver(1)));

        // ========== Collection Operations - Aggregation ==========
        register("count", List.of(collectionAggregator(INTEGER)));
        register("empty", List.of(collectionAggregator(BOOLEAN)));
        register("exists", List.of(collectionAggregator(BOOLEAN)));

        // Boolean-specific aggregations require Collection<Boolean>
        register("allTrue", List.of(collectionAggregator(BOOLEAN, BOOLEAN)));
        register("anyTrue", List.of(collectionAggregator(BOOLEAN, BOOLEAN)));
        register("allFalse", List.of(collectionAggregator(BOOLEAN, BOOLEAN)));
        register("anyFalse", List.of(collectionAggregator(BOOLEAN, BOOLEAN)));

        // ========== Boolean Operations ==========
        register("and", binaryOp(BOOLEAN));
        register("or", binaryOp(BOOLEAN));
        register("not", unaryOp(BOOLEAN));
    }

    private static void register(String name, List<SignatureDefinition> signatures) {
        REGISTRY.put(name, signatures);
    }

    private static void register(String name, SignatureDefinition signature) {
        register(name, List.of(signature));
    }

    @Nonnull
    public static List<SignatureDefinition> getSignatures(String name) {
        return REGISTRY.getOrDefault(name, List.of());
    }

    public static boolean isDefined(String name) {
        return REGISTRY.containsKey(name);
    }
}
```

### 6. Resolution Flow

```java
// In OverloadResolver or FunctionRegistry
public static Operation buildOperation(
    String name,
    List<IRNode> resolvedArgs
) {
    // 1. Get signature definitions from registry
    List<SignatureDefinition> candidates = OperationRegistry.getSignatures(name);

    // 2. Find best match based on argument types (cost-based matching)
    SignatureDefinition bestMatch = findBestSignature(candidates, resolvedArgs);

    // 3. Resolve the result type (happens once during construction)
    ResolvedSignature resolved = ResolvedSignature.resolve(bestMatch, resolvedArgs);

    // 4. Create Operation with concrete signature
    return new Operation(name, resolvedArgs, resolved);
}
```

## Key Design Principles

1. **Four Type Resolution Strategies**:
   - `Static`: Type known at definition time (most common)
   - `InputType`: Same as input (e.g., `where`)
   - `EffectiveInputType`: Element type of input collection (e.g., `first`)
   - `FhirSystemType`: FHIR type to system type mapping (e.g., `getValue`)

2. **Clear Separation**:
   - `SignatureDefinition`: For registry, contains `ResultSpec`
   - `ResolvedSignature`: For Operation nodes, contains concrete `Type`

3. **Type Resolution Timing**:
   - Happens once during `Operation` construction
   - Results in statically-typed IR tree
   - No runtime type resolution needed

4. **Factory Methods**:
   - Cover ~90% of common signature patterns
   - Reduce boilerplate in registry
   - Make registry readable and maintainable

5. **Type Safety**:
   - Sealed `ResultSpec` interface ensures exhaustive handling
   - All Operation nodes have concrete types after construction
   - Visitors can rely on static type information

## Examples

### Simple Static Type
```java
// Registry
register("abs", unaryOp(INTEGER, DECIMAL, QUANTITY));

// Resolves to
abs(5) → Operation("abs", [Literal(5)], ResolvedSignature([INTEGER], INTEGER))
```

### Dynamic Type - Element Extraction
```java
// Registry
register("first", List.of(elementExtractor()));

// Resolves to
Patient.name.first() → Operation("first", [Traversal], ResolvedSignature([Collection<String>], String))
```

### Dynamic Type - Type Preservation
```java
// Registry
register("where", List.of(collectionPreserver(1)));

// Resolves to
Patient.name.where(use='official') →
  Operation("where", [Traversal, Lambda], ResolvedSignature([Collection<HumanName>], Collection<HumanName>))
```

### Boolean Aggregation (Specific Element Type)
```java
// Registry
register("allTrue", List.of(collectionAggregator(BOOLEAN, BOOLEAN)));

// Resolves to
Patient.active.allTrue() →
  Operation("allTrue", [Traversal], ResolvedSignature([Collection<Boolean>], Boolean))
```