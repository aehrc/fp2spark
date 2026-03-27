# FHIRPath Type System and Signatures (Element‑first)

This single specification combines and supersedes the prior `TYPES.md` and `FHIRPATH_TYPES.md` documents (as of 2025‑10‑18). It defines the element‑first type model, implicit adapters, and the complete set of FHIRPath operator/function signatures used by this project.

## Goals

Provide a simple, precise, and implementable static type model for `FHIRPath → IR → SQL` that:
- Tracks element type and cardinality (shape)
- Supports implicit adaptations (casts) with costs for overload resolution
- Enables concise polymorphic operator/function signatures

## Notation

- Shapes (cardinality + element type):
  - `?T` = `Single[T]` (`0..1`)
  - `*T` = `Many[T]` (`0..*`)
- Lambdas: `(S_in ⇒ S_out)` where `S` are shapes (`?T` or `*T`).
- Optional parameters (doc notation): trailing `[arg : S]` desugars to two overloads: with and without that argument; optional parameters must be trailing (no mandatory params after an optional)
- Constraints: `[ … ] ⇒ sig` precede the arrow; we use named predicates like `Comparable T`.
- LUB: `LUB(T, U)` is least upper bound in the element-type lattice; must exist when stated.
- Cardinality lattice (join): `? ⊔ ? = ?`, `? ⊔ * = *`, `* ⊔ ? = *`, `* ⊔ * = *`

- Top/Bottom element types:
  - `Any` = top element type; `⊥` (Nothing) = bottom element type
  - Subtyping: `⊥ <: T <: Any`; `LUB(T, ⊥) = T`
- Empty literal: `{}` has principal type `?⊥`. For matching convenience only, `?⊥ ⇄ *⊥` (cost 0) is allowed.

## Type sets and predicates

```text
Numeric        = { INTEGER, LONG (STU), DECIMAL }
Arithmetic     = Numeric ∪ { QUANTITY }
TemporalDate   = { DATE, DATE_TIME }
TemporalTime   = { TIME }
Temporal       = TemporalDate ∪ TemporalTime
Comparable     = { STRING, INTEGER, LONG (STU), DECIMAL, QUANTITY, DATE, DATE_TIME, TIME }
StringLike     = { STRING }
BooleanLike    = { BOOLEAN }
Equatable T    = predicate: T supports equality (=) with implicit conversions per spec
Equivalent T   = predicate: T supports equivalence (~) semantics per spec
```

Note: `QUANTITY` comparability/equality depends on dimensional compatibility (units); this is a runtime check.

### Specification style preference

Prefer constrained, named type‑set signatures ("for‑each" form) whenever possible. For example, use `[T ∈ Arithmetic] ⇒ abs(?T) → ?T` instead of listing each numeric/quantity overload; use `TemporalDate`/`Temporal` for date/time component extractors.

## Implicit adaptations (adapters) and cost

Adapters enable type‑checking and overload resolution via least‑cost plans (multi‑step allowed; costs are additive). Mixed‑type arithmetic resolution relies entirely on these adapters and their costs; there is no separate promotion table. Numeric division (/) always yields DECIMAL.

- FHIRPath‑defined implicit conversions (from `specs/FHIRPath.md`):
  - Numeric widening:
    - `INTEGER → DECIMAL` (cost 1)
    - `INTEGER → LONG` (cost 1, STU/optional)
    - `LONG → DECIMAL` (cost 1, STU/optional)
  - To quantity (default unit `'1'` when originating from scalars):
    - `INTEGER → QUANTITY` (cost 1)
    - `DECIMAL → QUANTITY` (cost 1)
    - `LONG → QUANTITY` (cost 1, STU/optional)
  - Temporal:
    - `DATE → DATE_TIME` (cost 1)

- Additional project adapters (not defined by FHIRPath):
  - FHIR value extraction: `Fhir[Prim] → Prim` (cost 1) for FHIR primitives supporting `getValue()`

