# Type Variables and Polymorphic Signatures Discussion

**Date**: October 17, 2025  
**Context**: Discussion on handling polymorphic operators like `in`, `contains`, `union`, and `equal` in the FHIRPath type system

---

## Problem Statement

The current type system cannot express polymorphic signatures where multiple parameters must have related types. Specifically:

### Problematic Operators

1. **`in` operator**: `X in Collection[X] -> Boolean`
   - Element type must match collection element type (or have common type)
   
2. **`contains` operator**: `Collection[X] contains X -> Boolean`
   - Similar constraint to `in`
   
3. **`union` operator**: `Collection[T] | Collection[T] -> Collection[T]`
   - Both collections must have same element type (or common type)
   - Result type is that common type
   
4. **`equal` operator**: `T = T -> Boolean`
   - Both operands should be same type (with adaptation)

### Current Limitation

```java
// What we WANT to express but CANNOT:
signature("in",
    capture("X"),                    // ❌ No capture/type variable support
    CollectionType(capture("X")),    // ❌ Cannot reference same type variable
    BOOLEAN
)

// What we CAN express (inadequate):
signature("in",
    ANY,                             // ✓ Matches anything
    CollectionType(ANY),             // ✓ Matches any collection
    BOOLEAN                          // ✓ Static result
)
// Problem: No guarantee that argument types are compatible!
```

---

## Solution Options Discussed

### Option 1: Extend Type System with Type Variables

Add explicit type variable support to the type system:

```java
sealed interface Type {
    // ...existing types...
    record TypeVariable(String name) implements Type {}
}

// Usage in signatures:
signature("in",
    typeVar("X"),                    // Declares type variable X
    CollectionType(typeVar("X")),    // References same X
    BOOLEAN
)

signature("union",
    CollectionType(typeVar("T")),
    CollectionType(typeVar("T")),
    CollectionType(typeVar("T"))     // Result uses same T
)
```

**Resolution Algorithm with Unification:**

```java
class OverloadResolver {
    record UnificationContext(Map<String, Type> bindings) {}
    
    Cost adaptWithUnification(Type actual, Type expected, UnificationContext ctx) {
        // Identity
        if (actual.equals(expected)) return cost(0);
        
        // Type variable unification
        if (expected instanceof TypeVariable tv) {
            Type bound = ctx.bindings.get(tv.name());
            if (bound == null) {
                // First occurrence: bind it
                ctx.bindings.put(tv.name(), actual);
                return cost(0);
            } else {
                // Already bound: must be compatible
                return adapt(actual, bound);
            }
        }
        
        // Collection with type variables
        if (expected instanceof CollectionType(TypeVariable tv)) {
            // Unify element types
            // ...
        }
        
        // Standard adaptation rules
        // ...
    }
}
```

**Benefits:**
- Clean, systematic approach
- Type variables are explicit in signatures
- Standard unification algorithm
- Can eliminate some `ResultSpec` implementations

**Concerns:**
- Doesn't handle common type computation automatically
- For `2 in (2.0 | 3.2)`, need to compute `commonType(INTEGER, DECIMAL) = DECIMAL`
- Type variables alone don't express "find common supertype"

---

### Option 2: Custom Programmatic Signature Specification

Allow custom resolution logic for cases that cannot be expressed in the type system:

```java
interface CustomSignatureResolver {
    /**
     * Attempt to resolve a call with custom logic.
     * Returns null if this resolver cannot handle the call.
     */
    ResolvedCall resolve(List<IRNode> args);
}

class InOperatorResolver implements CustomSignatureResolver {
    @Override
    public ResolvedCall resolve(List<IRNode> args) {
        if (args.size() != 2) return null;
        
        Type elementType = args.get(0).getType();
        Type collectionType = args.get(1).getType();
        
        if (!(collectionType instanceof CollectionType ct)) {
            return null; // Not a collection
        }
        
        // Try to find common type
        Type common = AdaptationEngine.findCommonType(elementType, ct.elementType());
        if (common == null) {
            return null; // No common type
        }
        
        // Create adapted arguments
        IRNode adaptedElement = adapt(args.get(0), common);
        IRNode adaptedCollection = adapt(args.get(1), new CollectionType(common));
        
        return new ResolvedCall(
            new ResolvedSignature(BOOLEAN),
            List.of(adaptedElement, adaptedCollection)
        );
    }
}

// Registry integration:
class OperationRegistry {
    private static final Map<String, CustomSignatureResolver> CUSTOM_RESOLVERS = Map.of(
        "in", new InOperatorResolver(),
        "contains", new ContainsOperatorResolver(),
        "union", new UnionOperatorResolver()
    );
}
```

