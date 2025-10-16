# Type System

Assumptions:
- there is never a need to define a wildcard type that distinguishes between 
  a single element of a type and a collection of the type and thus one wildcard (ANY) 
  can be used to match both.


## Type Hierarchy

```
Type
├── OrdinaryType (non-functional types)
│   ├── ValueType (non-collection types)
│   │   ├── PrimitiveType: STRING, BOOLEAN, INTEGER, DECIMAL, DATE, DATE_TIME, TIME, QUANTITY
│   │   ├── ComplexType (structured types with fields)
│   │   ├── ResourceType (extends ComplexType with resource semantics)
│   │   └── FhirType (FHIR-specific wrapper types)
│   ├── CollectionType[ValueType|ANY] (written as [T])
│   └── NULL (empty collection / bottom type)
├── Lambda[OrdinaryType|ANY] (functional type with return type)
└── ANY (wildcard type matches anh OrdinaryType)
```

### Type Categories

- **ValueType**: Non-collection types (primitives, complex types, ANY wildcard)
- **CollectionType**: Homogeneous collections `[T]` where `T` is a ValueType
- **OrdinaryType**: Union of ValueType, CollectionType, and NULL (all non-functional types)
- **Lambda[T]**: Functional type with return type T (where T is OrdinaryType)
- **NULL**: Special empty/bottom type representing `{}` (empty collection)
- **ANY**: Wildcard that matches any DefinedType (not NULL or Lambda)

### Important Properties

1. **Collection Homogeneity**: Collections can only contain ValueType (not collections or lambdas)
2. **No Nested Collections**: `[[T]]` is not a valid type (collections are automatically flattened)
3. **NULL is Polymorphic**: NULL adapts to any OrdinaryType with cost 0
4. **Singleton Promotion**: Per FHIRPath spec, `T` can be used where `[T]` is expected (free promotion)

## Adaptation Rules

### Cost Semantics

The `cost` is used **additively** in multi-argument functions to determine overload resolution:
- **cost = 0**: Free adaptations (no runtime conversion needed)
- **cost = 1**: Requires a runtime cast/conversion
- **cost = ∞**: Adaptation impossible (type error)

**Lower total cost wins** in overload resolution.

### Key Insight: Element Promotion is Free

Per FHIRPath semantics, `T` can be used in place of `[T]` (singleton promotion) **with cost 0**.
This is a free adaptation that doesn't require a cast node in the IR.

**Example**:
```
Given actual arguments: (INTEGER, INTEGER)
Signature candidates:
  1. (INTEGER, DECIMAL)     → cost = 0 + 1 = 1 (second arg needs cast)
  2. ([INTEGER], [INTEGER]) → cost = 0 + 0 = 0 (both use free singleton promotion)

Result: Signature 2 is selected (lower cost)
```

**Signature Equivalence**: `(INTEGER, INTEGER)` and `([INTEGER], [INTEGER])` are functionally equivalent.
If both exist (which should be avoided), the first defined wins.

---

## Adaptation Rule Definitions

X ⇒ Y = Z means that type X can be adapted to type A actual type for the IRNode is Z.
The actual type cannot be a wildcard type that is ANY, CollectionType[ANY] or Lambda[ANY].

### Identity (cost: 0)

Every type adapts to itself with zero cost.

```
A ⇒ A  = A for all A in Type
```

---

### Wildcard Matching (cost: 0)

ANY matches any OrdinaryType

```
T ⇒ ANY = T  for all T in OrdinaryType
```

**Examples**:
- `INTEGER ⇒ ANY`
- `ComplexType ⇒ ANY`
- `FhirType ⇒ ANY`

### NULL Polymorphism (cost: 0)

NULL is a bottom type that adapts to any ordinary type with zero cost.

```
NULL ⇒ T = T for all T in (OrdinaryType)
```

**Examples**:
- `NULL ⇒ INTEGER`
- `NULL ⇒ [STRING]`
- `NULL ⇒ ComplexType`


---

### Element Promotion (cost: 0)

Per FHIRPath spec, a value can be used where a collection is expected (singleton promotion).
This is a **free** adaptation.

```
A ⇒ [B] = C if A ⇒ B = C  (cost: cost(A ⇒ B))
```

**Examples**:
- `INTEGER ⇒ [INTEGER]` (cost: 0, free singleton)
- `INTEGER ⇒ [DECIMAL]` (cost: 1, requires cast then singleton)
- `STRING ⇒ [ANY]` (cost: 0, wildcard + singleton, both free)

**Rationale**: In FHIRPath, scalars are implicitly singleton collections. This allows functions
defined on collections to work seamlessly with scalar arguments.

### Lambda Covariance (cost: cost(A ⇒ B))

Lambda types are covariant in their return type.

