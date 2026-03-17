# Design Insights (scratch/)

Consolidated design rationale, rejected alternatives, and open questions extracted from 31 design documents in `scratch/` (Oct 2024 - Jan 2025). Contains only what is **not captured** in ARCHITECTURE.md, TYPE_SYSTEM.md, docs/CODEGEN_DESIGN.md, or `.local/work/DESIGN_INSIGHTS.md`.

---

## 1. Design Philosophy / Principles

### "Make Simple Cases Simple"

The driving principle behind the functional registry (docs/CODEGEN_DESIGN.md) was: **most FHIRPath operations map to single SparkSQL API calls**. The original handler-based architecture treated the code generator as a framework needing extensibility points (strategy pattern, command objects, annotation-based binding). The realization that "this is a translator, not a fluent API" led to the dramatically simpler functional approach.

Source: `CODEGEN_SIMPLIFIED_WITH_TYPE_CHECKING.md`

### Catalyst-Inspired but Deliberately Simpler

The Spark Catalyst architecture was studied in detail (multi-phase resolution, rule-based transformations, `RuleExecutor` batches until fixed point). The project deliberately chose a **single-phase approach** because:

- FHIRPath's expression language is much simpler than SQL (no JOINs, subqueries, CTEs)
- Multi-phase resolution doubles the class count (unresolved + resolved nodes)
- Rule-based batches are overkill when overload resolution + cast insertion handles 95% of cases
- The single-phase approach is easier to understand and debug

Two-phase resolution was scored 31/40 vs 37/40 for the hybrid registry+visitor, primarily due to complexity and class count doubling. It remains a "nice-to-have" if optimization passes (constant folding, dead code elimination) become necessary.

Source: `CATALYST_ARCHITECTURE_RECOMMENDATIONS.md`, `ARCHITECTURE_ANALYSIS.md`

### Operation Consolidation: 18 Classes to 1

The decision to use a single generic `Operation` node (identified by name + resolved signature) rather than per-operation classes was driven by concrete metrics:

- **18+ classes** at 30% FHIRPath coverage, projected to **50+** at full coverage
- Type information defined in **3 places** per operation (SIGNATURES field, getType(), eval() switch)
- Each new function required **~50 lines** of boilerplate (class + constructor + SIGNATURES + getType() + eval())

The generic Operation node reduced this to **~5 lines** (registry entry + codegen case).

Source: `ARCHITECTURE_ANALYSIS.md` (§1.5 IRNode Subclass Inventory)

---

## 2. Key Decisions & Rationale

### Why Option 4 (Hybrid Registry + Visitor) Was Selected

Five architecture options were evaluated with a scoring matrix:

| Option | Type Safety | Reusability | Maintainability | Performance | Total |
|--------|-------------|-------------|-----------------|-------------|-------|
| 1: Visitor only | 9 | 9 | 8 | 9 | 35/40 |
| 2: Two-Phase (Catalyst) | 10 | 7 | 6 | 8 | 31/40 |
| 3: Registry only | 8 | 2 | 9 | 10 | 29/40 |
| **4: Hybrid (selected)** | **10** | **10** | **8** | **9** | **37/40** |
| Previous design | 7 | 2 | 5 | 10 | 24/40 |

Option 3 (Registry only) scored highest on maintainability but was rejected because it **doesn't solve multi-target support** — `eval()` still returns Spark `Column`. The hybrid approach separates signatures (registry) from code generation (visitor), addressing all identified issues.

Source: `ARCHITECTURE_ANALYSIS.md` (§3 Comparison Matrix)

### Why Option 5 Registry Design (TypeGroup) Was Selected

Five registry designs were evaluated for how signatures are defined. The key criterion was **zero overhead for single signatures**, since 60-70% of operations have exactly one signature:

- **Option 1** (Current + fixes): Too much mutation, violated single-registration principle
- **Option 2** (Fluent builder): Elegant but added abstraction for the common case
- **Option 3** (Method reference composition): Clean but type set composition was awkward
- **Option 4** (Explicit multi-registration): Verbose for multi-type patterns
- **Option 5** (TypeGroup — selected): `SignatureDefinition` IS-A `TypeGroup`, so single signatures have zero wrapper overhead; `TypeMapping` composes elegantly for multi-type patterns

Source: `REGISTRY_DESIGN_OPTIONS.md`

### Why Handler-Based Codegen Architecture Was Replaced

The original codegen design (Jan 2025, `CODEGEN_*.md` files) explored an elaborate handler-based architecture with:

- **Stateful command objects** per invocation (`CODEGEN_STATEFUL_HANDLERS.md`)
- **Annotation-based binding** for operation dispatch (`CODEGEN_ANNOTATION_BINDING.md`)
- **InvocationBinder** for automatic Column↔Wrapper boxing/unboxing (`CODEGEN_COLLECTION_WRAPPER_AND_BOXING.md`)
- **Three handler types**: Operation-based, Type-based, Generic (`CODEGEN_HANDLER_ORGANIZATION.md`)
- **Domain wrappers**: `Collection`, `Quantity`, `LambdaExpression` (`CODEGEN_TYPE_WRAPPERS.md`)

This was replaced by the functional registry because:
1. The handler architecture was designed for a framework; the actual need was a translator
2. Most operations are one-liners (`Column::and`, `functions::abs`)
3. Annotations + reflection added complexity without proportional benefit
4. The N×M problem (operations × types) was better solved by type-dispatched lambdas in a flat registry

The key insight from `CODEGEN_SIMPLIFIED_WITH_TYPE_CHECKING.md`: "This is a translator, not a fluent API."

### ResultSpec: Four Patterns Cover 100% of Result Types

The `ResultSpec` sealed interface was designed after analyzing all FHIRPath operations and finding exactly four patterns for computing result types:

| Pattern | Example | Frequency |
|---------|---------|-----------|
| **Static** — type known at definition time | `abs(Integer) → Integer` | ~70% |
| **InputType** — same as first argument | `where(Collection<T>) → Collection<T>` | ~15% |
| **EffectiveInputType** — element type of input | `first(Collection<T>) → T` | ~10% |
| **FhirSystemType** — FHIR to system mapping | `getValue(FhirType) → SystemType` | ~5% |

This sealed hierarchy enables exhaustive pattern matching and prevents ad-hoc result type computation.

Source: `TYPE_RESOLUTION_DESIGN.md`, `DESIGN.md`

### Type System: Element-First Design Validated by Expert Analysis

An expert language design analysis (`TYPE_SYSTEM_DESIGN_OPTIONS_EXPERT_ANALYSIS.md`) evaluated three type system alternatives:

1. **Cardinality-agnostic** (no CollectionType, everything is a collection): Simplest algebra (~50% fewer adaptation rules), but requires separate cardinality tracking metadata
2. **Hybrid with cardinality bounds**: Maximum precision (`[T]{1..3}`), but over-engineered
3. **Current element-first** (CollectionType as explicit wrapper): Best balance

The expert validated that the current element-first design is sound. The main complexity (element promotion rules, collection wildcards) is inherent to FHIRPath semantics, not design flaws. The real gap is **lack of type variables** for polymorphic operators.

Source: `TYPE_SYSTEM_DESIGN_OPTIONS_EXPERT_ANALYSIS.md`

### Handler Organization: The Type vs Operation Tension

When organizing codegen handlers, there's a fundamental tension:

| Approach | Adding New Operation | Adding New Type |
|----------|---------------------|-----------------|
| **Type-based** (one handler per type) | Touch many files | Add one file |
| **Operation-based** (one handler per op group) | Touch one file | Touch many files |

The project chose **operation-based** organization (e.g., `ComparisonOps`, `ArithmeticOps`) because new operations are added more frequently than new types, and heavily-overloaded operations (comparison: 7+ types) are better grouped by operation family.

Source: `CODEGEN_HANDLER_ORGANIZATION.md`

---

## 3. Unresolved Concerns / Open Questions

### Type Variables for Polymorphic Operators

The type system cannot express signatures where multiple parameters must have related types:

```
-- What we want to express:
in(X, [X]) → Boolean        -- element type must match collection element type
union([T], [T]) → [T]       -- both collections must have compatible types
equal(T, T) → Boolean       -- both operands should be same type
```

Currently these use `ANY` wildcards, which loses the type compatibility constraint. Three solutions were designed:

1. **TypeVariable + Unification**: Standard approach, clean but doesn't handle common type computation
2. **Custom Programmatic Resolvers**: Maximum flexibility, but scattered resolution logic
3. **CaptureType + AdaptationEngine** (recommended): Type variables with adaptation closure that automatically computes common types

The `AdaptationEngine.findCommonType()` algorithm was fully designed:
- Compute adaptation closure (all reachable types via adaptation rules) for both types
- Find intersection of closures
- Select common type with minimum total adaptation cost

**Example**: `2 in (2.0 | 3.2)` → `commonType(INTEGER, DECIMAL) = DECIMAL` → cast `2` to DECIMAL