**Benefits:**
- Maximum flexibility for complex cases
- Can implement arbitrary resolution logic
- Can handle common type computation naturally
- Doesn't require type system changes

**Drawbacks:**
- Less declarative than type system approach
- More code to maintain
- Resolution logic scattered across resolver classes
- Harder to reason about type safety

---

### Option 3: Type Variables with Common Type Computation (Hybrid)

Extend type variables with capture semantics that include adaptation closure:

```java
// Capture type that tracks "reachable types" via adaptation
record CaptureType(String name) implements Type {
    // Represents: "any type reachable from the bound type via adaptation"
}

signature("in",
    capture("X"),                    // Captures actual type
    CollectionType(capture("X")),    // Must be compatible via adaptation
    BOOLEAN
)

// Resolution with adaptation closure:
Cost resolveWithCapture(Type actual, CaptureType expected, CaptureContext ctx) {
    Type bound = ctx.bindings.get(expected.name());
    
    if (bound == null) {
        // First binding: just bind it
        ctx.bindings.put(expected.name(), actual);
        return cost(0);
    } else {
        // Already bound: find common type
        Type common = findCommonType(actual, bound);
        if (common == null) {
            return IMPOSSIBLE;
        }
        // Update binding to common type
        ctx.bindings.put(expected.name(), common);
        // Return adaptation cost
        return cost(adapt(actual, common)) + cost(adapt(bound, common));
    }
}
```

**Example Resolution:**

```fhirpath
2 in (2.0 | 3.2)
```

1. `2` has type `INTEGER`
2. `(2.0 | 3.2)` has type `[DECIMAL]`
3. Signature: `in(capture("X"), [capture("X")])`
4. First arg: bind `X = INTEGER`, cost 0
5. Second arg: `[DECIMAL]` vs `[X]` where `X = INTEGER`
   - Element types: `DECIMAL` vs `INTEGER`
   - Common type: `DECIMAL`
   - Update binding: `X = DECIMAL`
   - Costs: adapt `INTEGER` to `DECIMAL` (cost 1) + adapt `DECIMAL` to `DECIMAL` (cost 0) = 1
6. Final: `in(cast(2, DECIMAL), [DECIMAL]) -> BOOLEAN`

---

## Adaptation Closure Computation

### Concept: Reachable Types

For any type `T`, the **adaptation closure** is the set of all types reachable via adaptation rules:

```
adaptationClosure(INTEGER) = {
    INTEGER (cost 0),
    DECIMAL (cost 1),
    QUANTITY (cost 2),
    [INTEGER] (cost 0, singleton promotion),
    [DECIMAL] (cost 1, cast + promotion),
    [QUANTITY] (cost 2, transitive cast + promotion),
    ANY (cost 0)
}

adaptationClosure(FhirType(INTEGER)) = {
    FhirType(INTEGER) (cost 0),
    INTEGER (cost 1, unwrap),
    DECIMAL (cost 2, unwrap + cast),
    QUANTITY (cost 3, unwrap + transitive cast),
    // ... plus collection wrappers
}
```

### Common Type Algorithm

