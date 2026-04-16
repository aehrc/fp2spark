Ra# Claude Code Reference

## Project Overview

This project implements FHIRPath to SQL translation.

## Pathling Reference Implementation

The `.local/pathling/` directory (symlink) contains the Pathling project, a mature FHIRPath to SparkSQL implementation. Use it as a reference when implementing FHIRPath capabilities.

**IMPORTANT:** Use symlink-following options when searching (e.g., `find -L`, `grep -R`).

**Key directories:**
- `.local/pathling/fhirpath/src/main/java/au/csiro/pathling/fhirpath/` - Core FHIRPath logic
- `.local/pathling/fhirpath/src/test/java/` - Test patterns and examples
- `.local/pathling/utilities/src/main/java/au/csiro/pathling/fhirpath/literal/` - Literal parsing

## Working Files

Working documents go in `.local/work/` (gitignored). Migrate finalized content to appropriate locations.

## Testing Guidelines

### Test Naming

Test classes MUST be named by capability: `StringFunctionsTest` (not `Stage14Test` or `Issue15Test`).

### Core Rules

- **Assume existing tests are CORRECT** — consult the spec before changing them
- **FHIRPath collections are ALWAYS one-dimensional** — no nested arrays
- **Specs are ground truth** — trust the spec over your mental model
- See [TESTING.md](TESTING.md) for test DSL and guidelines, [CONTRIBUTING.md](CONTRIBUTING.md) for workflow

### Pathling Compatibility Tests

Compat tests in `src/test/java/com/example/fhirpath/compat/` validate behavior against Pathling. Test classes are mirrors of Pathling's tests and should not be modified for fp2sql-specific exclusions.

Expected failures (due to intentional design differences like strict typing) are defined separately in `CompatExclusions.java` using the XFAIL mechanism. Add new exclusions there, not in test classes.

### FHIRPath Test Writer Agent

For writing unit tests for FHIRPath functions/operators, use the `fhirpath-test-writer` agent:

```
/task Use fhirpath-test-writer to write tests for [function name]
```

Reference: `src/test/java/com/example/fhirpath/ir/string/SubstringTest.java`
Agent location: `.claude/agents/fhirpath-test-writer.md`

## Specification Divergences

[SPEC_DIVERGENCES.md](SPEC_DIVERGENCES.md) documents intentional architectural differences between fp2sql and the FHIRPath specification. It is the authority for deciding whether a compatibility-suite exclusion is a valid design choice vs a bug or missing feature.

**Rules:**
- Any exclusion in `config.yaml` classified as `type: design` or `type: wontfix` **MUST** trace back to a documented decision in SPEC_DIVERGENCES.md.
- **Do NOT** add or modify entries in SPEC_DIVERGENCES.md without explicit user approval. Propose the change and wait for confirmation before writing it.

## Project Guidelines

- **Java Coding Style**: [JAVA_CODING_STYLE.md](JAVA_CODING_STYLE.md)
- **Architecture**: [ARCHITECTURE.md](ARCHITECTURE.md)
- **Contributing**: [CONTRIBUTING.md](CONTRIBUTING.md)
