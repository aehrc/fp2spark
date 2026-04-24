---
name: review-compat-exclusions
description: >
  Audit and clean up exclusions in the fhirpath-js YAML compatibility test config, one file at
  a time. Use this skill when the user asks to re-review, audit, or clean up exclusions in
  `src/test/resources/fhirpath-js/config.yaml` — for example "re-review exclusions for
  6.1_equality", "audit config.yaml", "clean up wontfix entries", "consolidate #437", "replace
  Pathling ids with fp2sql issues". Also trigger when the user references tightening
  classifications, removing stale global rules, or reconciling upstream Pathling blocks with the
  fp2sql hygiene rules. Scope is always one YAML test file (or the global `*.yaml` block treated
  as its own unit); cross-file work is a composition of single-file runs.
---

# Review Compat Exclusions

End-to-end audit of one slice of exclusions in
`src/test/resources/fhirpath-js/config.yaml`: inventory every rule that applies to the chosen
slice, verify it still matches and still describes reality, reclassify or remove as needed, and
leave behind a file-specific block that satisfies the hygiene rules.

Complement skill: `enable-compat-suite` handles enabling a currently blanket-skipped file. This
skill handles re-reviewing already-enabled content.

The repo is `piotrszul/fp2spark` on GitHub.

## Key References

- **Exclusion hygiene rules**: `.claude/skills/enable-compat-suite/SKILL.md` § "Exclusion hygiene"
- **Exclusion format and types**: [TESTING.md](../../../TESTING.md) § "Exclusion format"
- **Specification divergences**: [SPEC_DIVERGENCES.md](../../../SPEC_DIVERGENCES.md)
- **Config file**: `src/test/resources/fhirpath-js/config.yaml`
- **Matcher semantics (authoritative)**: `src/test/java/com/example/fhirpath/compat/yaml/format/ExcludeRule.java`
- **Test class**: `src/test/java/com/example/fhirpath/compat/yaml/YamlReferenceCompatTest.java`

## Scope

Pick one slice before starting. A slice is always one of:

- **A single test file** — e.g. `6.1_equality.yaml`. Covers the `fp2sql — <file>` block plus any
  upstream Pathling block globbing the same file, plus any global `*.yaml` rules that match.
- **The global `*.yaml` block** — treat as a virtual unit when the goal is to clean global
  rules independently of per-file work. Sub-rules are processed one at a time.

Do not attempt two slices in the same PR. Cross-file dedup (rules in the global block that also
appear per-file) is resolved during the owning slice's PR.

## Workflow

Execute sequentially. Do NOT stop to ask for feedback unless a step says so.

### Step 1: Understand the scope

Identify the slice and the relevant blocks. Example for `6.1_equality.yaml`:

```bash
# Locate all blocks matching this file
rg -n 'glob:.*6\.1_equality' src/test/resources/fhirpath-js/config.yaml

# List all rule titles matching the file via its glob (incl. *.yaml rules)
python3 - <<'PY'
import yaml, pathlib, fnmatch
cfg = yaml.safe_load(pathlib.Path('src/test/resources/fhirpath-js/config.yaml').read_text())
for block in cfg['excludeSet']:
    if fnmatch.fnmatch('fhirpath-js/cases/6.1_equality.yaml', block.get('glob') or '*') \
       or fnmatch.fnmatch('6.1_equality.yaml', block.get('glob') or '*'):
        print('BLOCK', block.get('title'))
        for r in block.get('exclude', []):
            print('  -', r.get('title'), '|', r.get('type'), '|', r.get('id', ''))
PY
```

If the slice is the global `*.yaml` block, list every sub-rule and pick *one* sub-rule per
worksheet unit.

### Step 2: Create the feature branch

```bash
git checkout main && git pull
git checkout -b review/<slice-name>
```

Use hyphens; e.g. `review/6.1-equality`, `review/global-functions-list`.