```
Lambda[A] ⇒ Lambda[B] = Lambda[C]  if A ⇒ B = C  (cost: cost(A ⇒ B))
```

**Examples**:
- `Lambda[INTEGER] ⇒ Lambda[DECIMAL]` (cost: 1)
- `Lambda[STRING] ⇒ Lambda[ANY]` (cost: 0)
- `Lambda[[INTEGER]] ⇒ Lambda[[DECIMAL]]` (cost: 1, via array covariance)

**Rationale**: A lambda producing a more specific return type can be used where a more general
return type is expected (standard covariance).

---

### Cast: Primitive Implicit Casts (cost: 1)

Widening conversions between primitive types require a runtime cast.

```
INTEGER → DECIMAL
DECIMAL → QUANTITY
DATE → DATE_TIME
```

**General rule**:
```
If A → B (transtive cast exists), then A ⇒ B with cost 1
```

**Examples**:
- `INTEGER ⇒ DECIMAL` (cost: 1)
- `DATE ⇒ DATE_TIME` (cost: 1)

**Note**: These casts are transitive: `INTEGER → DECIMAL → QUANTITY` means:
- `INTEGER ⇒ QUANTITY` (cost: 1, via DECIMAL)
---

### Cast: FHIR System Cast (cost: 1)

FHIR types unwrap to their system types, requiring a runtime conversion.

```
FhirType ⇒ T  if systemType(FhirType) ⇒ T
  cost: 1 + cost(systemType(FhirType) ⇒ T)
```

**Examples**:
- `FhirType(STRING) ⇒ STRING` (cost: 1 + 0 = 1)
- `FhirType(INTEGER) ⇒ DECIMAL` (cost: 1 + 1 = 2, unwrap + cast)
- `FhirType(INTEGER) ⇒ [DECIMAL]` (cost: 1 + 1 + 0 = 2, unwrap + cast + free promotion)

**Rationale**: FHIR types wrap system types and require explicit conversion (getValue()).

---

### No Cross-Category Adaptation (cost: ∞)

Lambda types and OrdinaryTypes are fundamentally incompatible.

```
Lambda[T] ⇏ OrdinaryType  (functions cannot become data)
OrdinaryType ⇏ Lambda[T]  (data cannot become functions)
If A → B (transtive cast exists), then A ⇒ B with cost 1

**These adaptations always fail**, causing type errors during overload resolution.

- `INTEGER ⇒ DECIMAL` (cost: 1)
- `DATE ⇒ DATE_TIME` (cost: 1)

**Note**: These casts are transitive: `INTEGER → DECIMAL → QUANTITY` means:
- `INTEGER ⇒ QUANTITY` (cost: 1, via DECIMAL)
| **Identity** | `T ⇒ T` | 0 |
| **NULL Polymorphism** | `NULL ⇒ T` | 0 |
| **Wildcard Matching** | `T ⇒ ANY`, `[T] ⇒ [ANY]` | 0 |
| **Element Promotion (exact)** | `T ⇒ [T]` | 0 |
| **Element Promotion (cast)** | `INTEGER ⇒ [DECIMAL]` | 1 |
| **Primitive Cast** | `INTEGER ⇒ DECIMAL` | 1 |
| **FHIR Unwrap** | `FhirType(T) ⇒ T` | 1 |
| **Transitive Cast** | `INTEGER ⇒ QUANTITY` | 2 |
| **FHIR Unwrap + Cast** | `FhirType(INTEGER) ⇒ DECIMAL` | 2 |
| **Cross-Category** | `Lambda[T] ⇔ T` | ∞ |

---

## Type Resolution Algorithm

When resolving overloaded function calls:

1. **Filter by arity**: Eliminate signatures that don't match the argument count
2. **Attempt adaptation**: For each remaining signature:
   - Try to adapt each argument to the corresponding parameter type
   - If any adaptation fails (cost = ∞), reject this signature
   - Otherwise, sum the adaptation costs: `total_cost = Σ cost(arg_i ⇒ param_i)`
3. **Select best match**: Choose the signature with minimum total cost
4. **Handle ambiguity**: If multiple signatures have the same minimum cost, reject as ambiguous
   - This should not happen with well-designed function registries
5. **Type error**: If no signature succeeds, report a type error

---

## Design Principles

1. **Zero-cost abstractions**: Common patterns (singleton promotion, wildcards) have cost 0
2. **Explicit casts**: Runtime conversions always have cost ≥ 1
3. **Additive costs**: Multi-step adaptations sum their individual costs
4. **Prefer specificity**: More specific types are preferred (lower cost) over general ones
5. **Type safety**: Cross-category adaptations are impossible (cost ∞)
6. **FHIRPath compatibility**: Singleton promotion is free to match FHIRPath semantics