- Arity convenience:
  - Only for bottom: `?⊥ ⇄ *⊥` (cost 0) to allow `{}` to match either shape

If multiple matches tie on minimal cost, treat as ambiguity (error) unless a deterministic tiebreak is defined.

## Signature notation (Haskell‑style)

- Quantification: `∀ T, U, K, L` (element types), `α, β ∈ {?, *}` (arity variables)
- Constraints: `[ … ] ⇒ …` (constraints appear before the arrow)
- Shapes in arguments/results: `α T` means a shape (``?T`` or ``*T``)
- Lambdas: `(S1 ⇒ S2)` (parentheses for lambdas)
- Optional parameters (doc notation): trailing `[arg : S]` desugars to two overloads: with and without that argument; optional parameters must be trailing
- Cardinality lattice (join): `? ⊔ ? = ?`, `? ⊔ * = *`, `* ⊔ ? = *`, `* ⊔ * = *`

### Minimal illustrative examples

These examples illustrate the shape notation and constraints (not exhaustive):

```text
-- first
∀ T, α. first(α T) → ?T

-- where (per‑element predicate)
∀ T, α. where(α T, (?T ⇒ ?BOOLEAN)) → α T

-- select/map (per‑element)
∀ T, U, α. select(α T, (?T ⇒ ?U)) → α U

-- union (requires LUB)
∀ K, L, α, β. [LUB(K, L) defined] ⇒ union(α K, β L) → *LUB(K, L)
```

## FHIRPath operator and function signatures

The sections below mirror the organization of the FHIRPath specification. Unless otherwise noted, they operate on collections and return collections; singleton evaluation rules and empty propagation follow the spec.

### Functions

#### Existence

```text
∀ T, α. empty(α T) → ?BOOLEAN
∀ T, α. exists(α T, [ (?T ⇒ ?BOOLEAN) ]) → ?BOOLEAN
∀ T, α. all(α T, (?T ⇒ ?BOOLEAN)) → ?BOOLEAN
∀ α.    allTrue(α BOOLEAN) → ?BOOLEAN
∀ α.    anyTrue(α BOOLEAN) → ?BOOLEAN
∀ α.    allFalse(α BOOLEAN) → ?BOOLEAN
∀ α.    anyFalse(α BOOLEAN) → ?BOOLEAN
∀ T.    subsetOf(*T, *T) → ?BOOLEAN
∀ T.    supersetOf(*T, *T) → ?BOOLEAN
∀ T, α. count(α T) → ?INTEGER
∀ T, α. distinct(α T) → α T
∀ T, α. isDistinct(α T) → ?BOOLEAN
```

#### Filtering and projection

```text
∀ T, α. where(α T, (?T ⇒ ?BOOLEAN)) → α T
∀ T, U, α. select(α T, (?T ⇒ ?U)) → α U
∀ T, α. repeat(α T, (?T ⇒ α T)) → α T
∀ T, U, α. ofType(α T, U typeSpecifier) → α U
```

#### Subsetting

```text
-- indexer [i]
∀ T, α. [](α T, ?INTEGER) → ?T
∀ T, α. single(α T) → ?T
∀ T, α. first(α T) → ?T
∀ T, α. last(α T) → ?T
∀ T, α. tail(α T) → α T
∀ T, α. skip(α T, ?INTEGER) → α T
∀ T, α. take(α T, ?INTEGER) → α T

-- set operations
∀ K, L. [LUB(K, L) defined] ⇒ intersect(*K, *L) → *K
∀ K, L. [LUB(K, L) defined] ⇒ exclude(*K, *L)   → *K
```

#### Combining

```text
∀ K, L, α, β. [LUB(K, L) defined] ⇒ union(α K, β L) → *LUB(K, L)
∀ K, L, α, β. [LUB(K, L) defined] ⇒ combine(α K, β L) → *LUB(K, L)
```

#### Conversion (explicit)