**Status**: Designed but not implemented. The `ANY` wildcard approach works for now because the analyzer handles these operators as special cases.

Source: `TYPE_VARIABLES_AND_POLYMORPHIC_SIGNATURES.md`

### Analyzer Refactoring Plan (4 Phases)

A comprehensive refactoring plan was designed for the Analyzer:

**Phase 1 — Extract Resolution Strategies:**
- `ResolutionContext` value object (replace multiple constructor parameters)
- `NodeResolver<T extends AstNode>` strategy pattern (LiteralResolver, VariableResolver, etc.)
- `OperationResolver` to consolidate registry and infrastructure operations

**Phase 2 — Improve Type Resolution:**
- `TypeAdapter` interface (ExactMatchAdapter, ImplicitCastAdapter, NullAdapter, LambdaTypeAdapter)
- `SignatureMatcher` separated from adaptation logic

**Phase 3 — Improve Testability:**
- `AnalyzerBuilder` for creating test analyzers with mock components
- `DesugaringPass` as a separate AST transformation pass

**Phase 4 — Address Specific Issues:**
- Fix recursive variable resolution (lazy evaluation + caching)
- Consolidate `FunctionRegistry` into `OperationRegistry`

**Status**: None started. The plan identifies 4 weeks of work.

Source: `ANALYZER_REFACTORING_PLAN.md`

### Lambda Resolution via Signature Introspection

Four options were designed for resolving lambda-taking functions (where, select, exists, all, iif):

| Option | Approach | Complexity |
|--------|----------|-----------|
| 1: Early Signature Inspection | Get signatures before analyzing args | Low |
| 2: Lazy Argument Resolution | ArgumentResolver with caching | High |
| 3: Signature Metadata | Add `lambdaParameterIndices` to SignatureDefinition | Medium |
| **4: Signature Introspection** (recommended) | Inspect existing parameter types for LambdaType | **Minimal** |

Option 4 was recommended because it requires **no changes to SignatureDefinition** — it simply checks `sig.parameterTypes().stream().anyMatch(t -> t instanceof LambdaType)` to detect lambda parameters, then uses a fast path for normal functions and a lambda path for lambda functions.

Current problem: `isLambdaFunction()` uses hardcoded function names. Option 4 eliminates this by making the signature self-describing.

**Status**: Designed but not implemented.

Source: `LAMBDA_RESOLUTION_DESIGN_OPTIONS.md`

### Two-Phase Resolution (Catalyst-Style)

Deferred as "nice-to-have". Would enable:
- Rule-based optimization passes (constant folding, dead code elimination)
- Better error messages (can show unresolved tree)
- Inspection of unresolved expressions before type binding

The cost is significant: two parallel type hierarchies (Unresolved + Resolved nodes), and the current single-phase approach handles all current needs.

Source: `CATALYST_ARCHITECTURE_RECOMMENDATIONS.md`

### EvalHelper.java — Adopt or Remove

Also noted in `.local/work/DESIGN_INSIGHTS.md`. Decision still pending.

### Type Dispatch Inconsistency in SparkCodeGenerator

The current code generator dispatches on type inconsistently:
- **Group 1**: Uses result type (add, sub, multiply, abs)
- **Group 2**: Uses first argument type (gt, lt, comparisons)
- **Group 3**: Uses neither / hardcoded (upper, and)
- **Group 4**: Uses singularity from argument (count)

This was analyzed in `CODEGEN_TYPE_DISPATCH.md` with the conclusion that result type is the correct dispatch target for most operations, since the overload resolver has already selected the correct signature and inserted casts.

---

## 4. Codegen Refactoring Ideas (from CODEGEN_* docs)

The 11 CODEGEN_* documents (Jan 2025) pre-date the functional registry and were designed for an elaborate handler-based architecture. Most of these designs were superseded, but some ideas remain relevant:

### Still Relevant

- **InvocationBinder pattern**: Automatic Column↔Wrapper boxing/unboxing could reduce boilerplate if complex types (Quantity, temporal) grow. Currently not needed since most operations work directly with `Column`.
- **Handler precedence** (Operation→Type→Generic): The priority ordering for resolving which handler handles an operation. Currently implicit in the `*Ops` class organization.
- **Testing strategy**: The approach of testing at the Column expression level (not just end-to-end) was designed but the project uses end-to-end FHIRPath tests instead.

### Superseded (Archive)