```java
class AdaptationEngine {
    /**
     * Find the common type that both types can adapt to.
     * Returns the type with minimum total adaptation cost.
     */
    static Type findCommonType(Type a, Type b) {
        // Trivial cases
        if (a.equals(b)) return a;
        if (a == NULL) return b;
        if (b == NULL) return a;
        
        // Compute adaptation closures
        Set<Adaptation> closureA = computeAdaptationClosure(a);
        Set<Adaptation> closureB = computeAdaptationClosure(b);
        
        // Find intersection (types reachable from both)
        Adaptation bestCommon = null;
        int bestCost = Integer.MAX_VALUE;
        
        for (Adaptation adaptA : closureA) {
            for (Adaptation adaptB : closureB) {
                if (adaptA.targetType.equals(adaptB.targetType)) {
                    int totalCost = adaptA.cost + adaptB.cost;
                    if (totalCost < bestCost) {
                        bestCost = totalCost;
                        bestCommon = adaptA;
                    }
                }
            }
        }
        
        return bestCommon != null ? bestCommon.targetType : null;
    }
    
    record Adaptation(Type targetType, int cost, IRNode adaptedNode) {}
    
    /**
     * Compute all types reachable from source via adaptation rules.
     * Uses iterative deepening to handle transitive adaptations.
     */
    static Set<Adaptation> computeAdaptationClosure(Type source) {
        Set<Adaptation> closure = new HashSet<>();
        Queue<Adaptation> frontier = new LinkedList<>();
        
        // Start with identity
        frontier.add(new Adaptation(source, 0, null));
        
        while (!frontier.isEmpty()) {
            Adaptation current = frontier.poll();
            
            if (!closure.add(current)) {
                continue; // Already visited
            }
            
            // Apply all adaptation rules
            for (Adaptation next : applyAdaptationRules(current)) {
                if (next.cost < Integer.MAX_VALUE) {
                    frontier.add(next);
                }
            }
        }
        
        return closure;
    }
    
    /**
     * Apply all single-step adaptation rules to a type.
     */
    static List<Adaptation> applyAdaptationRules(Adaptation from) {
        List<Adaptation> results = new ArrayList<>();
        Type t = from.targetType;
        int baseCost = from.cost;
        
        // Wildcard matching (cost 0)
        results.add(new Adaptation(ANY, baseCost, null));
        
        // Singleton promotion (cost 0)
        results.add(new Adaptation(new CollectionType(t), baseCost, null));
        
        // Primitive casts (cost 1)
        for (Type target : getPrimitiveCastTargets(t)) {
            results.add(new Adaptation(target, baseCost + 1, null));
        }
        
        // FHIR unwrap (cost 1)
        if (t instanceof FhirType ft) {
            results.add(new Adaptation(ft.systemType(), baseCost + 1, null));
        }
        
        // Collection element adaptation
        if (t instanceof CollectionType ct) {
            for (Adaptation elemAdapt : applyAdaptationRules(
                    new Adaptation(ct.elementType(), 0, null))) {
                results.add(new Adaptation(
                    new CollectionType(elemAdapt.targetType),
                    baseCost + elemAdapt.cost,
                    null
                ));
            }
        }
        
        return results;
    }
}
```

---

## Integration with OverloadResolver

### Current Architecture

```java
class OverloadResolver {
    static ResolvedCall resolveCall(List<SignatureDefinition> candidates, List<IRNode> args) {
        ResolvedCall best = null;
        int bestCost = Integer.MAX_VALUE;
        
        for (SignatureDefinition sig : candidates) {
            // Check arity
            if (!sig.canApplyToArgumentCount(args.size())) continue;
            
            // Attempt adaptation for each parameter
            int totalCost = 0;
            List<IRNode> adaptedArgs = new ArrayList<>();
            
            for (int i = 0; i < args.size(); i++) {
                Adapt result = adapt(args.get(i), sig.parameterTypes().get(i));
                if (!result.ok) break;
                totalCost += result.cost;
                adaptedArgs.add(result.node);
            }
            
            // Check if this is better
            if (adaptedArgs.size() == args.size() && totalCost < bestCost) {
                bestCost = totalCost;
                best = new ResolvedCall(
                    ResolvedSignature.resolve(sig, adaptedArgs),
                    adaptedArgs
                );
            }
        }
        
        return best;
    }
}
```

### Enhanced with Type Variables/Captures

