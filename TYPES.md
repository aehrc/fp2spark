Fhirpath normally not a strongly typed language, but it does have a type system that can be used to classify values.
All functions and operators are defined on untypes collections of values,
where each value has a type, but they may differ in the collection.
Functions and operation may define constaints on both the type the arrity of the collection and the
types of the expected values in the collection, but these normally are checked at runtime.

For the purpose of this project (FHIPath to SparkSQL) we want to introduce a static type system
that can be used to check the types and arity of all expressions at compile-time.

For this purpose we need to introduce a concept of typed collection that is a collection with a specific,
and a single value of the type. Also to represent and empty collection we need a concept of empty collection type.

The type system should be able to express the following concepts:

- Primitive types (INTEGER, DECIMAL, STRING, BOOLEAN, DATE, DATE_TIME, TIME, etc).
- Complex types (ComplexTypes and Resources)
- FHIR types and resources (e.g., FHIR(integer), FHIR(HumanName), etc),
- Arity tracking (e.g., singleton vs collection)
- Nullability (NULL type, and nullable types)
- Implicit conversions (e.g., INTEGER to DECIMAL, singleton promotion)
- Function and operator signatures (input and output types)
- Lambda types (Lambda[T], where T is the return type)

It also:

- should be able to express the concept of empty collection type {}
- should preserve minmize the arity of expressions where possible
  e.g., the result of "where" should be singleton if the input collection is singleton

Notes:

- It does not need to a formal "type system" but just a design that can be implemented in Java code.
- If it makes it simpler we can only distinguish between singleton ([0..1]) and collection [[0..*]] arity.
- It's important that the arity is correctly set in IR nodes, as the runtime may use different
  representations for singleton vs collection values (e,g, SparkSQL Column vs Array[Column])
  and select the translation accordingly.

It should allow to:

- statically chek the types and arity of all expressions at compile-time
- determine the resulting type and arity of expressions
- allow to define overload function and operator signatures with type and arity constraints that can be checked
  statically
- when checking/resolving expression type by able to apply implicit conversions where needed
- support labda expressions with type and arity tracking (for functions like where, select, etc.)
- support empty collection literal {} (possibly with the NULL type)
- support both FHIRPpath implicit casts and the FHIR implicit getValue().
- support mulit-step conversion paths where needed (e.g., FHIR(integer) ⇒ INTEGER ⇒ DECIMAL)

We want:

- the design to be elegant and as simple as possible and easily expressible in Java code
- in particular the definition of function and operator signatures should be self-documenting and not too verbose
- using the principle of making the common case simple and the complex case possible e.g.: 
  weighting extending type system with type variables and parametric polymorphism vs allowing
      custom Java function type resolvers classes for some exotic signatures
- easy to integrate with the existing codebase and expression analyzer and IR generation

Some edge cases to consider (not an exhaustive list):

- overloaded singleton operators with limited numer of types (e.g., +, -,  <, > )
- overloaded collection operators (e.g., union)
- element extractors (e.g., first()))
- equality operators (e.g., =, !=)
- functions with per element lambda arguments (e.g., where, select)
- function with collection lambda arguments (e.g.,iif)
- function that return collections (e.g., where, iif) - should preserve arity where possible
- operators that require computation of lower upper type bound (e.g., union, in, =)
- empty collection literal {}

Difference to FHIRPath spec:

- equality operators (=, !=) should only be defined for collection with where the lowest upper bound type exists
  for elements types. This is a stricter requirement than in the FHIRPath spec but it makes sense for static type
  checking.
  Examples:

Let's aT represnts a collection of type T with arity a, where a is either ? (singleton) or * (collection)

- `?INTEGER` - singleton integer
- `*DECIMAL` - collection of decimals

Let `[T]`represent a type variabele that can be any type. `?[T]` represents a singleton collection of type T,
and `*[T]` represents a collection of type T.

- `lub(E,C)` represents the least upper bound type of types E and C.
- `{es}`: effective signature with type casts
- `T->X` represents implicts transfromation (can involve multiple steps) from type T to type X.
- `T = {INTEGER, DECIMAL, QUANTITY} represents that T can be any of the types INTEGER, DECIMAL, or QUANTITY.`
- `[X in Y]` represents that type X is a subtype of type Y.
The following should be true:

- `*[T].first()  ⇒ ?T`
- `*INTEGER.fist()  ⇒ ?INTEGER`
- `?INTEGER.fist()  ⇒ ?INTEGER` // minimize arity where possible
- `?[E] in *[C] ⇒ ?BOOLEAN if exists(T=lub(E,C))  { ?(E->T) in *(E->T) ⇒ ?BOOLEAN }`
- `?INTEGER in *DECIMAL  ⇒ ?BOOLEAN  { ?(INTEGER->DECIMAL) in *DECIMAL ⇒ ?BOOLEAN }`
- `*INTEGER in *DECIMAL  ⇒ error (no overload for collection in collection)`
- `*INTEGER in *STRING  ⇒ error (no lub)`
- `*[K] union *[L] ⇒ *[T = lub(K,L)]  { *(K->T) union *(L->T) ⇒ *(T) }`
- `*INTEGER union *DECIMAL  ⇒ *DECIMAL  { *(INTEGER->DECIMAL) union *DECIMAL ⇒ *DECIMAL }`
- `?INTEGER union ?DECIMAL  ⇒ *DECIMAL  { ?(INTEGER->DECIMAL) union *DECIMAL ⇒ *DECIMAL }`
- `?STRING union *DECIMAL  ⇒ error (no lub)`
- `*K = *L  ⇒ ?BOOLEAN  { *(K->T) = *(L->T) ⇒ *BOOLEAN  where T=lub(K,L) }`
- `?INTEGER = *DECIMAL  ⇒ ?BOOLEAN  { ?(INTEGER->DECIMAL) = *(DECIMAL) ⇒ *BOOLEAN  }`
- `*STRING = *DECIMAL  ⇒ error (no lub)}`
- `*[T].where(lambda ?[T]⇒?BOOLEAN) ⇒ *[T]`
- `?INTEGER.where(lambda ?INTEGER⇒?BOOLEAN) ⇒ ?INTEGER` // minimize arity where possible
- `*[T].iif(lambda *[T]⇒?BOOLEAN, lambda *[T]=>*[M], lambda *[T]=>*[K]) ⇒ *[T = lub(M,K)]`
- `?INTEGER.iif(lambda ?INTEGER⇒?BOOLEAN, lambda ?INTEGER=>?DECIMAL, lambda ?INTEGER=>?INTEGER) ⇒ ?DECIMAL`
  `{ ?INTEGER.iif(lambda ?INTEGER⇒?BOOLEAN, lambda ?INTEGER=>?DECIMAL, lambda ?INTEGER=>?DECIMAL) ⇒ ?DECIMAL }`
- `?[M in ARYTHMETIC_TYPES] + ?[N in ARYTHMETIC_TYPES]  ⇒ ?[P]  if exists(P = lub(M,N))`
- `?INTEGER + ?DECIMAL  ⇒ ?DECIMAL  { ?INTEGER + ?(INTEGER->DECIMAL) ⇒ ?DECIMAL }`
- `?STRING + ?DECIMAL  ⇒ error (no lub)`
- `?BOOLEAN + ?DECIMAL  ⇒ error(not arythmetic type)`

Out of scope:
- FHIR polymorphic types (e.g., value[x]) 

References:
- FHIRPath Specification `specs/FHIRPath.md`'
- FHIR FHIRPath Use Specification: `spec/FHIR_FHIRPath.md`