```text
-- Boolean
∀ X ∈ { BOOLEAN, INTEGER, DECIMAL, STRING }. toBoolean(?X) → ?BOOLEAN
convertsToBoolean(?Any) → ?BOOLEAN

-- Integer / Long (STU)
∀ X ∈ { INTEGER, STRING, BOOLEAN }. toInteger(?X) → ?INTEGER
convertsToInteger(?Any) → ?BOOLEAN
∀ X ∈ { INTEGER, LONG (STU), STRING, BOOLEAN }. toLong(?X) → ?LONG              -- STU
toLong(?LONG) → ?LONG                                                           -- identity (STU)
convertsToLong(?Any) → ?BOOLEAN                                                 -- STU

-- Decimal
∀ X ∈ { INTEGER, DECIMAL, STRING, BOOLEAN }. toDecimal(?X) → ?DECIMAL
convertsToDecimal(?Any) → ?BOOLEAN

-- Date
∀ X ∈ { DATE, DATE_TIME, STRING }. toDate(?X) → ?DATE
convertsToDate(?Any) → ?BOOLEAN

-- DateTime
∀ X ∈ { DATE_TIME, DATE, STRING }. toDateTime(?X) → ?DATE_TIME
convertsToDateTime(?Any) → ?BOOLEAN

-- Time
∀ X ∈ { TIME, STRING }. toTime(?X) → ?TIME
convertsToTime(?Any) → ?BOOLEAN

-- Quantity
∀ X ∈ { INTEGER, DECIMAL, QUANTITY, STRING, BOOLEAN }. toQuantity(?X, [ ?STRING ]) → ?QUANTITY
convertsToQuantity(?Any, [ ?STRING ]) → ?BOOLEAN

-- String
∀ X ∈ { STRING, INTEGER, DECIMAL, DATE, TIME, DATE_TIME, BOOLEAN, QUANTITY }. toString(?X) → ?STRING
convertsToString(?Any) → ?BOOLEAN
```

##### Conditional (iif)

```text
-- three-argument iif
∀ T, M, K, α, b, c ∈ {?, *}. [LUB(M, K) defined] ⇒
  iif(α T, (α T ⇒ ?BOOLEAN), (α T ⇒ b M), (α T ⇒ c K)) → (b ⊔ c) LUB(M, K)

-- two-argument iif (no else branch; else defaults to empty `{}`)
∀ T, M, α, b ∈ {?, *}. iif(α T, (α T ⇒ ?BOOLEAN), (α T ⇒ b M)) → b M
```

#### String manipulation

```text
indexOf(?STRING, ?STRING) → ?INTEGER
lastIndexOf(?STRING, ?STRING) → ?INTEGER
substring(?STRING, ?INTEGER, [ ?INTEGER ]) → ?STRING
startsWith(?STRING, ?STRING) → ?BOOLEAN
endsWith(?STRING, ?STRING) → ?BOOLEAN
contains(?STRING, ?STRING) → ?BOOLEAN
upper(?STRING) → ?STRING
lower(?STRING) → ?STRING
replace(?STRING, ?STRING, ?STRING) → ?STRING
matches(?STRING, ?STRING) → ?BOOLEAN
matchesFull(?STRING, ?STRING) → ?BOOLEAN
replaceMatches(?STRING, ?STRING, ?STRING) → ?STRING
length(?STRING) → ?INTEGER
toChars(?STRING) → *STRING

-- Additional (STU)
encode(?STRING, ?STRING) → ?STRING
decode(?STRING, ?STRING) → ?STRING
escape(?STRING, ?STRING) → ?STRING
unescape(?STRING, ?STRING) → ?STRING
trim(?STRING) → ?STRING
split(?STRING, ?STRING) → *STRING
join(*STRING, [ ?STRING ]) → ?STRING
```

#### Math functions (STU unless noted)