```java
class OverloadResolver {
    static ResolvedCall resolveCall(List<SignatureDefinition> candidates, List<IRNode> args) {
        for (SignatureDefinition sig : candidates) {
            if (!sig.canApplyToArgumentCount(args.size())) continue;
            
            // Create unification/capture context
            CaptureContext ctx = new CaptureContext();
            
            // Attempt adaptation with capture resolution
            Result result = adaptWithCaptures(args, sig, ctx);
            
            if (result.success && result.totalCost < bestCost) {
                bestCost = result.totalCost;
                best = result.resolvedCall;
            }
        }
        
        return best;
    }
    
    record Result(boolean success, int totalCost, ResolvedCall resolvedCall) {}
    
    static Result adaptWithCaptures(
            List<IRNode> args, 
            SignatureDefinition sig,
            CaptureContext ctx) {
        
        int totalCost = 0;
        List<IRNode> adaptedArgs = new ArrayList<>();
        
        for (int i = 0; i < args.size(); i++) {
            Type actual = args.get(i).getType();
            Type expected = sig.parameterTypes().get(i);
            
            // Handle captures/type variables
            if (expected instanceof CaptureType capture) {
                Cost cost = adaptToCapture(actual, capture, ctx);
                if (!cost.ok) {
                    return new Result(false, 0, null);
                }
                totalCost += cost.value;
                adaptedArgs.add(cost.adaptedNode);
                
            } else {
                // Regular adaptation
                Adapt adapt = adapt(args.get(i), expected);
                if (!adapt.ok) {
                    return new Result(false, 0, null);
                }
                totalCost += adapt.cost;
                adaptedArgs.add(adapt.node);
            }
        }
        
        // Resolve result type with capture bindings
        Type resultType = resolveResultType(sig.resultSpec(), adaptedArgs, ctx);
        
        return new Result(
            true,
            totalCost,
            new ResolvedCall(new ResolvedSignature(resultType), adaptedArgs)
        );
    }
    
    record Cost(boolean ok, int value, IRNode adaptedNode) {}
    
    static Cost adaptToCapture(Type actual, CaptureType capture, CaptureContext ctx) {
        Type bound = ctx.getBinding(capture.name());
        
        if (bound == null) {
            // First occurrence: bind it
            ctx.bind(capture.name(), actual);
            return new Cost(true, 0, /* original node */);
        } else {
            // Find common type
            Type common = AdaptationEngine.findCommonType(actual, bound);
            if (common == null) {
                return new Cost(false, 0, null);
            }
            
            // Update binding
            ctx.bind(capture.name(), common);
            
            // Return adaptation cost to common type
            Adapt adapt = adapt(/* node */, common);
            return new Cost(adapt.ok, adapt.cost, adapt.node);
        }
    }
}

class CaptureContext {
    private final Map<String, Type> bindings = new HashMap<>();
    
    void bind(String name, Type type) {
        bindings.put(name, type);
    }
    
    Type getBinding(String name) {
        return bindings.get(name);
    }
}
```

---

## Relationship to TypeAdapter Design

The existing `TypeAdapter` abstraction (mentioned in previous refactoring plans) can be leveraged:

```java
interface TypeAdapter {
    /**
     * Compute all types reachable from source via this adapter.
     * Each result includes the adaptation cost and transformation.
     */
    Set<AdaptationResult> reachableAdaptations(Type source);
    
    /**
     * Attempt to adapt node to target type.
     * Returns null if adaptation is impossible.
     */
    AdaptationResult adapt(IRNode node, Type target);
}

record AdaptationResult(Type resultType, int cost, IRNode adaptedNode) {}
```

**Key Insight**: The `reachableAdaptations()` method is exactly what we need for computing adaptation closures!

```java
class AdaptationEngine {
    private final List<TypeAdapter> adapters;
    
    Set<AdaptationResult> computeAdaptationClosure(Type source) {
        Set<AdaptationResult> closure = new HashSet<>();
        Queue<AdaptationResult> frontier = new LinkedList<>();
        
        // Start with identity
        frontier.add(new AdaptationResult(source, 0, null));
        
        while (!frontier.isEmpty()) {
            AdaptationResult current = frontier.poll();
            
            if (!closure.add(current)) {
                continue; // Already visited with lower/equal cost
            }
            
            // Apply all adapters
            for (TypeAdapter adapter : adapters) {
                for (AdaptationResult next : adapter.reachableAdaptations(current.resultType)) {
                    // Accumulate costs
                    AdaptationResult accumulated = new AdaptationResult(
                        next.resultType,
                        current.cost + next.cost,
                        next.adaptedNode // Chain transformations
                    );
                    
                    if (accumulated.cost < Integer.MAX_VALUE) {
                        frontier.add(accumulated);
                    }
                }
            }
        }
        
        return closure;
    }
    
    Type findCommonType(Type a, Type b) {
        Set<AdaptationResult> closureA = computeAdaptationClosure(a);
        Set<AdaptationResult> closureB = computeAdaptationClosure(b);
        
        // Find best common type
        AdaptationResult best = null;
        int bestCost = Integer.MAX_VALUE;
        
        for (AdaptationResult adaptA : closureA) {
            for (AdaptationResult adaptB : closureB) {
                if (adaptA.resultType.equals(adaptB.resultType)) {
                    int totalCost = adaptA.cost + adaptB.cost;
                    if (totalCost < bestCost) {
                        bestCost = totalCost;
                        best = adaptA; // or B, same result type
                    }
                }
            }
        }
        
        return best != null ? best.resultType : null;
    }
}
```

