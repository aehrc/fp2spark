---
name: implement-fhirpath
description: >
  End-to-end implementation of a FHIRPath feature from GitHub issue to a merged PR. Use this skill whenever
  the user asks to implement a FHIRPath function, operator, or capability referenced by a GitHub issue number.
  Trigger on phrases like "implement issue #X", "add FHIRPath function X", "implement X operator",
  "build feature for issue #X", or any request to implement a FHIRPath feature end-to-end. Also trigger
  when the user provides an issue number and expects implementation work, even if they don't say "implement"
  explicitly (e.g., "let's do #42", "work on issue 15", "pick up #28"). Covers the full lifecycle:
  spec research, design (with approval gates for framework changes), implementation, tests, compat
  exclusion cleanup, code review, simplification, CI wait, and squash-merge back to main.
---

# Implement FHIRPath Feature

This skill drives end-to-end implementation of a FHIRPath feature: from reading the GitHub issue all the way through to a squash-merged PR on `main`. It produces working code, not plans.

The repo is `piotrszul/fp2spark` on GitHub.

## Multi-Function Issues

When an issue covers multiple functions or operators, assess whether they can all be implemented in a single commit or whether splitting into multiple commits makes more sense (e.g., when functions have different complexity levels or touch different parts of the codebase).

If splitting, create a brief plan listing the commits, then execute Steps 3–8 for each commit before proceeding to Step 9 (push/PR) and the post-PR steps (compat, review, simplify, merge). All commits go on the same feature branch and into a single PR.

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

**Design approval gate.** If implementing the feature would require modifying or extending the existing framework — e.g., new IR node types, changes to the analyzer/operation registry shape, new type system features, new code generation patterns, new traversal semantics, or any change that other features will inherit — **stop and ask the user for explicit design approval before writing code**. Present:

- The architectural change you are proposing and why a within-framework solution does not work.
- The blast radius (which layers change, which existing code is affected).
- Alternative approaches you considered and why you rejected them.

Wait for explicit approval. Slotting a new operation into the existing registry/codegen pattern does *not* require this gate — it is reserved for changes that alter the framework itself.

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

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
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

### Step 10: Update fhirpath-js Compat Exclusions

The new feature usually unblocks tests in `src/test/resources/fhirpath-js/config.yaml` that were previously excluded as `feature` (unimplemented). Clean those up now so the compat suite reflects the post-implementation reality. Reuse the hygiene rules from the `review-compat-exclusions` skill — this step is a scoped subset of that workflow, restricted to rules touching the implemented feature.

#### 10a: Find rules in scope

Search for any exclusion rule that references the feature being implemented — by function name, operator, expression substring, or by referenced GitHub issue. For multi-function issues, do this for each function. Capture the parent block, rule title, type, id, and matcher.

```bash
# By function or operator name
rg -n '<function-name>\(' src/test/resources/fhirpath-js/config.yaml
rg -n '<function-name>' src/test/resources/fhirpath-js/config.yaml

# By the issue number this PR closes
rg -n '#<NUMBER>' src/test/resources/fhirpath-js/config.yaml
```

#### 10b: Decide per rule

For each in-scope rule, pick exactly one decision:

| Decision | When |
|---|---|
| **REMOVE** | The new implementation makes the rule's cases pass. Verify by running the suite (10d) after deletion. |
| **NARROW** | The matcher is over-broad — some cases now pass, others still fail for legitimate reasons. Rewrite the matcher (prefer `any: ["exact expression"]`) so it only catches the residual failures. |
| **RECLASSIFY** | The remaining failure is no longer a `feature` gap — it is a design divergence (`design`), a fhirpath.js bug (`ref-impl-bug`), a test infrastructure limit (`test-infra`), or a real fp2sql bug (`bug`). Rewrite `type`/`id`/`comment` accordingly. |
| **KEEP** | Rule still describes a real, unaddressed gap unrelated to this PR. No change. |

**Never** use `type: wontfix` and **never** carry forward Pathling ids (`#2\d{3}` or `#437`). If a rule needs to stay but currently has a stale type or Pathling id, treat it as RECLASSIFY/RE-ID and bring it into compliance — even if it pre-dates this PR.

#### 10c: Hygiene for any new or modified rule

A rule remaining in scope must satisfy:

- `feature` / `bug` / `test-infra` → `id: "#NNN"` pointing at a fp2sql issue.
- `design` → `comment` cites a `D\d+` token from `SPEC_DIVERGENCES.md`.
- `ref-impl-bug` → `comment` cites an `R\d+` token from `SPEC_DIVERGENCES.md`.
- Matcher actually matches at least one failing case in the YAML test file.
- No two in-scope rules match the same set of cases.

If a residual failure needs to land on `design` or `ref-impl-bug` and there is no existing D/R entry that fits, **stop and propose a new D/R entry to the user**: proposed id, spec evidence (quote the section), for `ref-impl-bug` the fhirpath.js code that demonstrates the bug, and the affected expressions. **Wait for explicit user approval before editing `SPEC_DIVERGENCES.md`** — the same guardrail applies as in the `review-compat-exclusions` skill.

If a residual failure suggests a real fp2sql bug rather than a divergence, file (or reference) a fp2sql issue and use `type: bug` with that id.

#### 10d: Verify and commit

```bash
mvn test -Dtest=YamlReferenceCompatTest
```

