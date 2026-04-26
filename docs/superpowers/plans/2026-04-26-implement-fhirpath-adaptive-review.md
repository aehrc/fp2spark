# Adaptive Code Review for `implement-fhirpath` — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Split Step 12 of the `implement-fhirpath` skill into a two-tier adaptive review (LITE / FULL) with a shared triage section that includes a LITE→FULL escalation gate.

**Architecture:** Single-file edit to `.claude/skills/implement-fhirpath/SKILL.md`. The current Step 12 (lines 286–327) is rewritten as four sub-sections (12.0 Classify, 12.1 Lite, 12.2 Full, 12.3 Triage); the Step 12 entry in "Key Reminders" (line 364) is updated to reflect the new shape. No code, no tests, no other files touched. Verification is a content checklist plus a fresh-subagent walk-through against the spec.

**Tech Stack:** Markdown only. Verification uses `grep`, `Read`, and a `general-purpose` subagent.

**Spec:** `docs/superpowers/specs/2026-04-26-implement-fhirpath-adaptive-review-design.md` (commit `a357cc1`).

---

## File Structure

| File | Action | Region | Responsibility |
|---|---|---|---|
| `.claude/skills/implement-fhirpath/SKILL.md` | Modify | Lines 286–327 (Step 12 body) | Replaced with Step 12.0 / 12.1 / 12.2 / 12.3 sub-sections per spec. |
| `.claude/skills/implement-fhirpath/SKILL.md` | Modify | Line 364 (Key Reminders bullet) | Replaced with adaptive-review reminder. |

No new files. No deletions. No changes outside `SKILL.md`.

---

## Task 1: Lock starting state

**Files:**
- Read: `.claude/skills/implement-fhirpath/SKILL.md`

- [ ] **Step 1: Read the current Step 12 region**

Run:

```bash
sed -n '286,327p' .claude/skills/implement-fhirpath/SKILL.md
```

Expected: 42 lines starting with `### Step 12: Code Review` and ending with the `git push` block. Confirm the lines match what is shown in the spec's "FULL path mechanics" section — that text is the source for Step 12.2 and must be preserved verbatim.

- [ ] **Step 2: Read the current Key Reminders bullet**

Run:

```bash
sed -n '364p' .claude/skills/implement-fhirpath/SKILL.md
```

Expected output (single line):

```
- **Use `superpowers:requesting-code-review` for review (Step 12).** Findings come back in this conversation, severity-graded. Apply clear-cut Critical/Important fixes; surface anything that touches the framework, public API, or spec semantics; push back on Minor findings that conflict with established patterns.
```

If line numbers have drifted, re-locate with `grep -n "^### Step 12" .claude/skills/implement-fhirpath/SKILL.md` and `grep -n "Use \`superpowers:requesting-code-review\` for review" .claude/skills/implement-fhirpath/SKILL.md` and update line numbers in subsequent tasks.

---

## Task 2: Replace the Step 12 body

**Files:**
- Modify: `.claude/skills/implement-fhirpath/SKILL.md` (Step 12 region, lines 286–327)

- [ ] **Step 1: Apply the replacement**

Use the `Edit` tool. `old_string` must match the *entire* current Step 12 region exactly (header through final `git push`). `new_string` is:

````markdown
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
   - `src/test/java/.../ir/**` (new test classes only)
   - `src/test/resources/fhirpath-js/config.yaml` (compat cleanup)
5. **Diff > 600 lines added** (`git diff --stat $BASE_SHA..$HEAD_SHA` total insertions).

If none fire → proceed to Step 12.1 (LITE). Otherwise → Step 12.2 (FULL). Either way, triage via Step 12.3.

#### Step 12.1 — Lite path

`Task`-dispatch a `general-purpose` subagent on `model: sonnet` with this prompt (substitute the PR number from `gh pr view`):

