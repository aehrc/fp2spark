# Architecture

This document describes the high-level architecture and design principles of the FHIRPath to SparkSQL translator.

## Overview

The system translates FHIRPath expressions into Apache Spark SQL Column expressions through a multi-layered architecture that separates parsing, semantic analysis, and code generation.

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

The architecture is organized into distinct layers, each with a clear responsibility:

- **Parser Layer**: Converts FHIRPath text to AST
- **Semantic Analysis**: Type checking and overload resolution
- **IR Layer**: Target-agnostic intermediate representation
- **Code Generation**: Target-specific output (Spark, SQL Server, etc.)

### 2. Target Independence

The IR (Intermediate Representation) is completely independent of any execution target. Code generation for different targets (Spark, SQL Server, PostgreSQL) is implemented via the Visitor pattern, allowing new targets to be added without modifying existing code.

### 3. Single Source of Truth

Type information and function signatures are defined once in a centralized registry. This eliminates redundancy and makes the system easier to maintain and audit against the FHIRPath specification.

### 4. Attributed IR Nodes

Type information is resolved during analysis and stored directly in IR nodes. This enables:
- Zero-cost type queries (`getType()` is simple field access)
- No type recalculation during code generation
- Clear separation between type checking and code generation

## Layered Architecture

### Layer 1: Parser

**Components:** ANTLR grammar, generated parser/lexer, AST builder

**Responsibility:** Transform FHIRPath text into a structured AST representing the syntax.

**Key Types:**
- `AstNode` - Base interface for AST nodes
- `AstLiteral`, `AstBinaryOperator`, `AstFunctionCall` - Concrete AST nodes

**Input:** FHIRPath expression string
**Output:** AST tree

### Layer 2: Semantic Analysis

**Components:** Analyzer, OperationRegistry, OverloadResolver

**Responsibility:**
- Type checking
- Overload resolution for operators and functions
- Insertion of implicit type casts
- Creation of attributed IR nodes

**Key Design:**
- **OperationRegistry**: Maps operation names to signature definitions
- **OverloadResolver**: Selects best-matching signature based on argument types
- **Type System**: Defined in [TYPE_SYSTEM.md](TYPE_SYSTEM.md)

**Input:** AST tree
**Output:** Typed IR tree with resolved signatures

### Layer 3: Intermediate Representation (IR)

**Components:** IRNode interface, Operation, Literal, Traversal, Cast nodes

**Responsibility:** Represent the semantic structure of FHIRPath expressions in a target-agnostic way.

**Key Design Decisions:**

1. **Generic Operation Node**: Instead of specific classes for each operator/function (Add, Abs, Subtract, etc.), a single `Operation` class handles all operations, identified by name and resolved signature.

2. **Attributed Nodes**: Each `Operation` stores its `ResolvedSignature`, enabling zero-cost type queries.

3. **Visitor Pattern**: IR nodes expose `accept(IRNodeVisitor<T>)` instead of target-specific methods like `eval()`.

**Key Types:**
```java
sealed interface IRNode {
    Type getType();
    <T> T accept(IRNodeVisitor<T> visitor);
}

record Operation(
    String name,
    List<IRNode> args,
    ResolvedSignature signature
) implements IRNode
```

**Benefits:**
- 18+ operation classes → 1 generic Operation class
- Complete target independence
- Extensible via visitors

### Layer 4: Code Generation

**Components:** IRNodeVisitor implementations (SparkCodeGenerator, SqlServerCodeGenerator, etc.)

**Responsibility:** Transform IR into target-specific executable code.

**Key Design:**

Code generators implement `IRNodeVisitor<T>` where `T` is the target type:
- `SparkCodeGenerator implements IRNodeVisitor<Column>`
- `SqlServerCodeGenerator implements IRNodeVisitor<String>`

Each visitor traverses the IR tree and generates appropriate code for its target platform.

**Adding New Targets:** Implement a new visitor class. No changes to IR or other layers required.

## Type System

The type system is fully documented in [TYPE_SYSTEM.md](TYPE_SYSTEM.md).

### Key Concepts

**Element Types and Shapes:**
- Element types: `INTEGER`, `STRING`, `BOOLEAN`, `DATE_TIME`, etc.
- Cardinality: `?T` (optional/single) and `*T` (many/collection)
- Combined: "shapes" like `?INTEGER` or `*STRING`
**Implicit Adaptations:**

The type system supports implicit conversions with costs for overload resolution:
- Numeric widening: `INTEGER → DECIMAL` (cost 1)
- Temporal: `DATE → DATE_TIME` (cost 1)
- FHIR value extraction: `Fhir[T] → T` (cost 1)


TODO: Complete the high level  design.

See [TYPE_SYSTEM.md](TYPE_SYSTEM.md) for complete details.

## Operation Registry

### Design: TypeGroup Pattern

The `OperationRegistry` maps operation names to signature definitions using a minimal abstraction pattern:

**Key Principles:**
- Zero overhead for single signatures (most common case)
- Elegant composition for multi-type operations
- Method references for common patterns
- Single source of truth for the type system

**Example:**
```java
// Single signature - direct, no wrapper
register("pow",
    Signatures.binaryFunc(DECIMAL, DECIMAL, DECIMAL)
)

// Multi-type with method reference
register("abs",
    forTypes(TypeSets.NUMERIC_WITH_QUANTITY).define(Signatures::unaryOp)
)
```

**Benefits:**
- Easy to audit against FHIRPath specification
- Minimal code to add new functions (~5 lines vs ~50 lines)
- Type definitions in one place (not duplicated)

## Key Design Decisions

### Empty Literal Handling

**Decision:** No special optimization for empty literals (`{}`) at the IR or code generation level.

**Rationale:**
- Rely on Spark's Catalyst optimizer for NULL propagation and constant folding
- Avoid duplicating optimization logic
- Maintain clear separation of concerns:
  - IR: logical structure
  - Code generation: correctness
  - Catalyst: optimization

**Example:** `a > {}` generates `Column(a > NULL)`, which Catalyst optimizes to `NULL` in the physical plan.

### Visitor Pattern for Multi-Target Support

**Decision:** Use Visitor pattern instead of target-specific methods in IR nodes.

**Benefits:**
- IR remains completely target-agnostic
- Adding new targets requires only implementing a new visitor
- Zero impact on existing code when adding targets
- Supports diverse output types (Column, String, etc.)

**Trade-offs:**
- Slightly more verbose than direct method calls
- Requires exhaustive visitor methods for all IR node types

## Extension Points

### Adding a New Function

1. Add signature to `OperationRegistry` (~5 lines)
2. Implement code generation in each visitor (~1-2 lines per target)

### Adding a New Target

1. Implement `IRNodeVisitor<T>` for the target
2. No changes to IR, analyzer, or other targets required

**Effort:** ~1-2 weeks per target
**Risk:** Low (isolated changes)

## Performance Characteristics

- **Type queries**: Zero cost (field access)
- **Type resolution**: Once during analysis
- **Visitor dispatch**: Typically inlined by JVM
- **Generated code**: Identical to hand-written for Spark

## References

- **Type System**: [TYPE_SYSTEM.md](TYPE_SYSTEM.md) - Complete type system specification
- **Detailed Design**: [DESIGN.md](DESIGN.md) - Implementation details and examples
- **FHIRPath Spec**: `specs/FHIRPath.md` - Official FHIRPath specification
- **Coding Style**: [JAVA_CODING_STYLE.md](JAVA_CODING_STYLE.md) - Java coding conventions
