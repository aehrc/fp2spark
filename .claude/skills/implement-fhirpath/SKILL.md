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
  exclusion cleanup, simplification, code review, CI wait, and squash-merge back to main.
---

# Implement FHIRPath Feature

This skill drives end-to-end implementation of a FHIRPath feature: from reading the GitHub issue all the way through to a squash-merged PR on `main`. It produces working code, not plans.

The repo is `aehrc/fp2spark` on GitHub.

## Multi-Function Issues

When an issue covers multiple functions or operators, assess whether they can all be implemented in a single commit or whether splitting into multiple commits makes more sense (e.g., when functions have different complexity levels or touch different parts of the codebase).

If splitting, create a brief plan listing the commits, then execute Steps 3–8 for each commit before proceeding to Step 9 (push/PR) and the post-PR steps (compat, simplify, review, merge). All commits go on the same feature branch and into a single PR.

## Invocation

```
/implement-fhirpath <NUMBER> [--worktree] [--unattended]
```

- `--worktree` — run in an isolated git worktree at `../fp2sql-<NUMBER>` (created if needed).
  Use when several issues are being implemented concurrently.
- `--unattended` — no user is available to answer questions. Changes the behaviour of every
  approval gate and stops before merge. **Required** when this skill is run inside a
  dispatched subagent, which cannot ask the user anything.

## Workflow

Execute these steps sequentially. Do NOT stop to ask for feedback unless explicitly indicated — keep moving forward.

### Step 0: Resolve Isolation and Mode

Resolve both settings first and **print the result** so a misfire is visible in the transcript
rather than surfacing at Step 13.

#### 0a: Worktree

Detect whether this session is already in a linked worktree — a dispatched subagent may have
been placed in one by the harness, in which case do NOT create another:

```bash
[ "$(git rev-parse --git-dir)" != "$(git rev-parse --git-common-dir)" ] && echo "in worktree"
```

If `--worktree` was passed and we are *not* already in one, create it and move into it. Branch
off freshly fetched `origin/main`; this also satisfies Step 2, which you then skip:

```bash
git -C <repo> fetch origin
git -C <repo> worktree add ../fp2sql-<NUMBER> -b issue/<NUMBER>-<short-description> origin/main
cd ../fp2sql-<NUMBER>
```

If `git worktree add` fails because the branch already exists, stop and report it — the repo
accumulates stale local branches and silently reusing one would build on the wrong base.

Whenever this step ends up in a worktree (created here or pre-existing), recreate `.local/`
per the "Worktrees" section of CLAUDE.md, then **verify it resolves**:

```bash
readlink -f .local/pathling   # must print a real directory
```

If it does not resolve, stop. Step 4a mandates consulting Pathling before writing Spark code;
proceeding without it means inventing code generation from scratch, which is not acceptable.

#### 0b: Mode

| Step | Interactive (default) | `--unattended` |
|---|---|---|
| 4 design-approval gate | Present the change and wait for the user. | **Abort.** Report the proposed change, blast radius, and alternatives; do not write code. |
| 10 compat exclusions | Perform normally. | **Skip entirely.** `config.yaml` is a shared-edit hot spot; concurrent branches conflict. Note the skip in the PR body so a consolidated pass can follow. |
| 10c / 12.3 new D/R entry | Propose and wait. | **Abort.** Never edit `SPEC_DIVERGENCES.md`. |
| 12.3 surface-to-user finding | Ask the user (incl. FULL escalation offer on LITE). | Leave it unapplied and list it in the return value. |
| 13 merge | Full step: watch CI, squash-merge, return to `main`. | **Stop after Step 9.** Do not merge. Return the PR number and any aborted-gate report. |

"Abort" means: stop work, leave the branch and any commits in place, and return a report
naming the gate that fired and what decision is needed. Do not guess at a design, and do not
work around the gate.

Print the resolved mode before continuing, e.g.
`Mode: worktree=../fp2sql-272, unattended=true — will stop after Step 9.`

