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

## D4. Monomorphic collections

The FHIRPath spec allows collections to contain elements of different types (e.g., a
mix of String, Integer, and Boolean values). fp2sql only supports monomorphic
collections where all elements share a single defined type. The only mechanism for
multiple types in one collection is FHIR choice elements, but these are still
represented as a single `choiceOf(A|B|C)` type.

Consequences:

- `ofType()` on a truly heterogeneous collection is not possible — collections are
  always homogeneously typed at compile time.
- Union of collections with different element types is not supported.

## D5. TypeInfo struct format includes baseType

fp2sql returns `TypeInfo` with `namespace`, `name`, and `baseType` fields per the
[CI build FHIRPath reflection spec](https://build.fhir.org/ig/HL7/FHIRPath/#reflection),
while fhirpath.js returns only `{name, namespace}`. This is an intentional design
choice following the full spec definition (ref: #111).

Consequences:

- `type()` results include the `baseType` field, making the returned struct
  structurally different from fhirpath.js expectations.
- Test assertions comparing type() output against `{name, namespace}` maps will
  fail due to the additional field and different representation format.

## D6. Choice type polymorphism requires explicit narrowing

FHIR choice elements (e.g., `Observation.value[x]`) must be narrowed to a specific
variant via `ofType()`, `is`, or `as` before field traversal or operations. Direct
use of a choice type in expressions (e.g., `Observation.value.value`,
`Observation.value > 100`) is rejected at compile time.

The FHIRPath spec allows dynamic dispatch on the runtime type; fp2sql's static
analyzer cannot determine which variant applies without an explicit narrowing step.

Consequences:

- `Observation.value.unit` requires `Observation.value.ofType(Quantity).unit`.
- Comparison or arithmetic on unnarrowed choice types fails overload resolution.

## D7. CODING literal lexer takes precedence over union of string literals

fp2sql's grammar (inherited from Pathling) defines a FHIR-specific CODING literal
token of the form `'system'|'code'|'version'`, reusing `|` as the component
delimiter:

    CODING : CODING_COMPONENT '|' CODING_COMPONENT (...)* ;

Because ANTLR's lexer is greedy and builds a single token from contiguous
characters (no whitespace skipping inside a token rule), an expression that
unions string literals with `|` without intervening whitespace (e.g.,
`('a'|'b').count()`) is tokenized as a single CODING token rather than as two
STRING tokens separated by the union operator. The parser then rejects the
expression at the `term` rule.

The FHIRPath spec does not define CODING literals — they are a FHIR-specific
extension. fhirpath.js does not support them, so it does not exhibit this
ambiguity: it parses `'a' | 'b'` as a collection union.

Consequences:

- Expressions that union two or more string literals with `|` without
  surrounding whitespace fail at parse time in fp2sql.
- **Workaround**: put whitespace around the `|` operator — `('a' | 'b')` parses
  correctly as a union because the CODING lexer rule cannot span whitespace.
- Alternative workarounds: use `combine()` explicitly, or use semicolon (`;`)
  where the grammar accepts it.
- Fixing this without the whitespace workaround would require removing CODING
  literal support from the grammar or introducing context-sensitive lexing.

## D8. UCUM ↔ calendar-duration conversion across the seconds boundary

The FHIRPath spec (§3.3 Quantity) defines calendar durations (year/month/week/day/...)
as **equivalent** (`~`) — not **equal** (`=`) — to their UCUM counterparts above the
seconds boundary. The `toQuantity(unit)` conversion table (§5.3) bridges calendar
units to UCUM only via `1 second = 1 's'`; all other conversions are calendar-to-
calendar. §9 further states that definite-duration quantities above seconds cannot
be used in date/time arithmetic (`1 'wk' + @2024-01-01` is an error).

fhirpath.js (`src/misc.js:103-154`) interprets these constraints strictly and
refuses `toQuantity()` cross-system conversion across the seconds boundary: it
returns `[]` for inputs where exactly one of (source unit, target unit) is a
calendar keyword and at least one is greater than one second.

fp2sql is more permissive. `QuantityConvertToUnit.convertUcumToCalendar` /
`convertCalendarToUcum` (`src/main/java/com/example/fhirpath/codegen/spark/udf/QuantityConvertToUnit.java`)
convert across systems using UCUM's internal factors plus the spec's equivalence
relationships (`1 'wk' ~ 1 week`), producing a converted Quantity where
fhirpath.js returns empty.

Consequences:

- `'1 \'wk\''.toQuantity('days')` returns `7 'd'` in fp2sql, `[]` in fhirpath.js.
- Neither implementation violates the spec — the spec neither mandates nor
  forbids cross-system `toQuantity()` above seconds. fhirpath.js takes the
  conservative reading; fp2sql takes the permissive one.

---

# Implementation Policy Choices

Cases where the FHIRPath specification leaves a behaviour open to the
implementation, **and** fp2sql's choice differs from the choice made by a
reference implementation. fp2sql is spec-compliant in every entry here; these
are not divergences from the spec, but they are observable behavioural
differences.

The spec leaves a behaviour open in one of two ways:

- **Explicit deferral** — the spec says so, typically with phrasing like
  "implementation decision" or "policy decision" (P1).
- **Silence on an input shape** — the spec defines the general rule and
  enumerates its exceptions, but some input falls outside both, so the answer
  follows from how exhaustively that enumeration is read (P2). The other
  implementation reads that same silence differently than fp2sql does. Where
  the spec *does* answer and the other implementation contradicts it, that is
  an R-entry (reference implementation bug), not a P-entry.

The reference implementation compared against is **fhirpath.js** for core
FHIRPath, and **Pathling** for the FHIR-specific bindings that fhirpath.js does
not implement (the terminology functions). Each entry names which.

Where a compatibility test surfaces the difference, the `config.yaml` exclusion
uses `type: design` and references the policy ID (e.g. `comment: "P1"`). Some
entries back no exclusion, because the compat suite does not exercise the
behaviour at all; those say so.

## P1. Offset-less DateTime treated as UTC

FHIRPath spec §6.1 ("Date/Time Equality") explicitly defers the missing-offset
case to the implementation:

> For DateTime values that do not have a timezone offsets, whether or not to
> provide a default timezone offset is a policy decision. […] To support
> comparison of DateTime values, either both values have no timezone offset
> specified, or both values are converted to a common timezone offset. The
> timezone offset to use is an implementation decision.

§6.2 ("Comparison") defers to the same rule.

**fp2sql's choice:** offset-less DateTime values are treated as UTC. This is
applied in `TemporalNormalize` for both equality and ordering, and inherited
by union dedup.

**fhirpath.js's choice:** offset-less DateTime values are interpreted in the
local-server timezone (an artifact of going through the JavaScript `Date`
constructor in `types.js`).

**Why fp2sql chose UTC:**

- Determinism: results are independent of the JVM / executor default timezone,
  which matters for distributed Spark execution and reproducible queries.
- Parity with Pathling, the codegen reference
  (`FhirPathDateTime.java:111` defaults missing offsets to `Z`).
- Consistency with already-pinned tests (e.g. `EqualityOperatorsDslTest`:
  `@2020-01-01T10:00:00+00:00 = @2020-01-01T10:00:00` is `true`).

**Observable consequence:** for any pair of expressions where one DateTime
has an explicit offset and the other does not, fp2sql and fhirpath.js may
disagree. The fhirpath.js compat suite happens not to exercise this case
today (no mixed-offset pairs in `6.1_equality.yaml`, `6.2_comparision.yaml`,
or `5.4_combining.yaml`), but future test additions could.

## P2. `memberOf()` on a CodeableConcept with no codings returns false

**Scope note:** unlike P1, the reference implementation compared against here is
**Pathling**, not fhirpath.js — fhirpath.js implements no FHIR terminology
functions, so it offers no behaviour to compare. This entry also backs no
`config.yaml` exclusion: the fhirpath-js compat suite contains no `memberOf`
cases at all. It is documentation of an interpretation, not the justification
for a rule.

FHIR FHIRPath ("Additional functions") defines the concept-valued case and then
enumerates the cases that yield empty:

> When invoked on a single concept-valued element, returns true if any code in
> the concept is a member of the given valueset.
>
> If the valueset cannot be resolved as a uri to a value set, or the input is
> empty or has more than one value, the return value is empty.

A `CodeableConcept` that is *present* but carries no `coding` — a free-text
concept such as `{"text": "patient reports chest pain"}` — is not in that
enumeration. It is not an empty input: the element exists.

**fp2sql's choice:** `false`. "Any code in the concept is a member" is vacuously
false when the concept contains no codes, and the spec's empty-result
enumeration is read as exhaustive. The concept identifies no code that could be
a member of any value set, which is a determinate answer rather than an
unanswerable question.

**Pathling's behaviour:** `empty`. `MemberOfUdf.doCall` returns null when its
decoded coding stream is null, and `TerminologyUdfHelpers.decodeOneOrMany`
returns null for a null column — which is how the Pathling encoder represents an
absent `coding` list. Note that Pathling's test suite does not cover this case
(its `emptyCoding` fixture is a null *Coding*, not a coding-less concept), so
this is inferred from the code path rather than from a pinned expectation.
Whether it is intended has been raised upstream as
[pathling#2700](https://github.com/aehrc/pathling/issues/2700).

**Boundary — the divergence is narrower than it first appears.** Only a concept
that is present *and* coding-less differs. Everything a caller would loosely
call "an empty CodeableConcept" already yields empty in both engines, because
HAPI's encoder writes a content-free `CodeableConcept` as an absent struct:

| Input | fp2sql |
| --- | --- |
| `code` absent entirely | empty |
| `code` present, no content at all | empty |
| `code` present with `text` only, no `coding` | **false** ← this entry |
| `code` with codings, none a member | false |
| `code` with codings, at least one member | true |
| value set unresolvable | empty |

Pinned by `TerminologyFunctionsTest`, which covers every row above.

**Observable consequence:** inside `where()`, `false` and empty are both falsy,
so filtering expressions such as
`Observation.component.where(code.memberOf(url))` agree with Pathling
regardless. The two differ only where the boolean is observed directly — for
example as a projected column, or under `not()`, where fp2sql yields `true` and
Pathling empty.

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

## R2. Bare `length` without parentheses treated as length() function

The FHIRPath spec §5 states: "Function names are always followed by a `()` to
distinguish them from path navigation." Therefore `$this.length` (without
parentheses) should be path navigation (field access), not the `length()` function
invocation. fhirpath.js treats bare `length` as the function, causing
`$this.length` and `length()` to return the same result.

Affected expressions:
`Patient.name.given.select($this.length) = Patient.name.given.select(length)`.

## R3. fhirpath.js operator precedence: `is`/`as` vs comparison/union

The FHIRPath spec operator precedence table (§3.5.7) defines `is`/`as` at level #06,
higher than `|` (#07) and comparison operators `<`, `>`, `<=`, `>=` (#08). fhirpath.js
appears to parse these operators with wrong relative precedence.

For example, `1 > 2 is Boolean` should parse as `1 > (2 is Boolean)` per spec,
yielding `1 > true` which is a type error (comparing Integer with Boolean). fhirpath.js
parses it as `(1 > 2) is Boolean` and returns `true`.

Similarly, `1 | 1 is Integer` should parse as `1 | (1 is Integer)` = `1 | true`,
a union of Integer and Boolean. fhirpath.js parses it as `(1 | 1) is Integer` and
returns `true`.

Affected expressions: `1 > 2 is Boolean`, `1 | 1 is Integer`.

## R4. fhirpath.js allows `$this` outside expression (lambda) parameters

The FHIRPath spec (§3.3) defines `$this` as an iteration variable available only
within functions that take an `expression` parameter (e.g., `where()`, `select()`,
`all()`, `aggregate()`): "$this … represent[s] the item from the input collection
currently under evaluation."

fhirpath.js allows `$this` in regular (non-expression) function arguments such as
`subsetOf()` and `supersetOf()`, where it appears to resolve to the root input
context. This usage is not defined by the spec.

Affected expressions: `Patient.name.first().subsetOf($this.name)`,
`Patient.name.subsetOf($this.name.first())`,
`Patient.name.first().supersetOf($this.name)`,
`Patient.name.supersetOf($this.name.first())`.

## R5. Time literals with timezone offset

The FHIRPath spec explicitly defines `Time` as a local-time type without a
timezone component:

- §5.1 (L368): `Time: @T14:34:28 (@ followed by ISO8601 compliant time beginning
  with T, no timezone offset)`
- §5.1 (L528-529): `Time values in FHIRPath do not have a timezone or timezone
  offset.`

fhirpath.js accepts `Time` literals with a trailing timezone offset (`@T17:00-05:00`,
`@T09:45Z`, `@T12+04:00`) and evaluates timezone-aware arithmetic, comparison, and
equality on them. This extends beyond the spec's `Time` definition; the spec places
timezone offsets only on `DateTime` (§5.1 L575-577).

fp2sql follows the spec and rejects timezone-bearing `Time` literals during parsing.

Affected expressions: time-literal comparisons and arithmetic such as
`@T17:00-05:00 < timeWithT.toTime()`, `@T23:59:23-05:00 + 2 minutes`,
`@T10:04:23-04:00 = @T14:04:23Z`, `@T09:45Z + 120 seconds`. 33 expressions across
7 case files (5.5_conversion, 5.6_string_manipulation, 6.1_equality,
6.2_comparision, 6.6_math, 7_aggregate, fhir-r4).

## R6. `Quantity + Date/DateTime/Time` commutation accepted

The FHIRPath spec defines time-valued Quantity arithmetic with an asymmetric
operand signature:

- §6.6.5 addition: "The left operand must be a Date, DateTime, or Time;
  the right operand must be a Quantity with a time-valued unit."
- §6.6.7 subtraction: same pattern (Date/DateTime/Time minus Quantity).

The reverse operand order is not defined.

fhirpath.js (`src/math.js` lines 70–77) permissively swaps the operands when the
first is a Quantity and the second is a Date/DateTime/Time, evaluating
`Quantity + Date` as `Date + Quantity`. This is an extension beyond the spec's
defined signature and produces a defined result (`1 year + @2016-02-29` returns
`[@2017-02-28]`) where the spec defines none.

fp2sql follows the spec's operator signature strictly and rejects
`Quantity + Date/DateTime/Time` at overload resolution.

Affected expressions: `1 year + @2016-02-29` (6.6_math.yaml). Workaround in
FHIRPath expressions that target both engines: put the Date/DateTime/Time on
the left (`@2016-02-29 + 1 year`).

## R7. fhirpath.js types `Patient.id` as `System.String` (FHIR.id lost)

fhirpath.js reports `Patient.id.type() = {namespace: "System", name: "String"}`
rather than `{namespace: "FHIR", name: "id"}`. Consequently:

```
Patient.id is System.String    // true  (fhirpath.js)
Patient.id is FHIR.id          // false (fhirpath.js)
Patient.id is FHIR.string      // false (fhirpath.js)
```

This is inconsistent with fhirpath.js's treatment of other FHIR primitives.
`Patient.active` keeps its FHIR namespace (`FHIR.boolean`), so under the same
strict namespace rule it intentionally applies to `is`/`as`/`ofType` (see
[#188](https://github.com/aehrc/fp2spark/issues/188)), `Patient.active is
System.Boolean` returns `false` — the correct FHIR-namespace-preserving
behavior. The `id` case collapses to System.String because fhirpath.js's model
does not register `id` as a distinct FHIR primitive (unlike `boolean`,
`string`, `decimal`, etc.).

fp2sql (correctly per the FHIR spec) types `Patient.id` as `FHIR.id` and
therefore reports `Patient.id is System.String = false`. fhirpath.js's behavior
is a ref-impl quirk resulting from an incomplete FHIR primitive registry.

Affected expressions: `Patient.id is System.String` (6.3_types.yaml, r4 and r5).

## R8. fhirpath.js evaluates undefined-field traversal against raw JSON

FHIRPath spec §3 ("Path selection") states that when an identifier "cannot be
resolved, the evaluation will end and signal an error to the calling environment."
fhirpath.js does not implement this — for paths that reference an identifier not
present in the FHIR model (e.g. `Observation.CustomField`), it neither errors
nor returns empty: it permissively looks the key up in the raw JSON object.
When the resource happens to carry a non-FHIR field, fhirpath.js returns that
value as if it were a defined element.

```
Observation.CustomField = 'test'    // [true]  (fhirpath.js, raw JSON has CustomField: "test")
Observation.CustomField = 'test'    // []      (fp2sql / Pathling — empty per FHIR-schema-bound model)
```

fhirpath.js's `engine.MemberInvocation` (`src/fhirpath.js:408`) falls through to
a raw-key lookup on the JSON object when the model has no such element.

fp2sql is FHIR-schema-bound: undefined identifiers resolve to an empty
collection in the analyzer (`Literal(null, NULL)` via
`Analyzer.resolveTraversal`), matching Pathling's
`traverse(...).orElse(EmptyCollection)`. fp2sql does not raise the
strict-spec error and instead follows FHIRPath's empty-propagation
discipline.

Affected expressions: `CustomField = 'test'`, `Observation.CustomField = 'test'`
(3.2_paths.yaml).
