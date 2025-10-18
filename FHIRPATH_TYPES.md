# FHIRPath Operator and Function Signatures (Element‑first)

This document defines static signatures for FHIRPath operators and functions using the element‑first shape model and Haskell‑style constraints. It mirrors the organization of the FHIRPath specification and notes any ambiguities or items marked STU (trial use) in the spec.

## Notation

- Shapes (cardinality + element type): `?T` = Single[T] (0..1), `*T` = Many[T] (0..*).
- Lambdas: `(S_in ⇒ S_out)` where `S` are shapes (`?T` or `*T`).
- Constraints: `[ … ] ⇒ sig` precede the arrow; we use named predicates like `Comparable T`.
- LUB: `LUB(T, U)` is least upper bound in the element-type lattice; must exist when stated.
- Bottom/Empty: `{}` has principal type `?⊥` (Nothing); `LUB(T, ⊥) = T`.
- Adapters follow the implicit conversions in the spec: `INTEGER→DECIMAL`, `INTEGER→LONG` (STU), `LONG→DECIMAL` (STU), `INTEGER→QUANTITY`, `DECIMAL→QUANTITY`, `DATE→DATE_TIME`.

## Type sets and predicates

```text
Numeric        = { INTEGER, LONG (STU), DECIMAL }
Arithmetic     = Numeric ∪ { QUANTITY }
TemporalDate   = { DATE, DATE_TIME }
TemporalTime   = { TIME }
Temporal       = TemporalDate ∪ TemporalTime
Comparable     = { STRING, INTEGER, DECIMAL, QUANTITY, DATE, DATE_TIME, TIME }
StringLike     = { STRING }
BooleanLike    = { BOOLEAN }
Equatable T    = predicate: T supports equality (=) with implicit conversions per spec
Equivalent T   = predicate: T supports equivalence (~) semantics per spec
```

Note: `QUANTITY` comparability/equality depends on dimensional compatibility (units); this is a runtime check.

---

## Functions

### Existence

```text
∀ T, α. empty(α T) → ?BOOLEAN
∀ T, α. exists(α T, [ (?T ⇒ ?BOOLEAN) ]) → ?BOOLEAN
∀ T, α. all(α T, (?T ⇒ ?BOOLEAN)) → ?BOOLEAN
∀ α.    allTrue(α BOOLEAN) → ?BOOLEAN
∀ α.    anyTrue(α BOOLEAN) → ?BOOLEAN
∀ α.    allFalse(α BOOLEAN) → ?BOOLEAN
∀ α.    anyFalse(α BOOLEAN) → ?BOOLEAN
∀ T.    subsetOf(*T, *T) → ?BOOLEAN          -- uses equals semantics
∀ T.    supersetOf(*T, *T) → ?BOOLEAN        -- uses equals semantics
∀ T, α. count(α T) → ?INTEGER
∀ T, α. distinct(α T) → α T                  -- removes duplicates by equals semantics
∀ T, α. isDistinct(α T) → ?BOOLEAN
```

### Filtering and projection

```text
∀ T, α. where(α T, (?T ⇒ ?BOOLEAN)) → α T
∀ T, U, α. select(α T, (?T ⇒ ?U)) → α U
∀ T, α. repeat(α T, (?T ⇒ α T)) → α T        -- assumes projection returns same element type; see notes
∀ T, U, α. ofType(α T, U typeSpecifier) → α U
```

### Subsetting

```text
∀ T, α. index(α T, ?INTEGER) → ?T            -- path indexer [i]
∀ T, α. single(α T) → ?T                     -- error if input has >1 element
∀ T, α. first(α T) → ?T
∀ T, α. last(α T) → ?T
∀ T, α. tail(α T) → *T
∀ T, α. skip(α T, ?INTEGER) → α T
∀ T, α. take(α T, ?INTEGER) → α T
-- set operations in Subsetting
∀ K, L. [LUB(K, L) defined] ⇒ intersect(*K, *L) → *K   -- returns elements from the left that are in right (by equals)
∀ K, L. [LUB(K, L) defined] ⇒ exclude(*K, *L)   → *K   -- returns elements from the left that are not in right (by equals)
```

### Combining

