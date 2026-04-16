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

### 5. Static Validity

All type and cardinality checks are performed statically during analysis — not at runtime. An expression that passes analysis is guaranteed to evaluate successfully on any conformant dataset, regardless of actual data values or collection sizes. This is an intentional deviation from the FHIRPath specification, which permits runtime singleton evaluation (coercing a 1-element collection to a scalar) and defers some type checks to evaluation time. In this system, operators that require singleton input (e.g., `is()`, `as()`, math, comparison) reject `MANY`-cardinality arguments at analysis time, even if a particular dataset might produce only one element at runtime.

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
- **OperationRegistry**: Maps operation names to signature definitions (single source of truth)
- **OverloadResolver**: Selects best-matching signature by adaptation cost
- **Type System**: See [TYPE_SYSTEM.md](TYPE_SYSTEM.md)

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

**Code generators implement** `IRNodeVisitor<T>` where `T` is target type (e.g., `Column` for Spark, `String` for SQL Server). Adding new targets requires only implementing a new visitor — no changes to IR or other layers.

---

## Type System

**Full documentation**: [TYPE_SYSTEM.md](TYPE_SYSTEM.md)

The type system uses an **element-first model**: types represent individual elements (`INTEGER`, `STRING`, `QUANTITY`, etc.), and cardinality (`SINGLE` or `MANY`) is orthogonal metadata — not part of the type hierarchy. The combination of element type + cardinality forms a **Shape** (e.g., `Shape(SINGLE, INTEGER)`).

This design enables clean overload resolution: the `OverloadResolver` can independently match argument types and validate cardinality constraints, inserting implicit adaptations (numeric widening, temporal promotion, FHIR value extraction) when needed.

---

## Key Design Decisions

For architectural decisions that affect FHIRPath specification compliance, see
**[SPEC_DIVERGENCES.md](SPEC_DIVERGENCES.md)**.

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

## Testing Strategy

**Primary strategy**: FHIRPath expression tests (full pipeline)

**Philosophy**:
- Invest in comprehensive, reusable FHIRPath expression test suite
- Tests serve as specification compliance suite, reusable across implementations

---

## References

### Design Documents

- **[TYPE_SYSTEM.md](TYPE_SYSTEM.md)** - Element-first type system, adaptation costs, signature syntax
- **[docs/CODEGEN_DESIGN.md](docs/CODEGEN_DESIGN.md)** - Code generator architecture, functional operation registry

### Specifications

- **[specs/FHIRPath.md](specs/FHIRPath.md)** - Official FHIRPath specification
- **[specs/FHIR_FHIRpath.md](specs/FHIR_FHIRpath.md)** - FHIR-specific FHIRPath extensions

### Coding Standards

- **[JAVA_CODING_STYLE.md](JAVA_CODING_STYLE.md)** - Java coding conventions
- **[CONTRIBUTING.md](CONTRIBUTING.md)** - Contribution guidelines
