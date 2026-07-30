# Adaptive Code Review for `implement-fhirpath` (Step 12)

**Date:** 2026-04-26
**Scope:** `.claude/skills/implement-fhirpath/SKILL.md`, Step 12 only

## Problem

Step 12 of the `implement-fhirpath` skill currently dispatches a single deep `superpowers:code-reviewer` subagent via the `superpowers:requesting-code-review` skill. The reviewer is thorough but slow on every PR — including the trivial cases (a new operation registration, a new `*Ops` entry, a new test class) that dominate this skill's output. The dominant pain is **wall-clock latency**.

## Goal

Cut wall-clock latency on trivial PRs without dropping review quality on risky ones. Keep findings inline (no PR comment round-trips). Preserve fresh-perspective independence (review runs in a subagent, not in the main agent).

## Design

### Two-tier review

Step 12 begins by **classifying** the PR as **LITE** or **FULL**, then dispatches the matching review path. Triage of findings is shared.

```
Step 11 simplify ✓
   ↓
Step 12.0  Classify (LITE | FULL)
   ├─→ 12.1 LITE  (Sonnet subagent → Skill("review", args=PR))
   │       ↓
   └─→ 12.2 FULL (superpowers:requesting-code-review, unchanged)
           ↓
Step 12.3  Triage findings (shared)
   ↓
Apply fixes → commit → push → Step 13
```

### Routing rules (Step 12.0)

The PR is **LITE** unless any signal below holds, in which case it is **FULL**.

**Workflow-state signals** (from earlier steps in the same skill run):

1. **Step 4 design-approval gate fired** — a framework-extending change was made.
2. **Step 10 proposed a new D/R entry** in `SPEC_DIVERGENCES.md`.
3. **Multi-function issue with non-trivial design choices** in any commit. (Judgment-based by design — there is no strict line-count or commit-count threshold; the skill self-assesses whether the multi-function work involved framework-level decisions or just parallel boilerplate.)

**Path-fallback signals** (from `git diff --name-only $BASE_SHA..$HEAD_SHA`):

4. Any change touched **outside this safe-set**:
   - `src/main/java/.../codegen/spark/ops/**`
   - `src/main/java/.../analyzer/OperationRegistry.java` — only when the change is purely a new entry registration (i.e., adding rows to the registry table). Any modification to the registration API itself, helper methods, or the registry's structural definition routes to FULL.
   - `src/test/java/.../ir/**` (new test classes only)
   - `src/test/resources/fhirpath-js/config.yaml` (compat cleanup)
5. **Diff > 600 lines added** (`git diff --stat $BASE_SHA..$HEAD_SHA`) — catches multi-function PRs that slipped past signals 1–3.

### LITE path (Step 12.1)

`Task`-dispatch a `general-purpose` subagent on `model: sonnet`. The subagent invokes the built-in `/review` slash command via the `Skill` tool, runs the resulting template against the PR, and returns a severity-classified report.

Subagent prompt (template):

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

The subagent's return value is the report; main conversation triages it (Step 12.3). No PR comment is posted.

**Why this shape:**
- Anthropic maintains the `/review` template — no template duplication or drift in the skill.
- Subagent dispatch preserves fresh perspective and keeps the diff out of the main context.
- Sonnet keeps quality high enough for the lite tier; Haiku was considered and rejected.

### FULL path (Step 12.2)

Identical to today's Step 12. `superpowers:requesting-code-review` invocation with the same template fields (`WHAT_WAS_IMPLEMENTED`, `PLAN_OR_REQUIREMENTS`, `BASE_SHA`, `HEAD_SHA`, `DESCRIPTION`).

### Triage (Step 12.3, shared)

Same protocol for both paths, with one LITE-specific addition:

- **Apply automatically** — clear-cut Critical/Important: bugs, missing test cases, dead code, hygiene violations, naming/typing fixes.
- **Surface to user** — anything touching public API, spec semantics, scope expansion, new D/R entries, framework changes.
  - **On the LITE path only:** when surfacing such a finding, *also* ask whether the user wants a **FULL review enforced** before deciding. A finding that elevates to user judgment is a signal the PR isn't actually trivial; FULL may catch related design-level issues LITE missed.
  - If the user opts to escalate → run Step 12.2 (FULL), merge both reports, re-triage, and present the combined surface-to-user set. Only then proceed with the user's decision.
  - If the user declines escalation → proceed with the standard triage protocol.
- **Defer / decline** — Minor findings that conflict with established patterns or don't survive a closer read.

(On the FULL path, no escalation prompt — FULL already ran.)

After triage, the existing Step 12 commit/push flow is unchanged.

## Edits to `implement-fhirpath/SKILL.md`

1. Split the current Step 12 body into:
   - **Step 12.0 — Classify** (new; routing rules above)
   - **Step 12.1 — Lite path** (new; dispatch instructions and subagent prompt)
   - **Step 12.2 — Full path** (today's content, retained verbatim)
   - **Step 12.3 — Triage** (extracted shared logic, plus LITE escalation gate)
2. Update the existing "Key Reminders" bullet on Step 12 to mention the LITE/FULL split and the escalation gate.

No changes to other steps. No changes outside `SKILL.md`.

## Known limitation

If Sonnet on the LITE path misses a subtle bug *and* the lite reviewer doesn't surface anything to user judgment, the bug will not be caught at Step 12. Mitigations are layered:

1. Step 4 design gate, Step 10 D/R proposals, the safe-set, and the 600-line ceiling route most risky changes to FULL outright.
2. Earlier steps (spec lookup in Step 3, Pathling consult in Step 4a, test designer in Step 5, full test suite in Step 6) catch most bugs before review.
3. The escalation gate in Step 12.3 lets the user enforce FULL on any surfaced finding.

The lite/full split is a calibrated tradeoff, not a bug-elimination guarantee.

## Out of scope

- Custom fp2sql-flavored reviewer prompt (would replace the `/review` template). Considered, deferred — the `/review` template covers code correctness, project conventions, and test coverage, which are the dominant gotchas. If the LITE path misses fp2sql-specific issues in practice, a follow-up can graduate to a custom prompt.
- Three-tier ladder (trivial / standard / risky). Considered, deferred — the simpler two-tier captures most of the wall-clock win.
- Changes to other skills (`requesting-code-review`, `simplify`, etc.).
