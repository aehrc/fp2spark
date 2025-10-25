# Claude Code Reference

## Project Overview

This project implements FHIRPath to SQL translation.

## Pathling Reference Implementation

The `.local/pathling/` directory contains the Pathling project, a mature FHIRPath to SparkSQL implementation. Use it as a reference when implementing FHIRPath capabilities:

**When to consult Pathling:**
- **Parsing FHIRPath literals**: See how String, Integer, Decimal, Boolean literals are parsed
- **SQL generation**: Study how FHIRPath expressions translate to Spark Column operations
- **Type system**: Understand FHIRPath type handling and conversions
- **Operator implementation**: Reference existing operator logic (arithmetic, comparison, boolean)
- **Function implementation**: Learn patterns for implementing FHIRPath functions

**Key directories:**
- `.local/pathling/fhirpath/` - FHIRPath implementation
- `.local/pathling/fhirpath/src/main/java/au/csiro/pathling/fhirpath/` - Core FHIRPath logic
- `.local/pathling/fhirpath/src/test/java/` - Test patterns and examples

**How to use:**
- Search for specific operators/functions: `grep -r "LiteralPath" .local/pathling/fhirpath/`
- Study SQL generation: Look for classes generating Spark Column expressions
- Understand patterns: Don't copy directly, but learn architectural patterns and approaches

**Important:** Pathling targets full FHIR support (Phase 2+). Our Phase 1 focuses on System types only, so adapt patterns accordingly.

## Working Files and Collaborative Documents

**IMPORTANT:** When collaborating on designs, specifications, or other working documents, generate all working versions in the `.local/work/` directory.

This directory is used for:
- Detailed design documents in progress
- Architecture proposals and alternatives
- Analysis and planning documents
- Other collaborative working files

**Guidelines:**
- Place work-in-progress documents in `.local/work/`
- Once finalized, migrate important documents to appropriate locations (e.g., `docs/`, root directory)
- The `.local/` directory is typically gitignored, so commit important content elsewhere when ready

## Important References

### FHIRPath Specification

The complete FHIRPath specification is available at `specs/FHIRPath.md`. This document should be consulted when:
- Implementing FHIRPath operators and functions
- Resolving questions about FHIRPath semantics and behavior
- Verifying correct interpretation of the FHIRPath type system
- Understanding FHIRPath grammar and expression evaluation rules

### FHIR-Specific FHIRPath Binding

The FHIR-specific extensions and bindings for FHIRPath are documented in `specs/FHIR_FHIRpath.md`. This document should be consulted when:
- Understanding how FHIRPath works with FHIR resources and data types
- Implementing FHIR-specific functions (e.g., `getValue()`, `hasValue()`, `resolve()`, `extension()`)
- Handling FHIR primitive type conversions and mappings to FHIRPath types
- Working with FHIR Quantity conversions to FHIRPath System.Quantity
- Understanding FHIR-specific variables (`%resource`, `%rootResource`)
- Implementing FHIR-specific operators and equivalence rules

### Searching Large Specification Files

**IMPORTANT:** The specification files `specs/FHIRPath.md` and `specs/FHIR_FHIRpath.md` are very large (hundreds of KB) and should NOT be loaded entirely into LLM context.

**Instead, use search tools to find specific information:**
- Use `Grep` tool to search for specific operators, functions, or keywords
- Use `Read` tool with offset/limit to read specific sections after locating them
- Examples:
  ```bash
  # Search for a specific function
  grep -n "substring" specs/FHIRPath.md

  # Search for type information
  grep -n "System.Integer" specs/FHIRPath.md

  # Search for FHIR-specific features
  grep -n "getValue()" specs/FHIR_FHIRpath.md
  ```

## Testing Guidelines

### FHIRPath Function/Operator Unit Tests

For writing or reviewing unit tests for FHIRPath functions and operators, use the **fhirpath-test-writer agent**:

```
/task Use fhirpath-test-writer to write tests for [function name]
```

**Testing Philosophy:**
- We rely on SparkSQL's underlying implementations to work correctly
- Focus tests on FHIRPath-specific behavior and edge cases only
- Do NOT perform exhaustive testing of the underlying SQL operations

**The agent will:**
1. Read the FHIRPath specification for the function
2. Extract all spec examples and conditions
3. Generate minimal, focused test cases
4. Write complete test classes following project conventions

**Reference:** See `src/test/java/com/example/fhirpath/ir/string/SubstringTest.java` for an example of the testing style the agent produces.

**Agent location:** `.claude/agents/fhirpath-test-writer.md`

## Project Guidelines

### Java Coding Style

See [JAVA_CODING_STYLE.md](JAVA_CODING_STYLE.md) for detailed coding conventions.

### Architecture and Design

See [ARCHITECTURE.md](ARCHITECTURE.md) for system architecture and design patterns.

### Contributing

For commit message guidelines, pull request process, and other contribution guidelines, see [CONTRIBUTING.md](CONTRIBUTING.md).