# Specification Divergences

Intentional functional differences between fp2sql and the FHIRPath specification,
and confirmed bugs in the fhirpath.js reference implementation.

This document, together with the [FHIRPath spec](specs/FHIRPath.md), is the authority
for deciding whether a compatibility-suite exclusion is a valid design choice vs a bug.

Exclusion rules in `config.yaml` that follow from these divergences use
`type: design` and reference the divergence ID (e.g. `comment: "D1"`).
Reference implementation bugs use `type: ref-impl-bug` and reference the
bug ID (e.g. `id: "R1"`).

---

## D1. Static typing and cardinality

fp2sql resolves types and cardinalities at compile time from the FHIR schema. The
FHIRPath spec assumes a dynamic evaluator that inspects collections at runtime. This
means fp2sql **cannot** perform:

- **Runtime singleton detection.** The spec says math/comparison operators should
  evaluate their operands and error if the result is not a single element (§6.6).
  fp2sql rejects multi-valued operands at compile time based on schema cardinality.
  An expression like `Patient.name + 'x'` errors during compilation even if the
  patient happens to have exactly one name at runtime.

- **Runtime boolean singleton evaluation.** The spec defines implicit boolean
  conversion for `where()`, `iif()`, and boolean operators: a single-element
  collection is truthy/falsy based on its value (§4.1). fp2sql requires the
  expression to be statically single-valued; it cannot fall back to runtime
  singleton-to-boolean coercion.

- **Runtime empty propagation across types.** The spec says operations on empty
  collections return empty regardless of type compatibility (§1.5). fp2sql
  type-checks operands statically, so `{} + 'x'` may error if `{}` is typed as
  a non-string collection rather than propagating empty.

## D2. No support for contained resources

FHIR resources may embed other resources via `Resource.contained`. fp2sql does not
support traversal into contained resources (e.g. `Patient.contained`). This is a
limitation of the Pathling encoding model which stores resources as flat rows.

## D3. No support for attributes on primitive types

FHIR primitive elements can carry `id` and `extension` attributes alongside their
value (e.g. `Patient.name.given[0].id`). fp2sql does not support this structure.

Consequences:

- Primitive elements with extensions but **no value** are invisible — they appear
  as absent rather than as an element with a null value and non-empty extensions.
- `element.id` on a primitive element returns empty.
- `element.extension()` on a primitive element is not supported.

---

# Reference Implementation Bugs

Confirmed cases where the fhirpath.js reference implementation diverges from the
FHIRPath specification. fp2sql follows the spec in these cases; the fhirpath.js test
suite expectations are excluded with `type: ref-impl-bug`.

## R1. Calendar duration != UCUM literal returns true instead of empty

The spec explicitly states that calendar durations and definite duration UCUM units
above seconds are "un-comparable" for equality (§6.1, Quantity Equality):

```
1 year = 1 'a'  // {} an empty collection
1 second = 1 's' // true
```

Since `!=` is defined as `not(=)` and `not({})` is `{}`, `1 year != 1 'a'` should
also return empty. However, fhirpath.js returns `true` due to a `null` vs `undefined`
confusion in `engine.unequal`: `FP_Quantity.equals()` returns `null` for un-comparable
quantities, but `engine.unequal` only checks for `=== undefined`, so `!null` evaluates
to `true`.

Affected expressions: `1 year != 1 'a'`, `1 month != 1 'mo'`,
`'1 year'.toQuantity() != 1 'a'`.