### Step 1: Understand the Scope

Read the GitHub issue to understand what needs to be built:

```bash
gh issue view <NUMBER> --repo aehrc/fp2spark
```

The issue typically contains:
- **Scope**: which functions/operators to implement
- **Design suggestions**: treat these as guidance, not requirements — you may deviate if you find a better approach during implementation

Note any acceptance criteria or test expectations mentioned in the issue.

### Step 2: Create the Feature Branch

**Skip this step if Step 0a already created the worktree and its branch.**

Follow CONTRIBUTING.md conventions exactly:

```bash
git fetch origin
git switch -c issue/<NUMBER>-<short-description> origin/main
```

Branch naming format: `issue/<number>-<kebab-case-description>` (e.g., `issue/42-string-functions`).

Branch off `origin/main` rather than checking out local `main` first: it guarantees a fresh
base, and it works inside a linked worktree, where `git checkout main` fails outright because
`main` is checked out in the primary worktree.

If the branch name already exists locally, stop and report it rather than reusing it.

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

Under `--unattended` (Step 0b) there is no user to approve: **abort** and return the same three
points as a report. Do not pick a design and proceed.

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
gh pr create --repo aehrc/fp2spark --title "<short title>" --body "$(cat <<'EOF'
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

**Skip this entire step under `--unattended` (Step 0b)** — record in the PR body that compat
cleanup is outstanding, and proceed to Step 11.

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

### Step 11: Simplify

Run a simplification pass first — invoke the `simplify` skill (i.e. call `Skill` with `skill: "simplify"`). It reviews changed code for reuse, quality, and efficiency and applies the fixes in place. Doing this before code review keeps the diff lean so the reviewer focuses on substantive issues rather than soon-to-be-deleted code. After it returns, run the full test suite to confirm no regressions, format, and commit:

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

If simplification produces nothing, skip the commit and proceed.

### Step 12: Code Review

Step 12 is **adaptive**: classify the PR as **LITE** or **FULL**, dispatch the matching review path, then run the shared triage in Step 12.3. Both paths return findings inline — no PR comment is posted.

#### Step 12.0 — Classify

The PR is **LITE** by default. It is **FULL** if any of the signals below holds.

**Workflow-state signals** (from earlier steps in this skill run):

1. **Step 4 design-approval gate fired** — a framework-extending change was made.
2. **Step 10 proposed a new D/R entry** in `SPEC_DIVERGENCES.md`.
3. **Multi-function issue with non-trivial design choices** in any commit. Judgment-based — no strict line-count or commit-count threshold; assess whether the multi-function work involved framework-level decisions or just parallel boilerplate.

**Path-fallback signals.** Compute the range:

```bash
BASE_SHA=$(git merge-base origin/main HEAD)
HEAD_SHA=$(git rev-parse HEAD)
```

Then check:

4. Any change touched **outside this safe-set** (`git diff --name-only $BASE_SHA..$HEAD_SHA`):
   - `src/main/java/.../codegen/spark/ops/**`
   - `src/main/java/.../analyzer/OperationRegistry.java` — only when the change is purely a new entry registration. Any modification to the registration API itself, helper methods, or the registry's structural definition routes to FULL.
   - `src/test/java/com/example/fhirpath/**` — capability tests, including additions to existing classes. EXCLUDES `src/test/java/com/example/fhirpath/test/**` (test infrastructure shared by every test) and `src/test/java/com/example/fhirpath/compat/**` (Pathling compat mirrors that must not drift).
   - `src/test/resources/fhirpath-js/config.yaml` (compat cleanup)
5. **Diff > 600 lines added** (`git diff --stat $BASE_SHA..$HEAD_SHA` total insertions).

If none fire → proceed to Step 12.1 (LITE). Otherwise → Step 12.2 (FULL). Either way, triage via Step 12.3.

#### Step 12.1 — Lite path

`Task`-dispatch a `general-purpose` subagent on `model: sonnet` with this prompt (substitute the PR number from `gh pr view`):