```text
∀ K, L, α, β. [LUB(K, L) defined] ⇒ union(α K, β L) → *LUB(K, L)
∀ K, L, α, β. [LUB(K, L) defined] ⇒ combine(α K, β L) → *LUB(K, L)     -- preserves duplicates
```

### Conversion (implicit and explicit)

Implicit conversions are applied by the adapter engine; these functions are explicit.

```text
-- Boolean
∀ X ∈ { BOOLEAN, INTEGER, DECIMAL, STRING }. toBoolean(?X) → ?BOOLEAN
convertsToBoolean(?Any) → ?BOOLEAN

-- Integer / Long (STU)
∀ X ∈ { INTEGER, STRING, BOOLEAN }. toInteger(?X) → ?INTEGER
convertsToInteger(?Any) → ?BOOLEAN
(toLong, convertsToLong)  -- STU: analogous to Integer, returning LONG

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

#### Conditional (iif)

We model `iif` using per‑collection lambdas over the input shape (spec text is not explicit):

```text
-- three-argument iif
∀ T, M, K, α, b, c ∈ {?, *}. [LUB(M, K) defined] ⇒
  iif(α T, (α T ⇒ ?BOOLEAN), (α T ⇒ b M), (α T ⇒ c K)) → (b ⊔ c) LUB(M, K)

-- two-argument iif (no else branch; else defaults to empty `{}`)
∀ T, M, α, b ∈ {?, *}. iif(α T, (α T ⇒ ?BOOLEAN), (α T ⇒ b M)) → b M
```

### String manipulation

```text
indexOf(?STRING, ?STRING) → ?INTEGER
lastIndexOf(?STRING, ?STRING) → ?INTEGER       -- STU
-- substring requires a singleton input (spec errors on multi-item input)
substring(?STRING, ?INTEGER, [ ?INTEGER ]) → ?STRING
startsWith(?STRING, ?STRING) → ?BOOLEAN
endsWith(?STRING, ?STRING) → ?BOOLEAN
contains_str(?STRING, ?STRING) → ?BOOLEAN      -- function form, distinct from collection operator
upper(?STRING) → ?STRING
lower(?STRING) → ?STRING
replace(?STRING, ?STRING, ?STRING) → ?STRING
matches(?STRING, ?STRING) → ?BOOLEAN
matchesFull(?STRING, ?STRING) → ?BOOLEAN       -- STU
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

### Math functions (STU unless noted)

```text
abs(?INTEGER) → ?INTEGER
abs(?DECIMAL) → ?DECIMAL
abs(?QUANTITY) → ?QUANTITY
ceiling(?INTEGER) → ?INTEGER
ceiling(?DECIMAL) → ?INTEGER
exp(?INTEGER) → ?DECIMAL
exp(?DECIMAL) → ?DECIMAL
floor(?INTEGER) → ?INTEGER
floor(?DECIMAL) → ?INTEGER
ln(?INTEGER) → ?DECIMAL
ln(?DECIMAL) → ?DECIMAL
log(?INTEGER, ?DECIMAL) → ?DECIMAL
log(?DECIMAL,  ?DECIMAL) → ?DECIMAL
-- power overloads; mixed cases use implicit INTEGER→DECIMAL
power(?INTEGER, ?INTEGER) → ?INTEGER
power(?DECIMAL, ?DECIMAL) → ?DECIMAL
round(?INTEGER, [ ?INTEGER ]) → ?DECIMAL
round(?DECIMAL, [ ?INTEGER ]) → ?DECIMAL
sqrt(?INTEGER) → ?DECIMAL
sqrt(?DECIMAL) → ?DECIMAL
truncate(?INTEGER) → ?INTEGER
truncate(?DECIMAL) → ?INTEGER
```

### Tree navigation

```text
children(α Any) → *Any
descendants(α Any) → *Any
```

### Utility