Must be 0 failures, 0 errors. If a REMOVE causes a new failure, fall back to NARROW or KEEP for that rule and document why in the commit body.

```bash
git add src/test/resources/fhirpath-js/config.yaml SPEC_DIVERGENCES.md  # SPEC only if user approved an entry
git commit -m "$(cat <<'EOF'
test: refresh compat exclusions for #<NUMBER>

<one-paragraph summary: X removed, Y narrowed, Z reclassified, plus any new D/R entries>

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
git push
```

### Step 11: Code Review

Invoke the `pr-review-toolkit:review-pr` skill (i.e. call `Skill` with `skill: "pr-review-toolkit:review-pr"`, default aspects). Do NOT use the built-in `/review` command — the toolkit orchestrates multiple specialized agents (code-reviewer, pr-test-analyzer, comment-analyzer, silent-failure-hunter, type-design-analyzer) and produces a richer report.

Triage the findings:

- **Apply automatically** — bugs, correctness issues, missing test cases for behavior the implementation already claims to support, dead code, hygiene violations, obvious naming/typing fixes, and any other clear-cut recommendations the reviewer marks as critical or important and that have an unambiguous fix.
- **Surface to the user** — anything that would change the public API, alter spec semantics, expand scope beyond the issue, require a new D/R entry in `SPEC_DIVERGENCES.md`, or modify/extend the existing framework. Do not silently apply these. The same `SPEC_DIVERGENCES` and design-extension guardrails from Steps 4 and 10 apply here.
- **Defer / decline** — purely stylistic suggestions, speculative refactors, or recommendations that conflict with established patterns elsewhere in the codebase. Note them briefly but do not act.

After applying fixes:

```bash
mvn spotless:apply
mvn test           # full suite, no regressions
git add <files>
git commit -m "$(cat <<'EOF'
fix(#<NUMBER>): address review feedback

<short bullet list of what changed and why>

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
git push
```

If the reviewer surfaces nothing actionable, skip the commit and proceed.

### Step 12: Simplify

Once the review pass is clean, run a simplification pass — invoke the `pr-review-toolkit:review-pr` skill again with the `simplify` aspect (`skill: "pr-review-toolkit:review-pr"`, args: `"simplify"`), or fall back to the local `simplify` skill. Apply the simplifications, run the full test suite to confirm no regressions, format, and commit:

```bash
mvn spotless:apply
mvn test
git add <files>
git commit -m "$(cat <<'EOF'
refactor(#<NUMBER>): simplify per code-simplifier

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
git push
```

If simplification produces nothing, skip the commit.

### Step 13: Wait for CI, Merge, Return to main

Watch CI to completion:

```bash
gh pr checks <PR> --repo piotrszul/fp2spark --watch
```

**On green**, squash-merge (matches the project's merge strategy — every PR lands as a single commit on `main`):

```bash
gh pr merge <PR> --repo piotrszul/fp2spark --squash --delete-branch
git checkout main && git pull
```

**On red**, do NOT bypass: no `--no-verify`, no force-push, no skipped checks. Investigate the failing job (`gh run view <run-id> --log-failed`), reproduce locally if possible, fix the root cause, push the fix, and re-watch CI. If the failure is environmental and unrelated to the change, surface it to the user before retrying.

Confirm the local `main` matches the merged state and the feature branch is gone:

```bash
git log --oneline -1   # should show the squashed commit
git branch             # feature branch should no longer exist locally
```

## Key Reminders

- **Write code, not plans.** If a plan exists in the issue, go straight to implementation.
- **Limit exploration.** Look at a few relevant files to understand patterns, then start coding. Do not spend excessive time reading every file in the codebase.
- **Spec is ground truth.** Trust the FHIRPath spec over your mental model.
- **Always consult Pathling before writing Spark code.** Pathling is a mature SparkSQL implementation of FHIRPath — its code generation patterns are battle-tested. Adapt its Spark expressions rather than inventing your own from scratch.
- **Existing tests are correct.** Do not modify existing tests unless the spec clearly contradicts them.
- **FHIRPath collections are one-dimensional.** No nested arrays, ever.
- **Design approval gate (Step 4).** Modifying or extending the framework — new IR nodes, new type system features, new code generation patterns, changes to the analyzer/registry shape — requires explicit user approval before coding. Slotting into the existing patterns does not.
- **Compat exclusion hygiene (Step 10).** Never `wontfix`. Never carry Pathling ids. Every `feature|bug|test-infra` rule has a fp2sql `id`. Every `design` cites a D-entry; every `ref-impl-bug` cites an R-entry.
- **`SPEC_DIVERGENCES.md` requires explicit approval.** Whether the trigger is a new compat exclusion (Step 10) or a review-driven divergence (Step 11), propose the D/R entry to the user and wait. Never edit `SPEC_DIVERGENCES.md` autonomously.
- **Use `pr-review-toolkit:review-pr`, not `/review`.** Apply clear-cut fixes; surface anything that touches the framework, public API, or spec semantics.
- **Squash-merge only.** Match the project's merge strategy — one commit per PR on `main`.
- **CI must be green to merge.** No bypasses. Investigate root cause on red.
- **Ask when stuck, not when clear.** Pause for user feedback on ambiguous spec requirements, architectural changes, or `SPEC_DIVERGENCES` entries — not on routine implementation choices.