```
You are reviewing PR #<NUMBER> in aehrc/fp2spark.

Invoke: Skill(skill="review", args="<NUMBER>")

That injects a code-review template; follow it. The template will run
gh pr view, gh pr diff, and produce a structured review.

After the template's review, classify each finding by severity
(Critical / Important / Minor) and return ONLY:
- Critical / Important / Minor lists, each with file:line citations
- An Assessment line: "Ready to merge: Yes/No"

Keep the report under 400 words.
```

The subagent's return value is the report. Pass it to Step 12.3 for triage. No PR comment is posted.

#### Step 12.2 — Full path

Invoke the `superpowers:requesting-code-review` skill (i.e. call `Skill` with `skill: "superpowers:requesting-code-review"`). The skill dispatches a `superpowers:code-reviewer` subagent that reviews a specific git range and returns findings categorized by severity (**Critical** / **Important** / **Minor**) directly in this conversation — no PR comment is posted.

Compute the range to review — everything this PR has added on top of `main`, including the simplification commit from Step 11:

```bash
BASE_SHA=$(git merge-base origin/main HEAD)
HEAD_SHA=$(git rev-parse HEAD)
```

When the skill prompts for its template fields, fill them as follows:

- `WHAT_WAS_IMPLEMENTED`: one or two sentences naming the FHIRPath function/operator/capability and the layers touched (analyzer / IR / codegen / tests).
- `PLAN_OR_REQUIREMENTS`: `Issue #<NUMBER>` plus the relevant FHIRPath spec section(s). If a Pathling reference shaped the design, mention that too.
- `BASE_SHA`, `HEAD_SHA`: from above.
- `DESCRIPTION`: a tight one-liner — the reviewer reads this first to set context.

The returned report has Strengths, Issues (Critical / Important / Minor), Recommendations, and an Assessment verdict. Pass it to Step 12.3 for triage.

#### Step 12.3 — Triage

Apply the same triage protocol to whichever path produced the report:

- **Apply automatically** — Critical and Important findings that are clear-cut: bugs, correctness issues, missing test cases for behavior the implementation already claims to support, dead code, hygiene violations, obvious naming/typing fixes. Default to fixing rather than re-litigating.
- **Surface to the user** — anything that would change the public API, alter spec semantics, expand scope beyond the issue, require a new D/R entry in `SPEC_DIVERGENCES.md`, or modify/extend the existing framework. Do not silently apply these. The same `SPEC_DIVERGENCES` and design-extension guardrails from Steps 4 and 10 apply here.
  - **On the LITE path only:** when surfacing such a finding, *also* ask the user whether they want a **FULL review enforced** before deciding. A finding that elevates to user judgment is a signal the PR isn't actually trivial; FULL may catch related design-level issues LITE missed.
    - If the user opts to escalate → run Step 12.2 (FULL), merge both reports, re-triage, and present the combined surface-to-user set. Only then proceed with the user's decision.
    - If the user declines escalation → proceed with the standard triage protocol.
  - On the FULL path, no escalation prompt — FULL already ran.
  - Under `--unattended` (Step 0b): leave these findings unapplied and list them in the return
    value. No escalation prompt on either path.
- **Defer / decline** — Minor findings that conflict with established patterns elsewhere in the codebase, or any finding that does not survive a closer read of the cited code. Note them briefly but do not act. The reviewer is not infallible — push back with technical reasoning rather than mechanically applying every suggestion.

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

If the reviewer's verdict is "Ready to merge: Yes" with no actionable Critical/Important findings, skip the commit and proceed.

### Step 13: Wait for CI, Merge, Return to main

**If `--unattended` (Step 0b): stop here.** Do not watch CI and do not merge. Return the PR
number, the LITE/FULL classification, whether Step 10 was skipped, and any surface-to-user
findings left unapplied. Merging is the dispatcher's job — concurrent squash-merges of branches
that all touch `OperationRegistry` and `config.yaml` produce conflicts and CI churn.

