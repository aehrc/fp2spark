Ra# Claude Code Reference

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

**Accessing Pathling:**

The `.local/pathling` directory is a **symlink** to the Pathling reference implementation.

**IMPORTANT:** Use symlink-following options when searching:
```bash
# Find files (use -L to follow symlinks)
find -L .local/pathling -name "StringCollection.java"

# Search code (use -R to follow symlinks, not -r)
grep -R "substring" .local/pathling/fhirpath/
```

**Key directories (relative paths via symlink):**
- `.local/pathling/fhirpath/` - FHIRPath implementation
- `.local/pathling/fhirpath/src/main/java/au/csiro/pathling/fhirpath/` - Core FHIRPath logic
- `.local/pathling/fhirpath/src/test/java/` - Test patterns and examples
- `.local/pathling/utilities/src/main/java/au/csiro/pathling/fhirpath/literal/` - Literal parsing utilities

**How to use:**
- Search for specific operators/functions: `grep -R "substring" .local/pathling/fhirpath/`
- Find specific classes: `find -L .local/pathling -name "StringLiteral.java"`
- Study SQL generation: Look for classes generating Spark Column expressions
- Understand patterns: Don't copy directly, but learn architectural patterns and approaches

**Examples to reference:**
- String escape sequences: `au.csiro.pathling.fhirpath.literal.StringLiteral`
- String operations: `au.csiro.pathling.fhirpath.collection.StringCollection`
- Test patterns: `au.csiro.pathling.fhirpath.dsl.SystemDslTest`

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

### Test Naming Conventions

**Test classes MUST be named by capability, not by implementation details:**

- ✅ **GOOD**: `TypesAndLiteralsTest`, `ArithmeticOperatorsTest`, `StringFunctionsTest`
- ❌ **BAD**: `Stage14Test`, `Issue15Test`, `Phase1Test`

**Rationale:** Test names should reflect what FHIRPath capability is being tested, making them:
- Discoverable by capability area
- Stable across refactorings
- Self-documenting

### Test Creation Workflow

When implementing a new FHIRPath capability, follow this workflow to ensure spec compliance:

**1. Identify Specification Sections**

From the GitHub issue, locate the FHIRPath spec sections:
```bash
# Example: Find string literal spec section
grep -n "String" specs/FHIRPath.md | grep -i literal
```

**2. Read Complete Spec Sections**

Read the ENTIRE referenced spec section(s), not just summaries:
```bash
# Use Read tool with offset/limit for large files
# Example: Read section starting at line 395
```

Extract ALL of:
- Required behaviors
- Examples provided in spec
- Edge cases mentioned
- Error conditions
- Special rules (e.g., escape sequences)

**3. Create Comprehensive Test Class**

Name format: `[Capability]Test.java`

Example structure:
```java
/**
 * Tests for FHIRPath [Capability Name].
 *
 * <p>Based on FHIRPath specification section X.Y: [Section Name]
 *
 * <p>Covers:
 * - All spec examples from section X.Y
 * - Edge cases: [list key edge cases]
 * - Error conditions: [list error conditions tested]
 */
public class CapabilityTest extends FhirPathTestBase {

    @TestFactory
    Stream<DynamicTest> testSpecExamples() {
        // Tests for ALL examples from spec
    }

    @TestFactory
    Stream<DynamicTest> testEdgeCases() {
        // Tests for edge cases mentioned in spec
    }
}
```

**4. Verify Completeness**

Before considering tests complete:
- [ ] Cross-reference against spec section(s)
- [ ] Verify EVERY example in spec has a test
- [ ] Verify EVERY edge case is covered
- [ ] Verify test class name reflects capability (not stage/issue)
- [ ] Verify tests are organized by spec subsections

**5. Run and Validate**

```bash
mvn test -Dtest=CapabilityTest
```

All tests must pass before creating PR.

### Critical Implementation Guidelines

**Learned from Stage 1.4 retrospective - follow these principles to avoid regressions:**

**When Test Failures Occur:**

1. **Assume existing tests are CORRECT** until proven otherwise
2. **Read the test code** to understand what it's testing
3. **Consult the spec** to verify expected behavior
4. **Only change tests** if they genuinely contradict the FHIRPath specification
5. **Document WHY** the test was wrong in your commit message

**Before Making Changes:**

- [ ] Verify your understanding against FHIRPath spec (not assumptions)
- [ ] Check if similar patterns exist elsewhere in codebase
- [ ] Ask: "Is the test wrong, or is my understanding wrong?"
- [ ] When in doubt, consult spec FIRST before changing code

**Core Semantic Rules:**

- **FHIRPath collections are ALWAYS one-dimensional arrays** - no nested arrays
- **Traversing MANY → MANY requires flatten** - e.g., `name.given` returns flat array
- **Specs are ground truth** - When in doubt, trust the spec over your mental model
- **Existing tests encode knowledge** - They may be teaching you something important

**Example - Collection Semantics:**

```java
// Given: Patient with multiple names, each with multiple given names
// name: [{ given: ["John", "James"] }, { given: ["Jane"] }]

// CORRECT: name.given returns flat array
List.of("John", "James", "Jane")

// WRONG: name.given does NOT return nested arrays
List.of(List.of("John", "James"), List.of("Jane"))  // ❌
```

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