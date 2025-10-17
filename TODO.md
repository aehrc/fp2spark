
To do list:

- Fix NULL and null evaluation for functions and operators.
- Further refactor Analyzer and FunctionRegistry to reduce code duplication. 
  Most likely hide the details of function call resolution from the Analyzer 
  and unify function and operator resolution.
- Try to implment Equals and Union as standard operations.
- Make the distinction between ANY and Colllection[ANY] more clear in the code.
- Type system re-desing/refactoring
  - Make a note in the requirements that we want static type checking with static arity checking.
  - Consider the options for design for arity tracking in the type system. 
  These may be different in the formal specification vs how they look in the code (for streamlined definition).
  - Consider extending type system with type variables and parametric polymorphism vs allowing 
  custom Java function type resolvers classes for some exotic signatures 
  (that cannot be expressed with non-generic types).