```text
trace(α T, [ (α T ⇒ α U) ]) → α T

-- Current date/time
now() → ?DATE_TIME
timeOfDay() → ?TIME
today() → ?DATE

-- STU
defineVariable(?STRING, [ expr: (α T ⇒ α U) ]) on input α T → α T

-- Boundaries and precision
lowBoundary(?DECIMAL, [ ?INTEGER ])   → ?DECIMAL
lowBoundary(?DATE,    [ ?INTEGER ])   → ?DATE
lowBoundary(?DATE_TIME,[ ?INTEGER ])  → ?DATE_TIME
lowBoundary(?TIME,    [ ?INTEGER ])   → ?TIME
highBoundary(?DECIMAL, [ ?INTEGER ])  → ?DECIMAL
highBoundary(?DATE,    [ ?INTEGER ])  → ?DATE
highBoundary(?DATE_TIME,[ ?INTEGER ]) → ?DATE_TIME
highBoundary(?TIME,    [ ?INTEGER ])  → ?TIME
precision(?DECIMAL) → ?INTEGER
precision(?DATE)    → ?INTEGER
precision(?DATE_TIME) → ?INTEGER
precision(?TIME)    → ?INTEGER

-- Extract Date/DateTime/Time components
yearOf( ?DATE | ?DATE_TIME ) → ?INTEGER
monthOf(?DATE | ?DATE_TIME ) → ?INTEGER
dayOf(  ?DATE | ?DATE_TIME ) → ?INTEGER
hourOf( ?DATE | ?DATE_TIME | ?TIME ) → ?INTEGER
minuteOf(?DATE | ?DATE_TIME | ?TIME ) → ?INTEGER
secondOf(?DATE | ?DATE_TIME | ?TIME ) → ?INTEGER
millisecondOf(?DATE | ?DATE_TIME | ?TIME ) → ?INTEGER
timezoneOffsetOf(?DATE_TIME) → ?DECIMAL
dateOf( ?DATE | ?DATE_TIME ) → ?DATE
timeOf(?DATE_TIME) → ?TIME
```

### Reflection (STU)

```text
type(α Any) → α TYPEINFO
```

---

## Operators

### Equality

```text
-- equals
∀ K, L, α, β. [Equatable LUB(K, L), LUB(K, L) defined] ⇒ equals(α K, β L) → ?BOOLEAN

-- equivalent
∀ K, L, α, β. [Equivalent LUB(K, L), LUB(K, L) defined] ⇒ equivalent(α K, β L) → ?BOOLEAN

-- not equals / not equivalent
notEquals(α K, β L)   ≡ not(equals(α K, β L))   → ?BOOLEAN
notEquivalent(α K, β L) ≡ not(equivalent(α K, β L)) → ?BOOLEAN
```

Notes:
- For `QUANTITY`, equality/equivalence require compatible dimensions; unit conversion may occur at runtime.
- For `DATE`, `DATE_TIME`, `TIME`, precision rules affect results; types must be convertible via implicit adapters.

### Comparison

```text
∀ T, α, β. [T ∈ Comparable] ⇒ gt(?T, ?T) → ?BOOLEAN
∀ T, α, β. [T ∈ Comparable] ⇒ lt(?T, ?T) → ?BOOLEAN
∀ T, α, β. [T ∈ Comparable] ⇒ ge(?T, ?T) → ?BOOLEAN
∀ T, α, β. [T ∈ Comparable] ⇒ le(?T, ?T) → ?BOOLEAN
```

### Types

```text
is(?Any, typeSpecifier) → ?BOOLEAN
as(?Any, typeSpecifier U) → ?U
-- Function forms (back-compat): is(?Any, U), as(?Any, U)
```

### Collections (operators)

```text
-- union operator
∀ K, L, α, β. [LUB(K, L) defined] ⇒ (α K) | (β L) → *LUB(K, L)

-- membership
∀ E, C. [LUB(E, C) defined] ⇒ in(?E, *C) → ?BOOLEAN

-- containment
∀ C, E. [LUB(E, C) defined] ⇒ contains(*C, ?E) → ?BOOLEAN
```

### Boolean logic

```text
and(?BOOLEAN, ?BOOLEAN) → ?BOOLEAN
or(?BOOLEAN,  ?BOOLEAN) → ?BOOLEAN
not(?BOOLEAN) → ?BOOLEAN
xor(?BOOLEAN, ?BOOLEAN) → ?BOOLEAN
implies(?BOOLEAN, ?BOOLEAN) → ?BOOLEAN
```

Note: Operands are first evaluated as Booleans via singleton-evaluation rules; empty propagates with three-valued logic per spec.

### Math (operators)