```
You are reviewing PR #<NUMBER> in piotrszul/fp2spark.

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
````

- [ ] **Step 2: Verify the replacement**

Run the four checks below. Each should print at least one matching line.

```bash
grep -n "^#### Step 12.0 — Classify" .claude/skills/implement-fhirpath/SKILL.md
grep -n "^#### Step 12.1 — Lite path" .claude/skills/implement-fhirpath/SKILL.md
grep -n "^#### Step 12.2 — Full path" .claude/skills/implement-fhirpath/SKILL.md
grep -n "^#### Step 12.3 — Triage" .claude/skills/implement-fhirpath/SKILL.md
```

If any check returns nothing, the Edit didn't apply correctly — re-read the file, diagnose, and retry.

```bash
grep -n "FULL review enforced" .claude/skills/implement-fhirpath/SKILL.md
```

Expected: matches inside Step 12.3 (the LITE escalation gate). If missing, the new content is incomplete.

---

## Task 3: Update the Key Reminders bullet

**Files:**
- Modify: `.claude/skills/implement-fhirpath/SKILL.md` (line 364, Key Reminders entry for Step 12)

- [ ] **Step 1: Apply the replacement**

Use the `Edit` tool. `old_string`:

```
- **Use `superpowers:requesting-code-review` for review (Step 12).** Findings come back in this conversation, severity-graded. Apply clear-cut Critical/Important fixes; surface anything that touches the framework, public API, or spec semantics; push back on Minor findings that conflict with established patterns.
```

`new_string`:

```
- **Adaptive code review (Step 12).** Step 12.0 classifies the PR as LITE (Sonnet subagent invoking `/review` on the PR) or FULL (`superpowers:requesting-code-review`); routing is by Step 4 / Step 10 workflow state plus a path/size fallback. Both paths feed the shared triage in Step 12.3, which on LITE additionally offers the user a FULL escalation when surfacing a finding that needs their judgment. Apply clear-cut Critical/Important fixes; push back on Minor findings that conflict with established patterns.
```

- [ ] **Step 2: Verify the replacement**

```bash
grep -n "Adaptive code review (Step 12)" .claude/skills/implement-fhirpath/SKILL.md
grep -c "Use \`superpowers:requesting-code-review\` for review (Step 12)" .claude/skills/implement-fhirpath/SKILL.md
```

Expected: first command finds one match in the Key Reminders list. Second command prints `0` (the old bullet text is gone — the in-body Step 12.2 still mentions the skill, but in narrative form, not the old reminder phrasing).

---

## Task 4: Content checklist verification

**Files:**
- Read: `.claude/skills/implement-fhirpath/SKILL.md`

- [ ] **Step 1: Run the checklist**

Each command below should produce ≥ 1 matching line (or the specified count). Any miss is a failure that must be fixed before Task 5.

```bash
# Section headers
grep -c "^#### Step 12.0 — Classify"  .claude/skills/implement-fhirpath/SKILL.md   # → 1
grep -c "^#### Step 12.1 — Lite path" .claude/skills/implement-fhirpath/SKILL.md   # → 1
grep -c "^#### Step 12.2 — Full path" .claude/skills/implement-fhirpath/SKILL.md   # → 1
grep -c "^#### Step 12.3 — Triage"    .claude/skills/implement-fhirpath/SKILL.md   # → 1

# Routing rules present
grep -c "Step 4 design-approval gate fired"            .claude/skills/implement-fhirpath/SKILL.md   # → 1
grep -c "Step 10 proposed a new D/R entry"             .claude/skills/implement-fhirpath/SKILL.md   # → 1
grep -c "Multi-function issue with non-trivial design" .claude/skills/implement-fhirpath/SKILL.md   # → 1
grep -c "outside this safe-set"                        .claude/skills/implement-fhirpath/SKILL.md   # → 1
grep -c "Diff > 600 lines added"                       .claude/skills/implement-fhirpath/SKILL.md   # → 1

# Lite path mechanics
grep -c "general-purpose. subagent on .model: sonnet"  .claude/skills/implement-fhirpath/SKILL.md   # → 1
grep -c "Skill(skill=\"review\", args="                .claude/skills/implement-fhirpath/SKILL.md   # ≥ 1

# Full path preserved
grep -c "superpowers:requesting-code-review"           .claude/skills/implement-fhirpath/SKILL.md   # ≥ 1
grep -c "WHAT_WAS_IMPLEMENTED"                         .claude/skills/implement-fhirpath/SKILL.md   # ≥ 1

# LITE escalation gate
grep -c "FULL review enforced"                         .claude/skills/implement-fhirpath/SKILL.md   # → 1
grep -c "If the user opts to escalate"                 .claude/skills/implement-fhirpath/SKILL.md   # → 1