### Step 3: Inventory

Build a worksheet with one row per `ExcludeRule` in scope. Capture for each row:

| Column | Meaning |
|---|---|
| `block_title` | The parent `excludeSet[*].title` |
| `glob` | The parent glob |
| `rule_title` | The rule's `title` |
| `type` | Current `type` |
| `id` | Current `id` (if any) |
| `outcome` | Skip / error / failure / pass |
| `matcher_kind` | `any` / `desc` / `expression` / `function` |
| `matcher_values` | The raw values |
| `line_range` | Line numbers in `config.yaml` |

Pre-compute hygiene flags on each row:

- **`STALE_TYPE`** — `type: wontfix`
- **`STALE_ID`** — `id` matches `#2\d{3}` or is literally `#437` (Pathling)
- **`MISSING_REF`** — type ∈ {`feature`, `bug`, `test-infra`} and `id` is empty
- **`MISSING_D_R`** — type ∈ {`design`, `ref-impl-bug`} and `comment` lacks a `D\d+`/`R\d+` token
- **`DUPLICATE`** — rule title (or matcher values) already present in another in-scope rule

### Step 4: Verify match coverage

For each row, confirm the rule still matches at least one test case in the slice. Use the same
matcher semantics as `ExcludeRule.java` (OR across matcher kinds):

- `any` / `desc` — substring match, scanning expression text and/or the case `desc`.
- `expression` — `Pattern.find()` (regex partial match, not `matches()`).
- `function` — substring `<name>(` in the expression text.
- `spel` — silently ignored at runtime; treat as always-not-matching.

Sketch:

```bash
# For 'any' / 'desc' substrings
rg -F -n 'substring-value' src/test/resources/fhirpath-js/cases/<file>.yaml

# For 'expression' regex (use python's re.search to mirror Pattern.find())
python3 - <<'PY'
import re, yaml, pathlib
data = yaml.safe_load(pathlib.Path('src/test/resources/fhirpath-js/cases/<file>.yaml').read_text())
pat = re.compile(r'<regex>')
for t in data.get('tests', []):
    expr = t.get('expression') or ''
    if pat.search(expr):
        print(expr)
PY

# For 'function'
rg -F -n '<name>(' src/test/resources/fhirpath-js/cases/<file>.yaml
```

Record `matches=N/M` per row. A rule with **`matches=0`** in its in-scope slice is dead and
must be either removed or narrowed (if it was intentionally global).

### Step 5: Verify premise

Separate from match coverage: is the reason still true?

- **`feature`** — grep `src/main/java/` for the function/operator to confirm it is still
  unimplemented. Cross-reference `Analyzer.java`, the codegen/resolver tables.
- **`bug`** — `gh issue view <id> --repo piotrszul/fp2spark` must show the issue open.
- **`design`** — the cited D-entry in `SPEC_DIVERGENCES.md` still describes the current
  behaviour.
- **`ref-impl-bug`** — the cited R-entry still describes the current fhirpath.js bug.
- **`test-infra`** — the infrastructure limitation still exists.

If the premise is falsified, the rule is stale (same outcome as zero matches: remove or
rewrite).

### Step 6: Decide

Pick exactly one decision per row:

| Decision | When | Follow-up |
|---|---|---|
| **REMOVE** | `matches=0`, or premise falsified | Delete the rule |
| **NARROW** | Matcher over-matches (hits cases that now pass) | Rewrite matcher to the specific failing expressions |
| **RECLASSIFY** | `STALE_TYPE`, or `#437` → D3, or Pathling reason maps to a D/R entry | Change `type`; if landing on `design`/`ref-impl-bug` without an existing D/R entry, trigger the SPEC_DIVERGENCES approval gate |
| **RE-ID** | `STALE_ID` (`#2xxx`), or `MISSING_REF` | File a fp2sql issue (see Step 8) and update `id` |
| **MERGE** | `DUPLICATE` of another in-scope rule | Delete the duplicate; keep the canonical rule (usually in the `fp2sql — <file>` block) |
| **KEEP** | Still correct, already referenced | No change |