```text
-- multiplication
*(?INTEGER, ?INTEGER) → ?INTEGER
*(?DECIMAL, ?INTEGER) → ?DECIMAL
*(?INTEGER, ?DECIMAL) → ?DECIMAL
*(?DECIMAL, ?DECIMAL) → ?DECIMAL
*(?QUANTITY, ?QUANTITY) → ?QUANTITY      -- dimensional exponent arithmetic at runtime

-- division
/(?INTEGER, ?INTEGER) → ?DECIMAL
/(?DECIMAL, ?INTEGER) → ?DECIMAL
/(?INTEGER, ?DECIMAL) → ?DECIMAL
/(?DECIMAL, ?DECIMAL) → ?DECIMAL
/(?QUANTITY, ?QUANTITY) → ?QUANTITY      -- dimensional exponent arithmetic at runtime

-- addition
+(?INTEGER, ?INTEGER) → ?INTEGER
+(?DECIMAL, ?INTEGER) → ?DECIMAL
+(?INTEGER, ?DECIMAL) → ?DECIMAL
+(?DECIMAL, ?DECIMAL) → ?DECIMAL
+(?QUANTITY, ?QUANTITY) → ?QUANTITY      -- compatible dimensions required
+(?STRING, ?STRING)   → ?STRING          -- differs from & in empty handling

-- subtraction
-(?INTEGER, ?INTEGER) → ?INTEGER
-(?DECIMAL, ?INTEGER) → ?DECIMAL
-(?INTEGER, ?DECIMAL) → ?DECIMAL
-(?DECIMAL, ?DECIMAL) → ?DECIMAL
-(?QUANTITY, ?QUANTITY) → ?QUANTITY      -- compatible dimensions required

-- integer division
div(?INTEGER, ?INTEGER) → ?INTEGER
div(?DECIMAL, ?DECIMAL) → ?INTEGER

-- modulo
mod(?INTEGER, ?INTEGER) → ?INTEGER
mod(?DECIMAL, ?DECIMAL) → ?DECIMAL

-- string concatenation
&(?STRING, ?STRING) → ?STRING
```

### Date/Time arithmetic

```text
-- addition
+(?DATE,       ?QUANTITY[calendar]) → ?DATE
+(?DATE_TIME,  ?QUANTITY[calendar|definite]) → ?DATE_TIME
+(?TIME,       ?QUANTITY[definite ≤ seconds]) → ?TIME

-- subtraction
-(?DATE,       ?QUANTITY[calendar]) → ?DATE
-(?DATE_TIME,  ?QUANTITY[calendar|definite]) → ?DATE_TIME
-(?TIME,       ?QUANTITY[definite ≤ seconds]) → ?TIME
```

Notes:
- Calendar vs definite duration semantics follow the spec; units above seconds with definite durations are errors for date/time arithmetic.

---

## Aggregates (STU)

```text
-- aggregator uses $this, $index, and $total; behaves like a fold
∀ T, U. aggregate(*T, aggregator: (?$this:T, ?$total:U, ?$index:INTEGER) ⇒ ?U, [ init: ?U ]) → ?U
```

Implementation note: Typing of the aggregator lambda is binary (`T × U → U`); `$index` is available as `?INTEGER`.

---

## Unclear or specification-dependent items

- repeat: projection result type is assumed to be `α T` (same element type) for type safety; in practice, projection may change element types. If so, generalize signature to `repeat(α T, (?T ⇒ α U)) → α U` with `Equatable U`.
- equals/equivalent on complex types: full structural equality/equivalence is runtime-defined; we model via `Equatable/Equivalent` predicates.
- QUANTITY operations: dimensional analysis (unit exponents, compatibility) is runtime; types capture only `QUANTITY`.
- Date/Time precision and timezone behavior affect equality/comparison results; signatures assume implicit adapters handle `DATE→DATE_TIME` as needed.
- `type()` (reflection) returns `TYPEINFO` meta-objects; not part of the core value type lattice.
- STU items (Long, matchesFull, math functions, additional string functions, defineVariable, reflection) are optional; gate by feature flags.
- Boolean short-circuiting is not required by the spec; our signatures do not impose evaluation order.
- iif: Spec defines `iif(criterion: expression, true-result: collection [, otherwise-result: collection])`. We model these as per‑collection lambdas over the input shape for static typing. Review needed if a different evaluation context is desired.

---

Generated: 2025‑10‑18