# Key Reminders bullet updated
grep -c "Adaptive code review (Step 12)"               .claude/skills/implement-fhirpath/SKILL.md   # → 1
grep -c "Use \`superpowers:requesting-code-review\` for review (Step 12)" .claude/skills/implement-fhirpath/SKILL.md   # → 0
```

If any expected count is wrong, locate the missing/extra content via `grep -n`, fix it, and re-run the checklist.

---

## Task 5: Walk-through verification by subagent

**Files:**
- Read: `.claude/skills/implement-fhirpath/SKILL.md`
- Read: `docs/superpowers/specs/2026-04-26-implement-fhirpath-adaptive-review-design.md`

- [ ] **Step 1: Dispatch a fresh subagent**

Use the `Agent` tool with `subagent_type: general-purpose`. Prompt:

```
Cross-check that two files agree on a design.

Spec:  docs/superpowers/specs/2026-04-26-implement-fhirpath-adaptive-review-design.md
Skill: .claude/skills/implement-fhirpath/SKILL.md (Step 12 only — sections 12.0 through 12.3, plus the Key Reminders bullet for Step 12)

Read both. For each requirement in the spec's "Design" and "Edits to
implement-fhirpath/SKILL.md" sections, answer:
  PASS — the requirement is captured in the SKILL.md text
  FAIL — the requirement is missing or misrepresented

Specifically verify all of:
- Routing: 5 signals (Step 4 gate, Step 10 D/R, multi-function judgment,
  safe-set paths, 600-line ceiling)
- Lite path: general-purpose subagent, model sonnet, invokes
  Skill("review", args=PR), returns severity-classified report
- Full path: superpowers:requesting-code-review, BASE_SHA/HEAD_SHA
  computation, all five template fields named
- Triage: apply / surface / defer categories all present; LITE-only
  escalation gate offering FULL review when surfacing to user; FULL path
  has no escalation prompt
- Key Reminders bullet replaced with the adaptive-review wording

Return ONLY:
- PASS/FAIL list, one line per requirement
- A final line: "Overall: PASS" or "Overall: FAIL — <reason>"

Do not edit either file. Under 300 words.
```

- [ ] **Step 2: Act on the verdict**

If `Overall: PASS` → proceed to Task 6.

If `Overall: FAIL` → fix each FAIL item in `SKILL.md`, re-run Task 4's checklist, then re-dispatch a fresh subagent (do not re-use the previous one — it has stale context). Loop until PASS.

---

## Task 6: Commit

**Files:**
- Stage: `.claude/skills/implement-fhirpath/SKILL.md`

- [ ] **Step 1: Confirm clean status apart from the SKILL.md edit**

```bash
git status --short
```

Expected: exactly one modified file:

```
 M .claude/skills/implement-fhirpath/SKILL.md
```

If anything else is modified or untracked, investigate before staging.

- [ ] **Step 2: Stage and commit**

```bash
git add .claude/skills/implement-fhirpath/SKILL.md
git commit -m "$(cat <<'EOF'
chore(skills): adaptive code review for implement-fhirpath Step 12

Split Step 12 into 12.0 Classify / 12.1 Lite / 12.2 Full / 12.3 Triage.
LITE dispatches a Sonnet subagent that invokes the built-in /review
slash command on the PR; FULL keeps today's superpowers:requesting-code-review
flow. Routing combines Step 4/Step 10 workflow state with a path/size
fallback. Triage is shared and adds a LITE-only escalation gate offering
the user a FULL review whenever a finding requires their judgment.

Spec: docs/superpowers/specs/2026-04-26-implement-fhirpath-adaptive-review-design.md

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

- [ ] **Step 3: Verify the commit**

```bash
git log --oneline -1
git show --stat HEAD
```

Expected: commit subject starts with `chore(skills):`, exactly one file changed, diff size in the low hundreds of lines.

---

## Self-Review Notes

- Spec coverage: each numbered design section maps to a task. Routing → Task 2 (Step 12.0 content). Lite mechanics → Task 2 (Step 12.1 content). Full path → Task 2 (Step 12.2, preserved verbatim). Triage + escalation gate → Task 2 (Step 12.3). SKILL.md edits §7 — body covered by Task 2, Key Reminders bullet by Task 3.
- No placeholders. Every Edit `old_string` / `new_string` payload, every grep command, and the commit message are concrete.
- Verification is layered: per-edit grep checks (Tasks 2 & 3), full content checklist (Task 4), independent subagent walk-through (Task 5).
- No code, no tests — verification rhythm is "edit, grep-back, walk-through" instead of "write test, fail, implement, pass." This is the right shape for a markdown skill change; preserved the TDD spirit (verify before commit) without forcing unit-test machinery.