Specific patterns expected during the current re-review:

- **Any `#437`** → `type: design`, `comment: "D3. …"`, no `id` needed.
- **Any `type: wontfix`** → reclassify; never carry forward.
- **Pathling `#2xxx`** → either reclassify to D/R (if it is a design divergence) or file a
  fp2sql issue and RE-ID.
- **Upstream Pathling block for the slice file** → after per-row decisions, fold surviving
  rules into the `fp2sql — <file>` block and delete the upstream block so each file has a
  single authoritative block.

### Step 7: Present approval table

Render one table to the user and wait for approval:

```
| # | Rule title | Current type/id | matches | Decision | New type/id | Issue to file | D/R |
|---|------------|-----------------|---------|----------|-------------|---------------|-----|
| 1 | …          | wontfix         | 3/12    | RECLASSIFY | feature #NEW | "feat: …"     | —   |
| 2 | …          | feature #437    | 4/12    | RECLASSIFY | design       | —             | D3  |
…
```

Block on user response. The user may correct classifications, ask for deeper investigation, or
push for more aggressive consolidation.

### Step 8: File fp2sql issues

For every RE-ID / MISSING_REF row, file an issue on `piotrszul/fp2spark`:

```bash
gh issue create --repo piotrszul/fp2spark \
  --title "<descriptive title>" \
  --body "<affected expressions, spec reference, link to the re-review PR once created>" \
  --label <type:bug|type:spec-gap|type:not-implemented> --label "compat:fhirpath-js"
```

