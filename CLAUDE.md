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