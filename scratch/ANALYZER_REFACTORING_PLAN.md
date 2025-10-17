[//]: # (prompt: Review current implementation of the Analyzer including supporting classes. 
[//]: # (Suggest how it can be refactored for better overal desing principle maintanabiliy and testability.)
# Analyzer Refactoring Plan

## Goals
1. Improve separation of concerns
2. Enhance testability through dependency injection
3. Reduce cyclomatic complexity
4. Make the design more maintainable and extensible

## Proposed Architecture

### Phase 1: Extract Resolution Strategies (High Priority)

#### 1.1 Create ResolutionContext (Value Object)
Replace multiple Analyzer constructor parameters with a single context object:

```java
public record ResolutionContext(
    AstNode contextNode,
    ResourceType resourceSpec,
    @Nullable Type thisType  // null outside lambda scope
) {
    public static ResolutionContext empty() {
        return new ResolutionContext(
            AstVariable.resourceVariable(), 
            ResourceType.EMPTY, 
            null
        );
    }
    
    public ResolutionContext withResource(ResourceType resource) {
        return new ResolutionContext(contextNode, resource, thisType);
    }
    
    public ResolutionContext withThisType(Type thisType) {
        return new ResolutionContext(contextNode, resourceSpec, thisType);
    }
    
    public boolean isInLambdaScope() {
        return thisType != null;
    }
}
```

**Benefits**: 
- Cleaner constructor
- Easier to add new context parameters
- Immutable transformation methods

#### 1.2 Extract NodeResolver Interface
Create strategy pattern for different node types:

```java
public interface NodeResolver<T extends AstNode> {
    boolean canResolve(AstNode node);
    IRNode resolve(T node, ResolutionContext context, Analyzer analyzer);
}
```

Implementations:
- `LiteralResolver`
- `VariableResolver`
- `TraversalResolver`
- `FunctionCallResolver`
- `BinaryOperatorResolver`

**Benefits**:
- Single Responsibility Principle
- Easy to unit test each resolver independently
- Can add new node types without modifying Analyzer

#### 1.3 Consolidate Registry Access
Create single `OperationResolver` that handles both registry and infrastructure operations:

```java
public interface OperationResolver {
    boolean canResolve(String operationName);
    IRNode resolve(String operationName, List<IRNode> args);
}

// Implementations:
// - RegistryOperationResolver (uses OperationRegistry)
// - InfrastructureOperationResolver (handles equals, union, etc.)
// - CompositeOperationResolver (chains multiple resolvers)
```

**Benefits**:
- Eliminates the split between registry and "infrastructure" functions
- Easier to migrate infrastructure functions to registry
- Testable in isolation

### Phase 2: Improve Type Resolution (Medium Priority)

#### 2.1 Extract OverloadResolver Adapter Logic
Current `adapt()` method is doing too much. Split into:

```java
public interface TypeAdapter {
    boolean canAdapt(Type from, Type to);
    AdaptationResult adapt(IRNode node, Type targetType);
}

public record AdaptationResult(
    IRNode adaptedNode,
    int cost,
    boolean success
) {
    public static AdaptationResult success(IRNode node, int cost) {
        return new AdaptationResult(node, cost, true);
    }
    
    public static AdaptationResult failure() {
        return new AdaptationResult(null, Integer.MAX_VALUE, false);
    }
}
```

Implementations:
- `ExactMatchAdapter` (cost 0)
- `ImplicitCastAdapter` (cost 1)
- `NullAdapter` (handles empty collections)
- `LambdaTypeAdapter` (special lambda matching)

**Benefits**:
- Each adaptation strategy is testable independently
- Clear cost model
- Easy to add new adaptation rules

#### 2.2 Separate Signature Matching from Adaptation
```java
public class SignatureMatcher {
    public Optional<SignatureMatch> findBestMatch(
        List<SignatureDefinition> candidates,
        List<Type> argumentTypes
    );
}

public record SignatureMatch(
    SignatureDefinition signature,
    int totalCost,
    List<Type> adaptations
) {}
```

**Benefits**:
- Signature matching logic isolated from adaptation
- Can test matching independently
- Clearer algorithm

### Phase 3: Improve Testability (Medium Priority)

#### 3.1 Add Builder Pattern for Analyzer
```java
public class AnalyzerBuilder {
    private ResolutionContext context = ResolutionContext.empty();
    private OperationResolver operationResolver = defaultOperationResolver();
    private List<NodeResolver<?>> nodeResolvers = defaultNodeResolvers();
    
    public AnalyzerBuilder withContext(ResolutionContext context) {
        this.context = context;
        return this;
    }
    
    public AnalyzerBuilder withCustomResolver(NodeResolver<?> resolver) {
        this.nodeResolvers.add(resolver);
        return this;
    }
    
    public Analyzer build() {
        return new Analyzer(context, operationResolver, nodeResolvers);
    }
}
```

**Benefits**:
- Easy to create test analyzers with mock components
- Flexible configuration for different use cases
- Clear default configuration

#### 3.2 Extract Desugaring as Separate Pass
```java
public interface AstTransformer {
    AstNode transform(AstNode node);
    boolean isApplicable(AstNode node);
}

public class DesugaringPass {
    private final List<AstTransformer> transformers;
    
    public AstNode apply(AstNode node) {
        // Apply all applicable transformers
    }
}

// Example transformer:
public class ExistsCriteriaDesugarer implements AstTransformer {
    // exists(criteria) → where(criteria).exists()
}
```

**Benefits**:
- Desugaring rules are independently testable
- Easy to add new transformations
- Can be applied before analysis or as a separate tool

### Phase 4: Address Specific Issues (High Priority)

#### 4.1 Fix Recursive Variable Resolution
Current issue: `resolveVariable()` creates new Analyzer, which could recurse infinitely.

Solution: Use lazy evaluation and caching:

```java
public class VariableResolver implements NodeResolver<AstVariable> {
    private final Map<String, Supplier<IRNode>> variableSuppliers;
    
    public VariableResolver(ResolutionContext context) {
        this.variableSuppliers = Map.of(
            CONTEXT_VARIABLE, () -> analyzeContext(context),
            RESOURCE_VARIABLE, () -> new Resource(context.resourceSpec())
        );
    }
    
    private IRNode analyzeContext(ResolutionContext context) {
        // Analyze with empty context to prevent recursion
        ResolutionContext emptyContext = context.withResource(ResourceType.EMPTY);
        return new Analyzer(emptyContext, ...).analyze(context.contextNode());
    }
}
```

#### 4.2 Consolidate Registry Classes
Current state: `FunctionRegistry` and `OperationRegistry` both exist with overlapping concerns.

Proposal: Merge into single `OperationRegistry` with:
- All operations (functions and operators)
- Consistent naming strategy
- Single lookup method

```java
public final class OperationRegistry {
    private static final Map<String, List<SignatureDefinition>> OPERATIONS;
    private static final Map<String, String> OPERATOR_ALIASES;
    
    public static List<SignatureDefinition> getSignatures(String operationName) {
        // Handle aliases (e.g., "|" -> "union")
        String canonical = OPERATOR_ALIASES.getOrDefault(operationName, operationName);
        return OPERATIONS.getOrDefault(canonical, List.of());
    }
}
```

## Implementation Order

### Week 1: High-Impact, Low-Risk Changes
1. ✅ Create `ResolutionContext` record
2. ✅ Extract `VariableResolver` with recursion fix
3. ✅ Consolidate `FunctionRegistry` into `OperationRegistry`

### Week 2: Core Refactoring
4. ✅ Create `NodeResolver` interface
5. ✅ Extract individual resolver implementations
6. ✅ Update `Analyzer` to use resolver chain

### Week 3: Type System Improvements
7. ✅ Create `TypeAdapter` interface
8. ✅ Split `OverloadResolver.adapt()` into adapter implementations
9. ✅ Extract `SignatureMatcher`

### Week 4: Testing & Polish
10. ✅ Add `AnalyzerBuilder`
11. ✅ Extract `DesugaringPass`
12. ✅ Add comprehensive unit tests for each resolver
13. ✅ Integration tests remain unchanged (verify no behavior change)

## Testing Strategy

### Unit Tests (New)
- Test each `NodeResolver` independently with mock contexts
- Test each `TypeAdapter` with sample types
- Test `SignatureMatcher` with various signatures
- Test desugaring transformers independently

### Integration Tests (Existing)
- Keep all existing `FhirPathIntegrationTest` tests
- They should pass without modification
- Add new integration tests for edge cases

### Performance Tests
- Benchmark before/after refactoring
- Ensure no significant performance regression
- The additional abstraction layers should be minimal overhead

## Migration Path

### Backward Compatibility
- Keep existing public API (`FhirPath.toColumn()`)
- Internal refactoring only
- No breaking changes for users

### Incremental Migration
- Each phase can be completed independently
- Tests pass after each phase
- Can pause/resume refactoring between phases

## Success Metrics

1. **Complexity**: Reduce Analyzer cyclomatic complexity from ~30 to <10
2. **Testability**: Achieve >90% unit test coverage (currently ~60%)
3. **Maintainability**: New operation can be added with <5 lines of code
4. **Performance**: <5% performance regression
5. **Correctness**: All existing integration tests pass

## Risks & Mitigations

### Risk: Breaking existing behavior
**Mitigation**: Comprehensive integration test suite runs after each change

### Risk: Performance degradation
**Mitigation**: Benchmark critical paths; use interfaces (JIT can optimize)

### Risk: Over-engineering
**Mitigation**: Only extract abstractions when >2 implementations exist or testing requires it

## Additional Improvements (Future)

1. **Error Handling**: Create custom exception hierarchy for better error messages
2. **Validation**: Add pre-analysis validation pass
3. **Optimization**: Add IR optimization passes (constant folding, etc.)
4. **Debugging**: Add explain() method to show resolution steps
5. **Documentation**: Generate operation catalog from registry