Prefer **umbrella issues** when a cluster of rules share a root cause (e.g., "unsupported
FHIRPath environment variables beyond %context"). A single issue with a list of expressions is
more tractable than 10 separate tickets.

Record the new issue numbers in the approval table.

### Step 9: Handle SPEC_DIVERGENCES.md changes

If any RECLASSIFY lands on `design` or `ref-impl-bug` *and* there is no existing D/R entry that
fits, **stop and propose the new entry to the user**:

- Proposed id (`D<next>` or `R<next>`)
- Spec evidence (quote the relevant section)
- For `ref-impl-bug`: the fhirpath.js code that demonstrates the bug (use the
  `fhirpath-spec` skill to access `.local/fhirpath.js/src/`)
- Affected expressions

**Wait for explicit user approval** before editing `SPEC_DIVERGENCES.md`.

### Step 10: Edit config.yaml

Apply decisions as a single atomic edit (or a small sequence of semantic commits within the
slice, e.g. "reclassify wontfix", "consolidate #437 → D3", "re-id Pathling #2xxx"). After
folding the upstream Pathling block into `fp2sql — <file>`:

- The resulting `fp2sql — <file>` block should be the single authoritative block for that
  file.
- Delete the emptied upstream Pathling block.
- If any global `*.yaml` duplicate was merged into the per-file block, remove the duplicate
  from the global block.

Rules that must hold after the edit (see `enable-compat-suite/SKILL.md` § "Exclusion hygiene"
for the authoritative list):

- No `type: wontfix` remains in scope.
- No Pathling ids (`#2\d{3}` or `#437`) remain in scope.
- Every `feature|bug|test-infra` rule in scope has `id: "#NNN"`.
- Every `design` rule cites a `D\d+` token in `comment`.
- Every `ref-impl-bug` rule cites an `R\d+` token in `comment`.
- No two in-scope rules match exactly the same set of cases.

### Step 11: Run the test suite

```bash
mvn test -Dtest=YamlReferenceCompatTest
```

Must be 0 failures, 0 errors. If removals cause new failures, fall back to KEEP for those rows
and document in the commit.

### Step 12: Format and commit

```bash
mvn spotless:apply  # YAML-only changes don't strictly need this, but run anyway for safety
git add src/test/resources/fhirpath-js/config.yaml
git commit -m "$(cat <<'EOF'
test: re-review compat exclusions for <slice>

<one-paragraph summary of decisions: X removed, Y reclassified, Z re-ided, W merged>

<list of new issues filed with their numbers and titles>

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

### Step 13: Verify traceability

Before opening the PR, grep the diff for hygiene violations:

```bash
# Any remaining Pathling ids in changed regions?
git diff main -- src/test/resources/fhirpath-js/config.yaml | grep -E '^\+.*id:.*"#(2[0-9]{3}|437)"' && echo "FAIL"

# Any remaining wontfix?
git diff main -- src/test/resources/fhirpath-js/config.yaml | grep -E '^\+.*type: wontfix' && echo "FAIL"

# Every new design rule cites a D-entry?
awk '/type: design/{getline; if ($0 !~ /D[0-9]+/) print NR": "$0}' src/test/resources/fhirpath-js/config.yaml
```

Any violation: fix before pushing.

### Step 14: Push and open PR

```bash
git push -u origin review/<slice-name>

gh pr create --repo piotrszul/fp2spark \
  --title "test: re-review compat exclusions for <slice>" \
  --body "$(cat <<'EOF'
## Summary
- Slice: <slice>
- Decisions: <N REMOVE / N NARROW / N RECLASSIFY / N RE-ID / N MERGE / N KEEP>
- Issues filed: #<a>, #<b>, …
- SPEC_DIVERGENCES changes: <none | proposed D<N>/R<N>>

## Hygiene checklist
- [x] No `wontfix` remains in slice
- [x] No Pathling ids (#2xxx, #437) remain in slice
- [x] Every `feature|bug|test-infra` has a fp2sql issue `id`
- [x] Every `design` cites a D-entry; every `ref-impl-bug` cites an R-entry
- [x] Upstream Pathling block for <file> folded into `fp2sql — <file>` block
- [x] No duplicate rules in scope

## Test plan
- [x] `mvn test -Dtest=YamlReferenceCompatTest` passes (0F/0E)

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

### Step 15: Wait for CI and merge

```bash
gh pr checks <PR> --repo piotrszul/fp2spark --watch
```

On green, squash-merge:

```bash
gh pr merge <PR> --repo piotrszul/fp2spark --squash --delete-branch
git checkout main && git pull
```

On red, investigate root cause; do not bypass hooks or force-push.

## Key reminders

- **One slice per PR.** Do not mix slices; duplicate/merge resolution within a slice is fine.
- **Spec is ground truth.** Trust the FHIRPath spec over fhirpath.js test expectations. Use
  the `fhirpath-spec` skill to resolve ambiguity; use `fhirpath-eval` to empirically verify
  fhirpath.js behaviour.
- **Never use `wontfix`.** Reclassify to one of `feature`, `bug`, `design`, `ref-impl-bug`,
  `test-infra`.
- **Never carry forward Pathling ids.** `#2xxx` and `#437` live on `pathling/pathling`, not
  `piotrszul/fp2spark`. File a fp2sql issue or reclassify to D/R.
- **Every rule needs a reference.** `id: "#NNN"` for feature/bug/test-infra; `D\d+` in comment
  for design; `R\d+` in comment for ref-impl-bug.
- **SPEC_DIVERGENCES.md changes require user approval.** Always propose and wait.
- **Approval table before editing.** No YAML changes until the table is approved.
- **Fold Pathling upstream blocks.** After a slice's review, each file should have exactly
  one authoritative `fp2sql — <file>` block. Delete emptied upstream blocks.
- **Prefer umbrella issues** for clusters of related rules (e.g., unsupported env vars, a
  family of unimplemented functions).
- **Narrow matchers win.** Prefer `any: ["exact expression"]` over open regex; `expression:`
  should cite why a regex was needed.
