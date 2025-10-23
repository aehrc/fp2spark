# Architecture

This document describes the high-level architecture and design principles of the FHIRPath to SparkSQL translator.

## Overview

The system translates FHIRPath expressions into Apache Spark SQL Column expressions through a multi-layered architecture separating parsing, semantic analysis, and code generation.

**Pipeline:**
```
FHIRPath Expression
  ↓ ANTLR Parser
AST (Abstract Syntax Tree)
  ↓ Analyzer
IR (Intermediate Representation)
  ↓ Code Generator (Visitor)
Spark Column / SQL String
```

## Design Principles

### 1. Separation of Concerns

Each layer has a clear, focused responsibility:
- **Parser**: Converts FHIRPath text to AST
- **Analyzer**: Type checking, overload resolution, IR construction
- **IR**: Target-agnostic semantic representation
- **Code Generator**: Target-specific output (Spark, SQL Server, etc.)

### 2. Target Independence

IR is completely independent of execution targets. Code generation for different platforms (Spark, SQL Server, PostgreSQL) uses the Visitor pattern, enabling new targets without modifying existing code.

### 3. Single Source of Truth

Type information and operation signatures are defined once in centralized registries (`OperationRegistry`, type system). This eliminates redundancy and simplifies auditing against the FHIRPath specification.

### 4. Attributed IR Nodes

Type information is resolved during analysis and stored in IR nodes:
- Zero-cost type queries (`getShape()` is field access)
- No type recalculation during code generation
- Clean separation between type checking and code generation

---

## Layered Architecture

### Layer 1: Parser

**Components**: ANTLR grammar, generated parser/lexer, AST builder

**Responsibility**: Transform FHIRPath text into structured AST

**Input**: FHIRPath expression string
**Output**: AST tree

### Layer 2: Semantic Analysis

**Components**: `Analyzer`, `OperationResolver`, `OverloadResolver`, `OperationRegistry`

**Responsibilities**:
- Type checking with element-first type system
- Overload resolution based on argument types and cardinality
- Cardinality enforcement (FHIRPath spec compliance)
- Implicit type cast insertion
- Attributed IR node construction

**Key Subsystems**:
- **OperationResolver**: Facade for operation resolution
- **OperationRegistry**: Maps operation names to signatures
- **OverloadResolver**: Selects best-matching signature by adaptation cost
- **Type System**: See [docs/TYPE_SYSTEM_DESIGN.md](docs/TYPE_SYSTEM_DESIGN.md)

**Input**: AST tree
**Output**: Typed IR tree with resolved signatures

### Layer 3: Intermediate Representation (IR)

**Components**: `IRNode` interface, `Operation`, `Literal`, `Traversal`, `Cast`, `Lambda` nodes

**Responsibility**: Target-agnostic semantic representation of FHIRPath expressions

**Key Design**:
- **Generic Operation Node**: Single `Operation` class for all operations (identified by name + resolved signature)
- **Attributed Nodes**: Each node carries `Shape` (type + cardinality) from analysis
- **Visitor Pattern**: `accept(IRNodeVisitor<T>)` enables multiple code generation targets

**Benefits**:
- 18+ operation-specific classes → 1 generic `Operation` class
- Complete target independence
- Extensible via visitors

### Layer 4: Code Generation

**Components**: `IRNodeVisitor` implementations per target (`SparkCodeGenerator`, etc.)

**Responsibility**: Transform IR into target-specific executable code

**Architecture**: See [docs/CODEGEN_DESIGN.md](docs/CODEGEN_DESIGN.md)

**Key Design**:
- **Stateful Handlers**: Command objects created per invocation with operation, type, context
- **Hybrid Organization**: Operation-based, type-based, and generic handlers
- **Automatic Boxing/Unboxing**: `InvocationBinder` converts between `Column` and domain wrappers
- **Domain Wrappers**: `Collection`, `Quantity`, `LambdaExpression` used sparingly where they add value

**Code generators implement** `IRNodeVisitor<T>` where `T` is target type:
- `SparkCodeGenerator implements IRNodeVisitor<Column>`
- `SqlServerCodeGenerator implements IRNodeVisitor<String>`

**Adding New Targets**: Implement new visitor class. No changes to IR or other layers required.

---

## Type System

