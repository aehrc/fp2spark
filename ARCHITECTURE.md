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
- **Functional Operation Registry**: Operations are pure functions registered in `SparkOperationRegistry`
- **Simple dispatch**: `SparkCodeGenerator.visitOperation()` looks up operation by name and calls the function
- **Grouped registration**: Operations organized by domain in `ops/` package (`BooleanOps`, `ArithmeticOps`, etc.)
- **No reflection, annotations, or base classes** — just functions

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
- Numeric to Quantity: `INTEGER → QUANTITY`, `DECIMAL → QUANTITY` (cost 1)
- Temporal: `DATE → DATE_TIME` (cost 1)
- FHIR value extraction: `Fhir[T] → T` (cost 1)

### Implementation Status

**Phase 1 - Complete** (Simple signatures):
- ✅ Element-first type system with cardinality as metadata
- ✅ Simple signatures with concrete types
- ✅ Cardinality enforcement per FHIRPath spec
- ✅ Arithmetic, comparison, string, collection operations
- ✅ Quantity type with literal parsing, same-unit equality/comparison
- ✅ Temporal types (Date, DateTime, Time) with literals and equality/comparison

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

### Functional Operation Registry

Operations are pure functions dispatched through `SparkOperationRegistry`:

```java
@FunctionalInterface
public interface SparkOperationDef {
    Column generate(List<Column> args, List<IRNode> argNodes,
                    Type resultType, SparkCodeGenerator generator);
}
```

### Convenience Registration

- `registry.binary(name, fn)` — simple two-argument ops (e.g., `Column::and`)
- `registry.unary(name, fn)` — simple one-argument ops (e.g., `functions::not`)
- `registry.register(name, def)` — full control for type-dispatched or complex ops

### Operation Groups

Operations are organized by domain in the `ops/` package:

Each `*Ops` class registers related operations for a domain area (e.g., `BooleanOps` for `and`/`or`/`xor`/`implies`/`not`, `ArithmeticOps` for arithmetic operators, `CombineOps` for the combine operator). See the `ops/` package for the full set.

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
2. Register the operation in appropriate `*Ops` class or create a new one (~1-5 lines)
3. Tests (~10-20 lines)

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
└── codegen/
    └── spark/
        ├── SparkCodeGenerator.java    - IR → Column visitor
        ├── SparkOperationDef.java     - Functional interface for operations
        ├── SparkOperationRegistry.java - Operation name → function map
        ├── SparkTypeMapper.java       - FHIRPath → Spark type mapping
        ├── CollectionValue.java        - Column + cardinality wrapper
        ├── SparkOpContext.java         - Operation context with helpers
        └── ops/                       - Grouped operation registrations
            ├── BooleanOps.java
            ├── ArithmeticOps.java
            ├── ComparisonOps.java
            ├── CollectionOps.java
            └── FilteringOps.java
```

---

## Testing Strategy

**Primary strategy**: FHIRPath expression tests (full pipeline)

**Philosophy**:
- Invest in comprehensive, reusable FHIRPath expression test suite
- Tests serve as specification compliance suite, reusable across implementations

---

## References

### Design Documents

- **[docs/TYPE_SYSTEM_DESIGN.md](docs/TYPE_SYSTEM_DESIGN.md)** - Type system design, element-first model, implementation phases
- **[docs/CODEGEN_DESIGN.md](docs/CODEGEN_DESIGN.md)** - Code generator architecture, functional operation registry

### Specifications

- **[specs/FHIRPath.md](specs/FHIRPath.md)** - Official FHIRPath specification
- **[specs/FHIR_FHIRpath.md](specs/FHIR_FHIRpath.md)** - FHIR-specific FHIRPath extensions

### Coding Standards

- **[JAVA_CODING_STYLE.md](JAVA_CODING_STYLE.md)** - Java coding conventions
- **[CONTRIBUTING.md](CONTRIBUTING.md)** - Contribution guidelines
