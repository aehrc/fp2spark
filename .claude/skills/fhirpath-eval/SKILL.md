---
name: fhirpath-eval
description: >
  EXECUTE a FHIRPath expression against a FHIR resource and return the actual result from
  fhirpath.js (the official reference implementation). DO NOT reason about what the
  expression returns — your FHIRPath mental model is not reliable. Run it. Required
  whenever the user supplies a FHIRPath expression together with a resource (inline JSON,
  a .json file path like `.local/work/foo.json` or `src/test/resources/...`, or a named
  FHIR resource in context), and asks any of: what does it return, does it evaluate to X,
  run this, check this on a Patient/Observation/Bundle, verify, sanity-check, what does
  fhirpath.js give, what does the reference give, our output disagrees — what's correct,
  round-trip check, empty-handling check, is my test expectation right. Required for
  debugging fhirpath.js behaviour, verifying test expectations empirically, and resolving
  spec ambiguities by running the expression. Trigger even if the user never says
  "fhirpath.js" — a concrete question about the value of an expression on data is the
  trigger. NOT for: spec-text lookup ("what does the spec say") → fhirpath-spec;
  explaining evaluation without running → fhirpath-spec; writing tests →
  fhirpath-test-designer; implementing a function from an issue → implement-fhirpath;
  enabling compat suites → enable-compat-suite; refactor/review → other skills.
---

# FHIRPath Evaluator (reference implementation)

Runs FHIRPath expressions against a resource using the official fhirpath.js reference implementation at `.local/fhirpath.js`. Use this whenever executing the expression will give a better answer than reasoning about it — concrete results beat speculation for edge cases, empty propagation, type coercion, and function argument scoping.

## Prerequisite (check once per workspace)

The skill depends on `.local/fhirpath.js` being a symlink (or checkout) with `node_modules` installed:

```bash
ls .local/fhirpath.js/node_modules/antlr4 >/dev/null 2>&1 || \
  (cd .local/fhirpath.js && npm install)
```

If `.local/fhirpath.js` does not exist at all, stop and tell the user — the reference implementation must be cloned or symlinked first.

## Running

Invoke the bundled script via Bash from the project root:

```bash
node .claude/skills/fhirpath-eval/scripts/fp-eval.js \
  --expr '<expression>' \
  [--resource <path-or-inline-json>] \
  [--env '<json>'] \
  [--model r4|stu3|r5]
```

- `--expr` (required): the FHIRPath expression. Single-quote the whole thing to keep it as one shell argument and preserve `$this`, brackets, etc.
- `--resource` (optional): either a path to a `.json` file OR an inline JSON string (must start with `{` or `[`). Defaults to `{}` when omitted — fine for pure literal expressions like `'abc'.substring(1)`.
- `--env` (optional): JSON object of environment variables. Keys are the variable names **without** the leading `%`. Example: `--env '{"myvar": 42}'` makes `%myvar` available.
- `--model` (optional): `r4`, `stu3`, or `r5`. Loads the matching FHIR type model so polymorphic access (`value.ofType(Quantity)`, polymorphic choice elements, `resolve()`, etc.) work. Omit when the expression uses only system types and named properties.

Output: pretty-printed JSON on stdout. Errors (syntax, evaluation, bad input) go to stderr and the script exits non-zero.

## Examples

### Literal expression, no resource needed

```bash
node .claude/skills/fhirpath-eval/scripts/fp-eval.js --expr "'abc'.substring(1)"
# => ["bc"]
```

### Inline resource

```bash
node .claude/skills/fhirpath-eval/scripts/fp-eval.js \
  --expr "name.given.first()" \
  --resource '{"resourceType":"Patient","name":[{"given":["Jane","A"]}]}'
# => ["Jane"]
```

### Resource file with FHIR R4 model

```bash
node .claude/skills/fhirpath-eval/scripts/fp-eval.js \
  --expr "Bundle.entry.resource.ofType(Observation).value.ofType(Quantity).value" \
  --resource ./.local/work/bundle.json \
  --model r4
```

### Environment variable

```bash
node .claude/skills/fhirpath-eval/scripts/fp-eval.js \
  --expr "name.where(family = %target)" \
  --resource ./patient.json \
  --env '{"target": "Doe"}'
```

### Comparing behaviours

Run the script twice with different expressions on the same resource. Small shell loops are fine; this skill is intended for interactive, per-invocation queries.

## Interpreting results

- The result is **always** a JSON array. A single-value result is still `[value]`; an empty result is `[]`. That is FHIRPath's collection semantics, not a quirk of the script.
- `[]` usually means the path did not resolve (missing element, filter with no matches, navigation from the wrong type). It is not in itself an error.
- Exit code `1` with a stderr "Evaluation error" means fhirpath.js threw — e.g., a parse error, wrong argument count, ambiguous identifier, or a type constraint violation. Read the message; it is usually precise.
- Exit code `2` means the invocation itself was bad (missing `--expr`, unparseable `--env`, etc.).
- When `--model` is not supplied, type-aware operations such as `ofType(FHIR.Observation)` may silently return `[]` because fhirpath.js has no way to know the resource's type hierarchy. If a polymorphic expression surprises you, add `--model r4`.

## When to use this skill

- The user asks "what does fhirpath.js return for …" or "does this expression evaluate to …".
- A spec ambiguity needs a concrete resolution — run it and see.
- You were about to hand-write a throwaway Node script to call `fhirpath.evaluate(...)` — use this instead; it already handles path resolution, env vars, and the FHIR model.
- You need to confirm a fp2sql test expectation against the reference.

## When NOT to use

- Pure spec-text lookups ("what does the spec say about …") — use `fhirpath-spec`.
- Reading fhirpath.js source to understand an algorithm — also `fhirpath-spec`.
- Batch evaluation across thousands of resources — write a dedicated script; this skill is per-invocation.