**Full documentation**: [docs/TYPE_SYSTEM_DESIGN.md](docs/TYPE_SYSTEM_DESIGN.md)

### Element-First Model

**Core Concepts**:
- **Element Types**: `INTEGER`, `STRING`, `BOOLEAN`, `DATE_TIME`, `DECIMAL`, `QUANTITY`, etc.
- **Cardinality**: `SINGLE` (0..1) or `MANY` (0..*)
- **Shape**: Combines element type + cardinality (e.g., `Shape(ONE, INTEGER)`)

**Key Principle**: Cardinality is metadata, not part of type hierarchy. No `CollectionType` - all types represent elements.

**Implicit Adaptations** (for overload resolution):
- Numeric widening: `INTEGER → DECIMAL` (cost 1)
- Temporal: `DATE → DATE_TIME` (cost 1)
- FHIR value extraction: `Fhir[T] → T` (cost 1)

### Implementation Status

**Phase 1 - Complete** (Simple signatures):
- ✅ Element-first type system with cardinality as metadata
- ✅ Simple signatures with concrete types
- ✅ Cardinality enforcement per FHIRPath spec
- ✅ Arithmetic, comparison, string, collection operations
- ✅ All 193 tests passing

**Phase 2 - Future** (Polymorphic signatures):
- Type variables for polymorphism (`?T`, `*T`)
- Type constraints (e.g., `T ∈ Arithmetic`)
- Mixed-type arithmetic (`2 + 2.5`)
- Polymorphic operators (`union`, `in`, `contains`)

### Signature System

**Current** (Phase 1):
```
ParamSpec(Type, Cardinality)
ResultTypeSpec(Type, Cardinality)
SignatureDefinition(params, result, minArity)
```

**Example signatures** enumerate types explicitly:
- `+(INTEGER, INTEGER) → INTEGER`
- `+(DECIMAL, DECIMAL) → DECIMAL`
- `count(*T) → ?INTEGER`

---

## Code Generation Architecture

**Full documentation**: [docs/CODEGEN_DESIGN.md](docs/CODEGEN_DESIGN.md)

### Stateful Handlers (Command Pattern)

Handlers are created **per invocation** with:
- `operation` - Operation name
- `dispatchType` - Type determining handler selection
- `context` - Code generation context

**Benefit**: Operation methods have clean signatures - no context parameter needed.

### Handler Organization

**Three handler types**:

1. **Operation-Based**: For heavily overloaded operations (comparison, arithmetic, math)
   - Benefit: Adding new comparison operator touches ONE file

2. **Type-Based**: For type-specific operations (string ops, quantity ops)
   - Benefit: All operations for a type in one place

3. **Generic**: For polymorphic operations (collection ops: count, where, select)
   - Benefit: Work on any type without specialization

### Automatic Boxing/Unboxing

`InvocationBinder` converts between SparkSQL `Column` and domain wrappers:

**Boxing** (when invoking handlers):
- `Column` → `Collection(column, isSingular)` for cardinality-aware operations
- `Column` → `Quantity(column)` for complex struct field access
- IR `Lambda` → `LambdaExpression(lambda, context)` for lambda operations

**Unboxing** (handler results):
- Wrappers → `Column` via `toColumn()`

**Philosophy**: Use wrappers **sparingly** - only where they add value. Most primitive operations work directly with raw `Column`.

### Domain Wrappers

**Collection**: Cardinality-aware operations (count, where, select)
**Quantity**: Complex struct field access (getValue, getUnit)
**LambdaExpression**: Lambda evaluation with `$this` binding

---

## Operation Registry

The `OperationRegistry` maps operation names to signature definitions.

**Design Goals**:
- Zero overhead for single signatures (most common)
- Minimal code to add functions (~5 lines)
- Single source of truth for type system
- Easy to audit against FHIRPath specification

**Current** (Phase 1): Signatures enumerate concrete types explicitly

**Future** (Phase 2): Type variables enable polymorphic signatures with fewer definitions

---

## Key Design Decisions

### Empty Collection Handling

**Decision**: Empty collections represented as SQL `NULL`

**Rationale**:
- Leverages Spark's Catalyst optimizer for NULL propagation
- Uses Spark 3.x non-ANSI functions (return NULL on error, not exceptions)
- Matches FHIRPath empty semantics naturally