```text
-- Absolute value (all Arithmetic types)
∀ T. [T ∈ Arithmetic] ⇒ abs(?T) → ?T

-- Rounding to integral types
ceiling(?INTEGER) → ?INTEGER
ceiling(?LONG)    → ?LONG
ceiling(?DECIMAL) → ?INTEGER

floor(?INTEGER) → ?INTEGER
floor(?LONG)    → ?LONG
floor(?DECIMAL) → ?INTEGER

truncate(?INTEGER) → ?INTEGER
truncate(?LONG)    → ?LONG
truncate(?DECIMAL) → ?INTEGER

-- Exponential / logarithmic over Numeric
∀ T. [T ∈ Numeric] ⇒ exp(?T) → ?DECIMAL
∀ T. [T ∈ Numeric] ⇒ ln(?T)  → ?DECIMAL
∀ T. [T ∈ Numeric] ⇒ log(?T, ?DECIMAL) → ?DECIMAL

-- Power (operand/result remains within the same Numeric kind)
∀ T. [T ∈ Numeric] ⇒ power(?T, ?T) → ?T

-- Round to precision (result is Decimal)
∀ T. [T ∈ Numeric] ⇒ round(?T, [ ?INTEGER ]) → ?DECIMAL

-- Square root (Decimal result)
∀ T. [T ∈ Numeric] ⇒ sqrt(?T) → ?DECIMAL
```

#### Tree navigation

```text
children(α Any) → *Any
descendants(α Any) → *Any
```

#### Utility

```text
trace(α T, [ (α T ⇒ α U) ]) → α T

-- Current date/time
now() → ?DATE_TIME
timeOfDay() → ?TIME
today() → ?DATE

-- STU
defineVariable(?STRING, [ expr: (α T ⇒ α U) ]) on input α T → α T

-- Boundaries and precision
∀ T. [T ∈ (Temporal ∪ DECIMAL)] lowBoundary(?T, [ ?INTEGER ])   → ?T
∀ T. [T ∈ (Temporal ∪ DECIMAL)] highBoundary(?T, [ ?INTEGER ])  → ?T
∀ T. [T ∈ (Temporal ∪ DECIMAL)] precision(?T) → ?INTEGER

-- Extract Date/DateTime/Time components
yearOf(?TemporalDate)     → ?INTEGER
monthOf(?TemporalDate)    → ?INTEGER
dayOf(?TemporalDate)      → ?INTEGER
hourOf(?Temporal)         → ?INTEGER
minuteOf(?Temporal)       → ?INTEGER
secondOf(?Temporal)       → ?INTEGER
millisecondOf(?Temporal)  → ?INTEGER
timezoneOffsetOf(?DATE_TIME) → ?DECIMAL
dateOf(?TemporalDate)     → ?DATE
timeOf(?DATE_TIME)        → ?TIME
```

### Operators

#### Equality

```text
-- equals
∀ K, L, α, β. [Equatable LUB(K, L), LUB(K, L) defined] ⇒ =(α K, β L) → ?BOOLEAN

-- equivalent
∀ K, L, α, β. [Equivalent LUB(K, L), LUB(K, L) defined] ⇒ ~(α K, β L) → ?BOOLEAN

-- not equals / not equivalent
!=(α K, β L)   ≡ not(=(α K, β L))   → ?BOOLEAN
!~(α K, β L) ≡ not(~(α K, β L)) → ?BOOLEAN
```

Notes:
- For `QUANTITY`, equality/equivalence require compatible dimensions; unit conversion may occur at runtime.
- For `DATE`, `DATE_TIME`, `TIME`, precision rules affect results; types must be convertible via implicit adapters.

#### Comparison

```text
∀ T. [T ∈ Comparable] ⇒ >(?T, ?T) → ?BOOLEAN
∀ T. [T ∈ Comparable] ⇒ <(?T, ?T) → ?BOOLEAN
∀ T. [T ∈ Comparable] ⇒ >=(?T, ?T) → ?BOOLEAN
∀ T. [T ∈ Comparable] ⇒ <=(?T, ?T) → ?BOOLEAN
```

#### Types

