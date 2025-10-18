# Type System (Element‑first)

Goal: Provide a simple, precise, and implementable static type model for `FHIRPath→IR→SparkSQL` that tracks element type and cardinality, supports implicit adaptations, and enables concise polymorphic operator/function signatures.

## Core model

- Shape (cardinality + element type):
  - `?T` = `Single[T] (0..1)`
  - `*T` = `Many[T] (0..*)`
- Element types (non-collection):
  - Primitives: `INTEGER`, `DECIMAL`, `STRING`, `BOOLEAN`, `DATE`, `DATE_TIME`, `TIME`, …
  - Structured (nominal): `HumanName`, `Address`, `Patient`, `Resource`, …
  - FHIR wrappers: `Fhir[T]` (e.g., `Fhir[STRING]`, `Fhir[HumanName]`)
- Top/Bottom:
  - `Any` = top element type
  - `⊥` (Nothing) = bottom element type
  - Subtyping: `⊥ <: T <: Any`
  - LUB: `LUB(T, ⊥) = T` (LUB defined only if an upper bound exists in the lattice)
- Empty literal:
  - `{}` : `?⊥` (principal type)
  - Zero‑cost arity swap for bottom: `?⊥ ⇄ *⊥` (matching convenience only)
- Lambdas:
  - `Lambda[S_in ⇒ S_out]`, where `S` are shapes (``?T`` or ``*T``)

Notes:
- We intentionally model arity explicitly even though FHIRPath treats all values as collections; this aids IR and codegen decisions.
- Prefer element-top `Any` and arity variables over a conflated ANY‑of‑any‑shape.

## Implicit adaptations (adapters) and cost

Adapters enable type‑checking and overload resolution via least‑cost plans (multi‑step allowed; costs are additive):
- Numeric widening: `INTEGER → DECIMAL` (cost 1)
- FHIR value extraction: `Fhir[Prim] → Prim` (cost 1) for FHIR types that support conversion to FHIRPath system primitive types (to be defined per FHIR spec)
- Optional (if enabled): domain‑specific adapters (e.g., `Quantity → DECIMAL`) with declared cost
- Arity: only for bottom: `?⊥ ⇄ *⊥` (cost 0) to allow `{}` to match either shape

If multiple matches tie on minimal cost, treat as ambiguity (error) unless a deterministic tiebreak is defined.

## Signature notation (Haskell‑style)

- Quantification: `∀ T, U, K, L` (element types), `α, β ∈ {?, *}` (arity variables)
- Constraints: `[ … ] ⇒ …`
- Shapes in arguments/results: `α T` means a shape (``?T`` or ``*T``)
- Lambdas: `[S1 ⇒ S2]`

Examples use FHIRPath names; equality is written `equals(…)` for clarity.

## Core signatures

```text
-- first
∀ T, α. first(α T) → ?T

-- where (per‑element predicate)
∀ T, α. where(α T, [?T ⇒ ?BOOLEAN]) → α T

-- select/map (per‑element)
∀ T, U, α. select(α T, [?T ⇒ ?U]) → α U

-- union
∀ K, L, α, β. [LUB(K, L) defined] ⇒ union(α K, β L) → *LUB(K, L)

-- in
∀ E, C. [LUB(E, C) defined] ⇒ in(?E, *C) → ?BOOLEAN

-- contains
∀ C, E. [LUB(E, C) defined] ⇒ contains(*C, ?E) → ?BOOLEAN

-- equals (and notEquals)
∀ K, L, α, β. [LUB(K, L) defined] ⇒ equals(α K, β L) → ?BOOLEAN

-- arithmetic (example: plus)
∀ M, N. [Arithmetic M, Arithmetic N, P = LUB(M, N)] ⇒ plus(?M, ?N) → ?P

-- iif (per‑collection variant; per‑element is analogous with ?T in lambdas)
∀ T, M, K, α. iif(α T, [α T ⇒ ?BOOLEAN], [α T ⇒ α M], [α T ⇒ α K]) → α LUB(M, K)
```

## Laws and examples

```text
{} : ?⊥
first({}) : ?⊥

-- first
*T.first() ⇒ ?T
?T.first() ⇒ ?T  -- no duplication needed

-- in / contains
?INTEGER in *DECIMAL ⇒ ?BOOLEAN    -- via INTEGER → DECIMAL
contains(*STRING, ?INTEGER) ⇒ error -- no LUB

-- union
*INTEGER ∪ *DECIMAL ⇒ *DECIMAL
?INTEGER ∪ ?DECIMAL ⇒ *DECIMAL
{} ∪ *DECIMAL ⇒ *DECIMAL           -- use ?⊥ → *⊥ for matching

-- where/select (arity‑preserving)
*T.where([?T ⇒ ?BOOLEAN]) ⇒ *T
?T.select([?T ⇒ ?U]) ⇒ ?U

-- equality
*K = *L ⇒ ?BOOLEAN if LUB(K, L) exists; else error

-- arithmetic
?INTEGER + ?DECIMAL ⇒ ?DECIMAL
?STRING  + ?DECIMAL ⇒ error        -- no LUB
```

Optional refinement: For strict operators (e.g., arithmetic, comparisons), if an operand is statically `?⊥`, the result may be typed as `?⊥` to reflect “empty‑in ⇒ empty‑out”. This is an optimization of static precision, not required for correctness.

## FHIR considerations

- Treat FHIR value extraction as an adapter: `Fhir[Prim] → Prim` (cost 1). Do not model it as subtyping to avoid unsoundness.
- Structured/resource hierarchies can be nominal (e.g., `Patient <: Resource`); define LUBs where needed. You do not need to enumerate all FHIR types—register them in a type registry and define edges as required.

## Implementation notes (resolver/IR)

- Overload resolution binds `(T, U, …)` and `(α, β)` with adapters; compute LUBs; pick least‑cost plan.
- Cardinality derives from signatures directly (e.g., `first ⇒ ?`, `where/select ⇒ preserve`, `union ⇒ *`).
- IR nodes should carry element type and cardinality; adapters become explicit IR steps (e.g., `Fhir[String] → String`, `Integer → Decimal`).

## Out of scope (for now)

- FHIR polymorphic `value[x]`
- Broad implicit stringification or cross‑domain casts not explicitly declared as adapters