### Cardinality Enforcement

**Decision**: Compile-time cardinality checking in Analyzer

**FHIRPath Spec Requirements**:
- Math operators require **single elements** (§3559-3566)
- Comparison operators require **single values** (§3196-3197)

**Implementation**: `OverloadResolver` validates argument cardinality against parameter requirements, throws `CardinalityMismatchException` on violations.

### Visitor Pattern for Multi-Target

**Decision**: Use Visitor pattern instead of target-specific methods in IR nodes

**Benefits**:
- IR remains target-agnostic
- Adding targets requires only implementing new visitor
- Zero impact on existing code
- Supports diverse output types (Column, String, etc.)

### IR Node Design

**Decision**: Single generic `Operation` class instead of operation-specific classes

**Benefits**:
- 18+ classes → 1 class
- Target independence
- Easy to add new operations (no new IR classes needed)

---

## Extension Points

### Adding a New Function

1. Add signature to `OperationRegistry` (~5 lines)
2. Add handler method with `@Operation` annotation (~5-10 lines)
3. Tests (~10-20 lines)

### Adding a New Handler

1. Extend `AnnotatedOperationHandler`
2. Implement operation methods with `@Operation` annotations
3. Register in `HandlerRegistry.standard()`

### Adding a New Target

1. Implement `IRNodeVisitor<T>` for target
2. No changes to IR, analyzer, or other targets

**Effort**: 1-2 weeks per target
**Risk**: Low (isolated changes)

---

## Performance Characteristics

- **Type queries**: Zero cost (field access on IR nodes)
- **Type resolution**: Once during analysis, cached in IR
- **Visitor dispatch**: Virtual method call, typically inlined by JVM
- **Generated Spark code**: Identical to hand-written
- **Optimization**: Delegated to Spark's Catalyst optimizer

---

## Package Organization

```
com.example.fhirpath/
├── analyzer/              - AST → IR transformation
│   └── Analyzer.java
├── operation/             - Operation resolution subsystem
│   ├── OperationResolver.java
│   ├── OperationRegistry.java
│   ├── OverloadResolver.java
│   └── ...
├── operation/signature/   - Type signature specifications
│   ├── SignatureDefinition.java
│   ├── ParamSpec.java
│   ├── ResultTypeSpec.java
│   └── ...
├── typing/                - Type system (element-first model)
│   ├── Type.java
│   ├── Shape.java
│   ├── Cardinality.java
│   └── ...
├── ir/                    - Intermediate representation nodes
│   ├── IRNode.java
│   ├── Operation.java
│   ├── Literal.java
│   └── ...
└── codegen/               - Code generation (future home)
```

---

## Testing Strategy

**Full documentation**: [docs/CODEGEN_TESTING_STRATEGY.md](docs/CODEGEN_TESTING_STRATEGY.md)

**Two-layer approach**:

1. **Component Tests**: Wrappers, utilities in isolation
2. **FHIRPath Expression Tests**: Full pipeline with FHIRPath expressions (primary strategy)

**Philosophy**:
- Skip handler/CodeGen tests (plumbing between components)
- Invest in comprehensive, reusable FHIRPath expression test suite
- Tests serve as specification compliance suite, reusable across implementations

---

## References

### Design Documents

- **[docs/TYPE_SYSTEM_DESIGN.md](docs/TYPE_SYSTEM_DESIGN.md)** - Type system design, element-first model, implementation phases
- **[docs/CODEGEN_DESIGN.md](docs/CODEGEN_DESIGN.md)** - Code generator architecture, stateful handlers, domain wrappers
- **[docs/CODEGEN_TESTING_STRATEGY.md](docs/CODEGEN_TESTING_STRATEGY.md)** - Testing strategy and philosophy

### Specifications

- **[specs/FHIRPath.md](specs/FHIRPath.md)** - Official FHIRPath specification
- **[specs/FHIR_FHIRpath.md](specs/FHIR_FHIRpath.md)** - FHIR-specific FHIRPath extensions

### Coding Standards

- **[JAVA_CODING_STYLE.md](JAVA_CODING_STYLE.md)** - Java coding conventions
- **[CONTRIBUTING.md](CONTRIBUTING.md)** - Contribution guidelines