- Stateful command objects per invocation → replaced by stateless lambdas in registry
- Annotation-based binding → replaced by explicit registration
- Domain wrappers (Collection, Quantity, LambdaExpression) → partially used (Quantity wrapper exists), but the full wrapper hierarchy was not needed
- Shared operation providers → replaced by flat registration in `*Ops` classes
- Factory method annotations → replaced by functional interface

---

## 5. Source Document Index

| Document | Topic | Status | Recommendation |
|----------|-------|--------|---------------|
| `ANALYSIS_01.md` | IRNode design options (5 options) | **Implemented** (Option 2+3 hybrid) | Archive |
| `ARCHITECTURE_ANALYSIS.md` | Comprehensive architecture analysis | **Implemented** (Option 4) | Archive |
| `DESIGN.md` | Architecture design (Option 4 detailed) | **Implemented** | Archive |
| `OPTION_4_DETAILED_DESIGN.md` | Option 4 implementation plan | **Implemented** | Archive |
| `REGISTRY_DESIGN_OPTIONS.md` | 5 registry designs (Option 5 selected) | **Implemented** | Archive |
| `TYPE_RESOLUTION_DESIGN.md` | ResultSpec sealed interface | **Implemented** | Archive |
| `REQUIREMENTS.md` | Original requirements | Superseded by issues | Archive |
| `fhirpath_type_system_design.md` | Early type system sketch | Superseded by TYPE_SYSTEM.md | Archive |
| `TYPE_SYSTEM_ANALYSIS.md` | Type hierarchy analysis | Captured in TYPE_SYSTEM.md | Archive |
| `TYPE_SYSTEM_DESIGN_OPTIONS_EXPERT_ANALYSIS.md` | Expert validation of type system | Key rationale in §2 above | **Keep** |
| `TYPE_VARIABLES_AND_POLYMORPHIC_SIGNATURES.md` | Type variables + CaptureType design | **Open** — not implemented | **Keep** |
| `CATALYST_ARCHITECTURE_RECOMMENDATIONS.md` | Catalyst analysis | Deferred (two-phase) | Archive |
| `ANALYZER_REFACTORING_PLAN.md` | 4-phase analyzer refactoring | **Open** — none started | **Keep** |
| `LAMBDA_RESOLUTION_DESIGN_OPTIONS.md` | Lambda resolution (4 options) | **Open** — not implemented | **Keep** |
| `CODEGEN_DESIGN.md` | Codegen design (handler-based) | **Superseded** by functional registry | Archive |
| `CODEGEN_DESIGN_DECISIONS.md` | Null handling, lambda semantics | Null handling rationale still relevant | Archive (captured above) |
| `CODEGEN_REFACTORING_PROPOSAL.md` | Handler-based refactoring | **Superseded** | Archive |
| `CODEGEN_ARCHITECTURE_ANALYSIS.md` | Codegen SOLID analysis | **Superseded** | Archive |
| `CODEGEN_TYPE_DISPATCH.md` | Type dispatch strategy | Insight captured in §3 above | Archive |
| `CODEGEN_HANDLER_ORGANIZATION.md` | Type vs operation organization | Insight captured in §2 above | Archive |
| `CODEGEN_SIMPLIFIED_WITH_TYPE_CHECKING.md` | "Translator not framework" insight | Key insight captured in §1 above | Archive |
| `CODEGEN_ANNOTATION_BINDING.md` | Annotation-based binding | **Superseded** | Archive |
| `CODEGEN_ANNOTATION_FACTORY_METHODS.md` | Factory method annotations | **Superseded** | Archive |
| `CODEGEN_COLLECTION_WRAPPER_AND_BOXING.md` | Collection wrapper + boxing | Partially relevant (§4 above) | Archive |
| `CODEGEN_STATEFUL_HANDLERS.md` | Command object pattern | **Superseded** | Archive |
| `CODEGEN_TYPE_WRAPPERS.md` | Domain wrapper design | Partially implemented (Quantity) | Archive |
| `CODEGEN_ELEMENT_TYPE_ANALYSIS.md` | Collection element type uses | Captured in TYPE_SYSTEM.md | Archive |
| `CODEGEN_SHARED_OPERATIONS.md` | Shared operation providers | **Superseded** | Archive |
| `CODEGEN_IMPLEMENTATION_EXAMPLES.md` | Code examples (handler-based) | **Superseded** | Archive |
| `CODEGEN_TESTING_STRATEGY.md` | Handler testing approach | Superseded by FHIRPath test suite | Archive |
| `CODEGEN_ADDITIONAL_CONSIDERATIONS.md` | Additional design considerations | Captured in existing docs | Archive |
