# Claude Code Reference

## Project Overview

This project implements FHIRPath to SQL translation.

## Important References

### FHIRPath Specification

The complete FHIRPath specification is available at `specs/FHIRPath.md`. This document should be consulted when:
- Implementing FHIRPath operators and functions
- Resolving questions about FHIRPath semantics and behavior
- Verifying correct interpretation of the FHIRPath type system
- Understanding FHIRPath grammar and expression evaluation rules

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
- The detailed design is in @DESIGN.md. Use it both when writing the code and also to record new or modified design choices.