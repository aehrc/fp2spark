package com.example.fhirpath.typing;

import jakarta.annotation.Nonnull;

/**
 * Represents a lambda type in the FHIRPath type system. Lambdas are expressions that are evaluated
 * with an implicit $this binding.
 *
 * <p>In FHIRPath, lambda parameter types are always implicit - determined by the collection element
 * type. There is no syntax to declare parameter types. The lambda is checked for compatibility
 * during type resolution.
 *
 * <p>Used by collection operations like where(), select(), repeat() that accept criteria or
 * projection expressions.
 *
 * <p>Examples:
 *
 * <ul>
 *   <li>{@code where(criteria)} expects Lambda(Boolean)
 *   <li>{@code select(projection)} expects Lambda(R)
 * </ul>
 *
 * @param returnShape The shape of the lambda body evaluation result
 */
public record LambdaType(@Nonnull Shape returnShape) implements Type {

  @Override
  @Nonnull
  public String getName() {
    return "Lambda(" + returnShape + ")";
  }

  @Override
  public boolean isPrimitive() {
    return false; // Lambdas are not primitives
  }

  @Override
  public boolean isComplex() {
    return false; // Lambdas are not complex types
  }

  @Override
  public String toString() {
    return "Lambda(" + returnShape + ")";
  }
}