Watch CI to completion:

```bash
gh pr checks <PR> --repo aehrc/fp2spark --watch
```

**On green**, squash-merge (matches the project's merge strategy — every PR lands as a single commit on `main`):

```bash
gh pr merge <PR> --repo aehrc/fp2spark --squash --delete-branch
```

Then return to `main`. In the primary worktree:

```bash
git switch main && git pull
```

In a linked worktree (Step 0a), `git switch main` fails — `main` is checked out in the primary
worktree, and `--delete-branch` cannot delete a branch that is still checked out here. Tear the
worktree down from the primary instead:

```bash
cd <primary-worktree>
git worktree remove ../fp2sql-<NUMBER>
git switch main && git pull
git branch -d issue/<NUMBER>-<short-description>   # local branch, if it survived
```

**On red**, do NOT bypass: no `--no-verify`, no force-push, no skipped checks. Investigate the failing job (`gh run view <run-id> --log-failed`), reproduce locally if possible, fix the root cause, push the fix, and re-watch CI. If the failure is environmental and unrelated to the change, surface it to the user before retrying.

Confirm the local `main` matches the merged state and the feature branch is gone:

```bash
git log --oneline -1   # should show the squashed commit
git branch             # feature branch should no longer exist locally
```

## Key Reminders

- **Resolve mode first (Step 0) and print it.** `--worktree` isolates the run; `--unattended`
  turns every approval gate into an abort and stops before merge. A subagent running this skill
  is always `--unattended` — it has no user to ask.
- **In a worktree, `.local/` must be recreated and verified** (Step 0a, and the "Worktrees"
  section of CLAUDE.md). Without it the mandatory Pathling consultation fails open.
- **Write code, not plans.** If a plan exists in the issue, go straight to implementation.
- **Limit exploration.** Look at a few relevant files to understand patterns, then start coding. Do not spend excessive time reading every file in the codebase.
- **Spec is ground truth.** Trust the FHIRPath spec over your mental model.
- **Always consult Pathling before writing Spark code.** Pathling is a mature SparkSQL implementation of FHIRPath — its code generation patterns are battle-tested. Adapt its Spark expressions rather than inventing your own from scratch.
- **Existing tests are correct.** Do not modify existing tests unless the spec clearly contradicts them.
- **FHIRPath collections are one-dimensional.** No nested arrays, ever.
- **Design approval gate (Step 4).** Modifying or extending the framework — new IR nodes, new type system features, new code generation patterns, changes to the analyzer/registry shape — requires explicit user approval before coding. Slotting into the existing patterns does not.
- **Compat exclusion hygiene (Step 10).** Never `wontfix`. Never carry Pathling ids. Every `feature|bug|test-infra` rule has a fp2sql `id`. Every `design` cites a D-entry; every `ref-impl-bug` cites an R-entry.
- **`SPEC_DIVERGENCES.md` requires explicit approval.** Whether the trigger is a new compat exclusion (Step 10) or a review-driven divergence (Step 12), propose the D/R entry to the user and wait. Never edit `SPEC_DIVERGENCES.md` autonomously.
- **Adaptive code review (Step 12).** Step 12.0 classifies the PR as LITE (Sonnet subagent invoking `/review` on the PR) or FULL (`superpowers:requesting-code-review`); routing is by Step 4 / Step 10 workflow state plus a path/size fallback. Both paths feed the shared triage in Step 12.3, which on LITE additionally offers the user a FULL escalation when surfacing a finding that needs their judgment. Apply clear-cut Critical/Important fixes; push back on Minor findings that conflict with established patterns.
- **Squash-merge only.** Match the project's merge strategy — one commit per PR on `main`.
- **CI must be green to merge.** No bypasses. Investigate root cause on red.
- **Ask when stuck, not when clear.** Pause for user feedback on ambiguous spec requirements, architectural changes, or `SPEC_DIVERGENCES` entries — not on routine implementation choices.
