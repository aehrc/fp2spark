---
name: enable-compat-suite
description: >
  Enable blanket-skipped YAML compatibility test suites by removing skip-all exclusions, triaging
  failures, and creating properly classified targeted exclusions. Use this skill whenever the user
  asks to enable a YAML compat suite, triage compat test failures, remove blanket skips, or work
  on an issue that mentions enabling a config.yaml file (e.g., "enable 6.1_equality.yaml",
  "triage compat failures for X", "work on issue #150"). Also trigger when the user references
  enabling or un-skipping fhirpath-js test files.
---

# Enable YAML Compat Suite

This skill drives end-to-end enablement of blanket-skipped YAML compatibility test suites: from
removing skip-all exclusions through triage, classification, and a reviewed PR with filed issues.

The repo is `aehrc/fp2spark` on GitHub.

## Key References

- **Exclusion format and types**: [TESTING.md](TESTING.md) § "Exclusion format"
- **Specification divergences**: [SPEC_DIVERGENCES.md](SPEC_DIVERGENCES.md)
- **Config file**: `src/test/resources/fhirpath-js/config.yaml`
- **Test class**: `src/test/java/com/example/fhirpath/compat/yaml/YamlReferenceCompatTest.java`
- **Arbitrary subjects**: `ENABLED_ARBITRARY_SUBJECTS` in `YamlSubjectFactory.java`
- **Re-review companion skill**: `.claude/skills/review-compat-exclusions/SKILL.md` — use when
  auditing already-enabled content rather than enabling a blanket-skipped file.

## Exclusion hygiene (authoring rules)

Every rule added or modified in `config.yaml` must satisfy these invariants. The re-review
skill exists precisely because earlier runs of this skill produced rules that break them.

1. **Never use `type: wontfix`.** It is obsolete. Use one of `feature`, `bug`, `design`,
   `ref-impl-bug`, `test-infra`.
2. **Never carry forward Pathling issue ids.** `#2xxx` and `#437` live on `pathling/pathling`,
   not `aehrc/fp2spark`. If a Pathling rule is reused, either (a) file a fp2sql issue and
   replace the id, or (b) reclassify to `design`/`ref-impl-bug` citing a D/P/R entry.
3. **`type: design` requires a `D\d+` or `P\d+` token** (e.g. `D3`, `P2`) somewhere in the
   rule's `comment`. A D-entry is a spec divergence; a P-entry is a spec-permitted policy
   choice that differs from a reference implementation. The reviewer should grep the diff and
   fail any `type: design` lacking a D- or P-entry citation.
4. **`type: ref-impl-bug` requires an `R\d+` token** in the rule's `comment`.
5. **`type: feature|bug|test-infra` requires `id: "#NNN"`** pointing to `aehrc/fp2spark`.
   No blank ids. No Pathling ids.
6. **Prefer narrow matchers.** `any: ["<exact expression>"]` or `desc: [...]` is preferable to
   open `expression:` regex. Use `expression:` only when the pattern genuinely varies, and
   state why in the `comment`.
7. **Avoid singleton global rules.** A rule in the `*.yaml` global block that only fires on
   one file belongs in that file's `fp2sql — <file>` block. Global rules should cover
   genuinely cross-cutting concerns (unimplemented functions, unsupported env vars, design
   divergences that apply everywhere).
8. **Every slice has exactly one authoritative block.** When a rule covered by a per-file
   `fp2sql — <file>` block also appears in the global block or a legacy upstream Pathling
   block, consolidate into the per-file block and delete the duplicates.

## Workflow

Execute these steps sequentially. Do NOT stop to ask for feedback unless explicitly indicated.

### Step 1: Understand the Scope

Read the GitHub issue:

```bash
gh issue view <NUMBER> --repo aehrc/fp2spark
```

Identify which YAML files to enable and any expected blockers mentioned in the issue.

### Step 2: Create the Feature Branch

```bash
git checkout main && git pull
git checkout -b issue/<NUMBER>-<short-description>
```

### Step 3: Remove Blanket Skips

Delete the `fp2sql — skip entire <file>` entries from `config.yaml` for each file in scope.
These are entries with `any: [""]` that match every test case. Be careful not to break YAML
indentation of surrounding entries.

### Step 3b: Verify Global Rule Premises

Before trusting that "existing global rules will absorb the failures," verify that the
global rules the issue (or the suite) relies on are still accurate. Global rules in
`config.yaml` claim that a given function, variable, or capability is unimplemented — but
these claims can go stale as fp2sql evolves. A stale global rule silently skips tests that
would otherwise pass, inflating the skip count and hiding real coverage.