```text
is(?Any, typeSpecifier) → ?BOOLEAN
as(?Any, typeSpecifier U) → ?U
-- Function forms (back-compat): is(?Any, U), as(?Any, U)
```

#### Collections (operators)

```text
-- union operator
∀ K, L, α, β. [LUB(K, L) defined] ⇒ (α K) | (β L) → *LUB(K, L)

-- membership
∀ E, C. [LUB(E, C) defined] ⇒ in(?E, *C) → ?BOOLEAN

-- containment
∀ C, E. [LUB(E, C) defined] ⇒ contains(*C, ?E) → ?BOOLEAN
```

#### Boolean logic

```text
and(?BOOLEAN, ?BOOLEAN) → ?BOOLEAN
or(?BOOLEAN,  ?BOOLEAN) → ?BOOLEAN
not(?BOOLEAN) → ?BOOLEAN
xor(?BOOLEAN, ?BOOLEAN) → ?BOOLEAN
implies(?BOOLEAN, ?BOOLEAN) → ?BOOLEAN
```

Note: Operands are first evaluated as Booleans via singleton-evaluation rules; empty propagates with three-valued logic per spec.

#### Math (operators)

Mixed‑type arithmetic is handled via the implicit adapters described above; operators are defined using constrained Numeric/Arithmetic signatures plus String same‑type cases.

```text
-- Numeric/Quantity arithmetic (same‑type via adapters)
∀ T. [T ∈ Arithmetic] ⇒ +(?T, ?T) → ?T
∀ T. [T ∈ Arithmetic] ⇒ -(?T, ?T) → ?T
∀ T. [T ∈ Arithmetic] ⇒ *(?T, ?T) → ?T

-- Numeric division (always Decimal)
∀ A,B. [A,B ∈ Numeric] ⇒ /(?A, ?B) → ?DECIMAL

-- Quantity division
/(?QUANTITY, ?QUANTITY) → ?QUANTITY

-- String concatenation via +
+(?STRING, ?STRING) → ?STRING

-- Integer division (truncated) and modulo
∀ T. [T ∈ Numeric] ⇒ div(?T, ?T) → ?INTEGER
∀ T. [T ∈ Numeric] ⇒ mod(?T, ?T) → ?T
```

#### Date/Time arithmetic

```text
-- addition
∀ T. [T ∈ Temporal] +(?T,?QUANTITY) → ?T

-- subtraction
∀ T. [T ∈ Temporal] -(?T,?QUANTITY) → ?T
```

Rule: For units above seconds, operations use calendar semantics; at seconds and below, definite-duration semantics apply. Using a definite-duration unit above seconds is an error per the FHIRPath spec.



## Assumptions and differences from the FHIRPath spec
- `iif` branch semantics: We assume the true and false branches are lambdas over the input collection shape (per‑collection), i.e., `(α T ⇒ b M)` and `(α T ⇒ c K)`. The FHIRPath specification text is not explicit; this choice aligns with collection‑centric semantics and makes cardinality and typing predictable.


## Unclear or specification-dependent items

- `repeat`: projection result type is assumed to be `α T` (same element type) for type safety; in practice, projection may change element types. If so, generalize to `repeat(α T, (?T ⇒ α U)) → α U` with `Equatable U`.
- Equals/equivalent on complex types: full structural equality/equivalence is runtime-defined; we model via `Equatable/Equivalent` predicates.
- QUANTITY operations: dimensional analysis (unit exponents, compatibility) is runtime; types capture only `QUANTITY`.
- Date/Time precision and timezone behavior affect equality/comparison results; signatures assume implicit adapters handle `DATE→DATE_TIME` as needed.
- `type()` (reflection) returns `TYPEINFO` meta-objects; not part of the core value type lattice.
- STU items (Long, matchesFull, math functions, additional string functions, defineVariable, reflection) are optional; gate by feature flags.
- Boolean short-circuiting is not required by the spec; math/boolean operator semantics do not imply evaluation order.
