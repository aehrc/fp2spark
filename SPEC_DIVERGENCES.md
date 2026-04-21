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