**When to verify:** Any time the GitHub issue predicates enablement on a named global
feature (e.g. *"blocked by unimplemented `extension()` function"*, *"absorbed by the
`%factory` global rule"*), or when a large fraction of a file's tests are skipped by a
single global rule.

**How to verify:** For each named feature the issue or global rule mentions, grep the
source for evidence it's implemented. For example:

```bash
# Is extension() actually unimplemented?
rg -n '\bextension\b' src/main/java/
# Is %factory actually unsupported?
rg -n '%factory|factory' src/main/java/
```

Cross-reference against `Analyzer.java` (for function rewrites), the codegen files (for
SparkSQL emission), and any resolver tables. If implementation exists, the global rule is
stale — remove the affected entry from the global list. Then re-run Step 5 and triage
whatever new failures surface.

**Symmetry:** When the issue lists multiple features as blockers, verify *all of them* —
not just the first one you happen to check. It's easy to verify one feature mid-triage
(because a failure prompted the check) and forget to apply the same scrutiny to the
others.

**Duplicate sweep:** When you remove or narrow a global rule, also grep `config.yaml` for
per-file duplicates of the same rule and delete them. Leaving a duplicate in a per-file
block silently re-enables the skip for one file and defeats the cleanup.

```bash
# Example: removing a global "Contained resources" rule
rg -n 'Contained|contained' src/test/resources/fhirpath-js/config.yaml
```

### Step 4: Enable Arbitrary Subjects

Check what `resourceType` each YAML test file uses:

```bash
python3 -c "
import yaml
with open('src/test/resources/fhirpath-js/cases/<FILE>.yaml') as f:
    data = yaml.safe_load(f)
subject = data.get('subject', {})
print('resourceType:', subject.get('resourceType', 'N/A'))
print('fields:', list(subject.keys())[:15])
"
```

If the subject uses a non-FHIR `resourceType` not already in `ENABLED_ARBITRARY_SUBJECTS`
(in `YamlSubjectFactory.java`), add it. Anonymous subjects (no `resourceType`) are already
handled.

### Step 5: Run Tests and Collect Failures

```bash
mvn test -Dtest=YamlReferenceCompatTest#<testMethod> -pl .
```

Collect all failures from the output. The assertion messages include the FHIRPath expression
in brackets (e.g., `[1 year > 12 months] Result size mismatch...`). For errors without
expression context, check the surefire XML report:

```bash
grep -B1 '<error\|<failure' target/surefire-reports/TEST-*.YamlReferenceCompatTest.xml \
  | grep 'testCase'
```

### Step 6: Classify Each Failure

For each failure, determine the correct exclusion type by investigating the root cause.

#### Classification process

1. **Check global rules first** — does an existing global exclusion (arbitrary subjects,
   unimplemented functions, equivalence operators, choice type polymorphism) already cover
   this test? If so, no new exclusion is needed — investigate why the global rule didn't
   match (e.g., the glob matching bug fixed in this project).

2. **Identify the error type:**
   - `CardinalityMismatchException` → likely arbitrary subject fields or empty-list bug (#156)
   - `OverloadResolutionException` → likely choice type polymorphism (D1) or missing overload
   - `AssertionFailedError` with wrong result → fp2sql bug or feature gap
   - `AssertionFailedError` with empty result → feature gap (empty propagation)
   - `ClassCastException` / Spark errors → fp2sql bug

3. **Consult the FHIRPath spec** using the `fhirpath-spec` skill for any failure where the
   correct behavior is unclear. The spec is ground truth. Pay special attention to:
   - Quantity equality/comparison rules (calendar vs definite durations)
   - Different-precision temporal comparison semantics
   - Empty collection propagation

4. **Check the fhirpath.js reference implementation** when the spec is ambiguous or when the
   test expectation seems wrong. The `fhirpath-spec` skill has access to `.local/fhirpath.js/src/`
   and will check it proactively.

5. **Assign a type** based on findings:

   | Type | When to use | Required reference format |
   |------|-------------|---------------------------|
   | `feature` | fp2sql doesn't implement this yet | `id: "#NNN"` on `aehrc/fp2spark` |
   | `bug` | fp2sql produces incorrect results | `id: "#NNN"` on `aehrc/fp2spark` |
   | `design` | Intentional fp2sql divergence, or a spec-permitted policy choice | `D\d+` or `P\d+` token in `comment` (traces to SPEC_DIVERGENCES.md) |
   | `ref-impl-bug` | fhirpath.js test expectation contradicts the spec | `R\d+` token in `comment` (traces to SPEC_DIVERGENCES.md) |
   | `test-infra` | Test infrastructure limitation | `id: "#NNN"` on `aehrc/fp2spark` |

   **Never use `wontfix`** — it is obsolete. If an existing `wontfix` exclusion falls within
   scope, reclassify it using the types above.

   **Never use Pathling ids** (`#2xxx`, `#437`). They point at `pathling/pathling`. File a
   fp2sql issue on `aehrc/fp2spark` or reclassify to `design`/`ref-impl-bug` with a D/P/R
   citation.

#### Handling SPEC_DIVERGENCES.md changes

If a failure should be classified as `design` or `ref-impl-bug`, **stop and propose the
SPEC_DIVERGENCES.md entry to the user**. Include:
- The proposed ID (D-next or R-next)
- The spec evidence (quote the relevant section)
- For `ref-impl-bug`: the fhirpath.js code that demonstrates the bug
- The affected expressions

**Wait for explicit user approval** before writing the entry.

### Step 7: Present Classification Summary for Approval

Before writing exclusions, present a summary table to the user for approval:

```
| # | Expression(s) | Type | Rationale | Issue/Ref |
|---|---------------|------|-----------|-----------|
| 1 | @2018 > @2017-01 | feature | Different-precision comparison | — |
| 2 | 1 year != 1 'a' | ref-impl-bug | Spec says empty, R1 | R1 |
| ...
```

**Wait for user approval** before proceeding. The user may reclassify entries or ask for
deeper investigation.

### Step 8: Write Targeted Exclusions

Add exclusions to `config.yaml` under the appropriate file-specific section. Use the full
classpath glob (e.g., `glob: "fhirpath-js/cases/6.1_equality.yaml"`).

Each exclusion must have:
- `title` — descriptive name
- `type` — from the approved classification
- `outcome` — `failure`, `error`, or omitted (skip)
- `comment` — brief explanation of why
- `id` — issue reference for `feature`, `bug`, and `test-infra` types
- Matcher (`any`, `expression`, `function`, or `desc`)

#### Pre-write checklist

Before saving the edit, confirm each new rule passes all six:

- [ ] `type` is one of `feature`, `bug`, `design`, `ref-impl-bug`, `test-infra`
      (never `wontfix`).
- [ ] `feature|bug|test-infra` has `id: "#NNN"` on `aehrc/fp2spark`
      (never `#2xxx` or `#437`).
- [ ] `design` has a `D\d+` or `P\d+` token in the `comment`; `ref-impl-bug` has `R\d+`.
- [ ] Matcher is as narrow as possible (`any` / `desc` preferred over `expression` regex).
- [ ] If adding to the global `*.yaml` block: the rule genuinely cross-cuts ≥2 files. If not,
      place it in the relevant `fp2sql — <file>` block instead.
- [ ] No duplicate of an existing in-scope rule (per-file block + global block + any
      surviving upstream Pathling block).

### Step 9: Reclassify In-Scope `wontfix`

Check for any `wontfix` exclusions in the file-specific sections being enabled. Reclassify
each one using the same classification process from Step 6. If a `wontfix` maps to `design`
or `ref-impl-bug`, follow the SPEC_DIVERGENCES.md approval process.

### Step 10: Run Full Test Suite

```bash
mvn test -pl .
```

All tests must pass with 0 failures and 0 errors. If failures remain, iterate on the
exclusions.

### Step 11: Format and Commit

```bash
mvn spotless:apply
git add <specific-files>
git commit -m "$(cat <<'EOF'
test: enable YAML compat suite: <files> (#<NUMBER>)

<summary of what was enabled and key exclusion categories>

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
EOF
)"
```

### Step 12: File Issues and Verify Traceability

For each `feature`, `bug`, and `test-infra` exclusion that doesn't already have a GitHub
issue, file one:

```bash
gh issue create --repo aehrc/fp2spark \
  --title "<descriptive title>" \
  --body "<description with affected expressions, spec reference, and link to #NUMBER>" \
  --label <type:bug|type:spec-gap|type:not-implemented> --label "compat:fhirpath-js"
```

Then update the exclusions in `config.yaml` with the new issue `id` values.

**Verify all exclusions have references.** Every exclusion in the file-specific sections
being enabled must trace to either:
- A GitHub issue via `id` (for `feature`, `bug`, `test-infra`)
- A D-entry or P-entry in SPEC_DIVERGENCES.md (for `design`)
- An R-entry in SPEC_DIVERGENCES.md (for `ref-impl-bug`)

If any exclusion is missing a reference, fix it before proceeding.

**Sweep neighbouring in-scope rules.** Don't stop at newly added rules — apply the same
traceability check to *all* rules in the `fp2sql — <file>` block for the file being
enabled and to any upstream Pathling block matching the same glob. If a neighbour has a
`wontfix`, a Pathling id, or a missing D/P/R citation, fix it in this PR. That is what
keeps the hygiene of the file monotonically improving instead of accumulating debt.

Quick diff-level checks before pushing:

```bash
# No wontfix remains in the slice
rg -n 'type: wontfix' src/test/resources/fhirpath-js/config.yaml

# No Pathling ids remain in the slice
rg -n 'id: "#(2[0-9]{3}|437)"' src/test/resources/fhirpath-js/config.yaml

# Every design rule cites a D- or P-entry
python3 - <<'PY'
import yaml, pathlib, re
cfg = yaml.safe_load(pathlib.Path('src/test/resources/fhirpath-js/config.yaml').read_text())
for block in cfg['excludeSet']:
    for r in block.get('exclude', []):
        if r.get('type') == 'design' and not re.search(r'\b[DP]\d+\b', r.get('comment') or ''):
            print('design w/o D-/P-entry:', r.get('title'))
        if r.get('type') == 'ref-impl-bug' and not re.search(r'R\d+', r.get('comment') or ''):
            print('ref-impl-bug w/o R-entry:', r.get('title'))
PY
```

**Present a summary of all issues created** to the user before moving on:

```
| Issue | Type | Title |
|-------|------|-------|
| #182  | bug  | type() and ofType() don't handle null collection elements |
| #184  | test-infra | Support expression map test runner feature |
| ...
```

Commit and push.

### Step 13: Push and Create PR

```bash
git push -u origin issue/<NUMBER>-<short-description>

gh pr create --repo aehrc/fp2spark --title "<short title>" --body "$(cat <<'EOF'
## Summary
- <which suites were enabled>
- <how many tests now running vs excluded>
- <key exclusion categories>

Closes #<NUMBER>

## Test plan
- [ ] Full test suite passes (no regressions)
- [ ] All exclusions properly classified
- [ ] Issues filed for all feature/bug/test-infra exclusions
- [ ] All exclusions have references (issue id, D-entry, P-entry, or R-entry)
- [ ] Code reviewed

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

### Step 14: Simplify

Use the `simplify` skill (`/simplify`) to clean up any code changes. Fix issues, commit,
and push.

Tell the user the PR is ready and suggest they run `/review` to review it.
**Wait for the user to confirm they are done with the review.**

### Step 15: Wait for CI and Merge

Once the user confirms the review is complete, wait for CI checks to pass:

```bash
gh pr checks <PR_NUMBER> --repo aehrc/fp2spark --watch
```

If checks pass, squash merge:

```bash
gh pr merge <PR_NUMBER> --repo aehrc/fp2spark --squash --delete-branch
git checkout main && git pull
```

If checks fail, report the failure details to the user and investigate.

## Key Reminders

- **Spec is ground truth.** Trust the FHIRPath spec over test expectations.
- **Always consult the spec** via `fhirpath-spec` skill before classifying ambiguous failures.
- **Never use `wontfix`.** Reclassify any in-scope `wontfix` exclusions.
- **Never carry forward Pathling ids.** `#2xxx` and `#437` point at `pathling/pathling`. File
  a fp2sql issue on `aehrc/fp2spark` or reclassify to `design`/`ref-impl-bug`.
- **SPEC_DIVERGENCES.md changes require approval.** Always propose and wait.
- **Classification summary requires approval.** Present the table and wait.
- **Every `feature`, `bug`, and `test-infra` needs a fp2sql issue.** File with
  `compat:fhirpath-js` label.
- **Every exclusion needs a reference.** Issue `id` for feature/bug/test-infra, `D\d+` or
  `P\d+` token in comment for design, `R\d+` token in comment for ref-impl-bug.
- **Global rules may already cover failures.** Check before adding redundant exclusions.
- **Global rules can be stale.** If the issue says enablement is "absorbed by the X global
  rule," verify X is still actually unimplemented by grepping the source. Apply the check
  symmetrically to every feature the issue names, not just the one that happens to come up
  during triage. When you remove a global rule, sweep the file for per-file duplicates and
  remove them too.
- **Sweep neighbours, not just new rules.** Before the PR, traceability checks apply to every
  rule in the `fp2sql — <file>` block for the file being enabled, not only newly added
  rules.
- **Refreshing from Pathling?** Never carry forward Pathling ids or `wontfix`. Run the
  `/review-compat-exclusions` skill on the affected slice instead.
