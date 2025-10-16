# Type System

## Type Hierarchy

Type:

- OrdinaryType:
  - ValueType:
    - DefinedType
      - STRING, BOOLEAN, INTEGER, DECIMAL, DATE, DATE_TIME, TIME, QUANTITY # Primitive types
      - ComplexType
      - ResourceType
      - FhirType
    - ANY # Wildcard type
  - [ValueType] # Collection of ValueType
  - NULL # Null type
- Lambda[OrdinaryType]

## Adaptation Rules

Please note that the `cost` is used additively in multi-argument functions to determine how many casts are needed
to match the argument types to the parameter types. Lower cost is better.
The 'cost' does not represent the adaptations needed but rather if actual type casts are needed, as some
adaptations have zero cost (are free).
This maybe somewhat confusing but in this type system (as per fhirpath spec)
T can be always used in place of [T] (effectively making T a collection of one item) but the reverse is not true.

So for example:

with actual types (INTEGER, INTEGER) cost of adapting to:

- ([INTEGER], [INTEGER]) is 0 (0+0)
- (INTEGER, DECIMAL) is 1 (0+1)

- so the former is preferred (as does not require a cast) over the latter (which does require a cast)

From the signatures (INTEGER, INTEGER) and ([INTEGER], [INTEGER]) are equivalent and if both are present
(which should not be the case) the one defined first is used.

### Identity (cost: 0)

A => A for all A in Type

### NULL Polymorphism (cost: 0)

NULL ⇒ T for all T in OrdinaryType

### Wildcards (cost: 0)

T ⇒ ANY for all T in ValueType

### Element Promotion (cost: cost(A ⇒ B))

A ⇒ [B] if A ⇒ B

### Array Covariance (cost: cost(A ⇒ B))

[A] ⇒ [B] if A ⇒ B

### Lambda Element Covariance (cost: cost(A ⇒ B))

Lambda[A] ⇒ Lambda[B] if A ⇒ B

### Cast: Primitive Implicit Casts (cost: 1)

INTEGER → DECIMAL → QUANTITY
DATE → DATE_TIME
A ⇒ if A → B

### Cast: FHIR System cast (cost: 1)

FHIRType => T if systemType(FHRIType) => T