**Adaptation costs are additive**: When chaining adaptations, costs sum up naturally. The algorithm can stop early when a matching target type is found (optimization).

---

## Example: `iif` Operator with Captures

```fhirpath
collection.iif(
    condition,              // Lambda[BOOLEAN]
    trueBranch,            // Lambda[Collection[T]]
    falseBranch            // Lambda[Collection[T]]
) -> Collection[T]
```

**Signature with Captures:**

```java
signature("iif",
    CollectionType(ANY),              // input collection
    LambdaType(BOOLEAN),              // condition lambda
    LambdaType(CollectionType(capture("C"))),  // true branch
    LambdaType(CollectionType(capture("C"))),  // false branch
    CollectionType(capture("C"))      // result type
)
```

**Resolution Example:**

```fhirpath
patients.iif(
    active,
    name.family,           // returns Collection[STRING]
    identifier.value       // returns Collection[FhirType(STRING)]
)
```

1. Arg 3: `Lambda[Collection[STRING]]` → bind `C = STRING`, cost 0
2. Arg 4: `Lambda[Collection[FhirType(STRING)]]` → needs `C = FhirType(STRING)`
3. Common type: `commonType(STRING, FhirType(STRING))` = none directly, but:
   - `FhirType(STRING)` can adapt to `STRING` (cost 1)
   - Common type: `STRING`
4. Update binding: `C = STRING`
5. Adapt arg 4: `Lambda[Collection[FhirType(STRING)]]` → requires cast of elements (cost 1)
6. Result: `Collection[STRING]`

---

## Benefits of Capture/Type Variable Approach

1. **Eliminates ResultSpec complexity**: Can use capture references directly
   - `ResultSpec.ArgumentType(2)` → just use `capture("X")` in result position
   
2. **Expresses constraints declaratively**: Signature shows relationships
   
3. **Automatic common type computation**: Resolution algorithm handles it
   
4. **Supports complex operators**: `union`, `in`, `contains`, `equal`, `iif`
   
5. **Type-safe**: Impossible adaptations are caught during resolution

---

## Recommended Implementation Path

### Phase 1: Add CaptureType to Type System

```java
sealed interface Type {
    // ...existing types...
    record CaptureType(String name) implements Type {}
}
```

### Phase 2: Implement AdaptationEngine

```java
class AdaptationEngine {
    static Type findCommonType(Type a, Type b);
    static Set<AdaptationResult> computeAdaptationClosure(Type source);
}
```

### Phase 3: Enhance OverloadResolver

```java
class OverloadResolver {
    private record CaptureContext(Map<String, Type> bindings) {}
    
    private static Result adaptWithCaptures(
        List<IRNode> args,
        SignatureDefinition sig,
        CaptureContext ctx
    );
}
```

### Phase 4: Update Signatures

```java
// Replace complex ResultSpecs with capture references
signature("union",
    CollectionType(capture("T")),
    CollectionType(capture("T")),
    CollectionType(capture("T"))      // Instead of ResultSpec.InputType
)

signature("in",
    capture("X"),
    CollectionType(capture("X")),
    BOOLEAN
)
```

### Phase 5: Simplify ResultSpec

Many `ResultSpec` implementations can be eliminated when captures handle the type relationships.

---

## Open Questions

1. **Should costs be additive when updating capture bindings?**
   - Current proposal: Yes, sum costs when finding common type
   - Alternative: Take minimum cost path
   
2. **How to handle ambiguous common types?**
   - Example: What if two types have multiple common supertypes?
   - Proposal: Choose lowest total adaptation cost
   
3. **Should we support bounded captures?**
   - Example: `capture("T", boundedBy: NUMERIC)` 
   - Limits what types can bind to a capture variable
   
4. **Performance concerns with closure computation?**
   - Adaptation closure could be large for complex type hierarchies
   - Mitigation: Cache closures, use early termination when target found

---

## Conclusion

Adding **capture types** (type variables with common type computation) to the existing type system provides:

- **Expressiveness**: Can define polymorphic signatures declaratively
- **Correctness**: Type relationships enforced systematically
- **Simplicity**: Reduces ad-hoc resolution logic
- **Compatibility**: Works with existing `TypeAdapter` and cost model

The implementation builds on the solid foundation of the current type system (which already has `CollectionType`, singleton promotion, and lambda handling correct) and adds systematic support for polymorphic operations.

**Next Steps**: Prototype the `CaptureType` and `AdaptationEngine` to validate the approach with real examples (`in`, `union`, `iif`).

