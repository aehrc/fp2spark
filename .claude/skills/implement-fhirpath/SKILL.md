---
name: implement-fhirpath
description: >
  End-to-end implementation of a FHIRPath feature from GitHub issue to a reviewed PR. Use this skill whenever
  the user asks to implement a FHIRPath function, operator, or capability referenced by a GitHub issue number.
  Trigger on phrases like "implement issue #X", "add FHIRPath function X", "implement X operator",
  "build feature for issue #X", or any request to implement a FHIRPath feature end-to-end. Also trigger
  when the user provides an issue number and expects implementation work, even if they don't say "implement"
  explicitly (e.g., "let's do #42", "work on issue 15", "pick up #28").
---

# Implement FHIRPath Feature

This skill drives end-to-end implementation of a FHIRPath feature: from reading the GitHub issue through to a reviewed PR. It produces working code, not plans.

The repo is `piotrszul/fp2spark` on GitHub.

## Multi-Function Issues

When an issue covers multiple functions or operators, assess whether they can all be implemented in a single commit or whether splitting into multiple commits makes more sense (e.g., when functions have different complexity levels or touch different parts of the codebase).

If splitting, create a brief plan listing the commits, then execute Steps 3–8 for each commit before proceeding to Step 9 (review) and Step 10 (PR). All commits go on the same feature branch and into a single PR.

## Workflow

Execute these steps sequentially. Do NOT stop to ask for feedback unless explicitly indicated — keep moving forward.

### Step 1: Understand the Scope

Read the GitHub issue to understand what needs to be built:

```bash
gh issue view <NUMBER> --repo piotrszul/fp2spark
```

The issue typically contains:
- **Scope**: which functions/operators to implement
- **Design suggestions**: treat these as guidance, not requirements — you may deviate if you find a better approach during implementation

Note any acceptance criteria or test expectations mentioned in the issue.

### Step 2: Create the Feature Branch

Follow CONTRIBUTING.md conventions exactly:

```bash
git checkout main && git pull
git checkout -b issue/<NUMBER>-<short-description>
```

Branch naming format: `issue/<number>-<kebab-case-description>` (e.g., `issue/42-string-functions`).

### Step 3: Research the FHIRPath Specification

Before writing any code, understand the feature's requirements from the spec. Use the `fhirpath-spec` skill to look up the feature definition, semantics, examples, and any FHIR-specific bindings.

Gather:
- Function/operator signature(s)
- Input/output types and collection behavior
- Empty collection propagation rules
- Edge cases and spec examples
- Type coercion requirements

**If requirements are unclear from the spec**, check the Pathling reference implementation:
- `.local/pathling/fhirpath/src/main/java/au/csiro/pathling/fhirpath/`
- Use `grep -r` with symlink following to search

**If still unresolved after checking Pathling**, ask the user for clarification. Do not guess at ambiguous semantics.

### Step 4: Design the Implementation

#### 4a: Consult the Pathling Reference Implementation

**Always** check how Pathling implements the feature before writing any Spark code generation. Pathling is a mature FHIRPath-to-SparkSQL implementation and its code generation patterns are directly relevant — this project targets the same Spark runtime.

Search Pathling for the feature:
- `.local/pathling/fhirpath/src/main/java/au/csiro/pathling/fhirpath/`
- Use `grep -r` with symlink following to find relevant classes
- Look for the Spark Column/SQL expressions Pathling uses — these are often more idiomatic and efficient than what you'd write from scratch

Pay particular attention to:
- **Which Spark functions** Pathling uses (e.g., `aggregate()` vs `forall()`, `transform()` vs manual iteration)
- **How it handles singular vs array columns** — Pathling has solved the same representation challenges
- **Null/empty handling patterns** — how it implements FHIRPath empty collection semantics in Spark
- **Type coercion approaches** — how it bridges FHIRPath types to Spark types

Adapt Pathling's approach to this project's architecture rather than reinventing from first principles.

#### 4b: Design Against This Project's Architecture

The architecture has four layers — identify which layers need changes:

1. **Analyzer** (`src/main/java/.../analyzer/`): Operation signatures in `OperationRegistry`, type resolution
2. **IR** (`src/main/java/.../ir/`): Usually no changes needed (generic `Operation` node handles most cases)
3. **Code Generator** (`src/main/java/.../codegen/spark/`): Operation implementations in `ops/` classes
4. **Tests** (`src/test/java/`): DSL-based tests extending `FhirPathTestBase`

For a typical new function, changes usually involve:
- Adding the signature to `OperationRegistry` (~5 lines)
- Registering the Spark implementation in the appropriate `*Ops` class (~1-20 lines)
- Writing tests (~20-50 lines)

Check how similar existing operations are implemented — follow the same patterns.

**If the feature requires new architectural capabilities** (new IR node types, new type system features, new code generation patterns), stop and ask the user for feedback on the design approach before implementing.

### Step 5: Implement and Write Tests

Write the implementation and tests together. Follow these guidelines:

**Implementation:**
- Follow patterns in ARCHITECTURE.md and CODEGEN_DESIGN.md
- Register operations in `OperationRegistry` with correct signatures
- Implement Spark code generation in the appropriate `*Ops` class under `codegen/spark/ops/`
- Use proper Java/Spark types (e.g., `DataTypes.createDecimalType()`, not `cast("double")`)

**Tests:**

Always use the `fhirpath-test-designer` skill to design tests. It applies input domain partitioning to systematically identify test cases across dimensions (core semantics, emptiness, cardinality, element types, etc.) and generates code using the project's fluent DSL. This ensures consistent, spec-grounded test coverage.

The skill will:
1. Research the spec for the feature
2. Build a test matrix covering relevant dimensions
3. Generate test code extending `FhirPathTestBase`

Test classes are named by capability (e.g., `StringFunctionsTest`), never by issue number.

### Step 6: Run Tests and Fix Failures

```bash
# Run new tests first to verify they work
mvn test -Dtest=<NewTestClass>

# Then run full suite to check for regressions
mvn test
```

Iterate until:
1. All NEW tests pass
2. No regressions in existing tests

If a test failure is ambiguous, check the spec before assuming the test is wrong — existing tests should be treated as correct unless the spec clearly contradicts them.

### Step 7: Format Code

```bash
mvn spotless:apply
```

### Step 8: Commit

Write a conventional commit message referencing the issue:

```bash
git add <specific-files>
git commit -m "$(cat <<'EOF'
feat: <short summary> (#<NUMBER>)

<what changed and why — a few sentences max>

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
EOF
)"
```

Follow the commit format in CONTRIBUTING.md: type prefix, short summary, optional body explaining what and why.

### Step 9: Push and Create PR

```bash
git push -u origin issue/<NUMBER>-<short-description>
```

Create the PR referencing the issue:

```bash
gh pr create --repo piotrszul/fp2spark --title "<short title>" --body "$(cat <<'EOF'
## Summary
- <what was implemented>
- <key design decisions>

Closes #<NUMBER>

## Test plan
- [ ] New unit tests for <feature> pass
- [ ] Full test suite passes (no regressions)
- [ ] Code reviewed and simplified

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

### Step 10: Simplify and Review

After the PR is created, run these two skills in order:

1. **Simplify** — use the `simplify` skill (`/simplify`) to clean up and simplify the code changes.
2. **Review** — use the `review` skill (`/review`) to review the code. Fix all high-priority issues identified by the reviewer.

If either step produces fixes, commit them separately, re-run `mvn test` to confirm no regressions, and push to update the PR.

## Key Reminders

- **Write code, not plans.** If a plan exists in the issue, go straight to implementation.
- **Limit exploration.** Look at a few relevant files to understand patterns, then start coding. Do not spend excessive time reading every file in the codebase.
- **Spec is ground truth.** Trust the FHIRPath spec over your mental model.
- **Always consult Pathling before writing Spark code.** Pathling is a mature SparkSQL implementation of FHIRPath — its code generation patterns are battle-tested. Adapt its Spark expressions rather than inventing your own from scratch.
- **Existing tests are correct.** Do not modify existing tests unless the spec clearly contradicts them.
- **FHIRPath collections are one-dimensional.** No nested arrays, ever.
- **Ask when stuck, not when clear.** Only pause for user feedback on ambiguous spec requirements or architectural decisions that introduce new patterns.
