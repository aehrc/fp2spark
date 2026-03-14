package com.example.fhirpath.typing;

import jakarta.annotation.Nonnull;
import java.util.Optional;

/**
 * Default type resolver that delegates to {@link ComplexType#getField(String)}.
 *
 * <p>Preserves existing behavior for explicit type definitions and tests.
 */
public class ComplexTypeResolver implements TypeResolver {

  @Override
  @Nonnull
  public Optional<FieldSpec> resolveField(
      @Nonnull final Type parentType, @Nonnull final String fieldName) {
    if (parentType instanceof ComplexType ct) {
      return ct.getField(fieldName);
    }
    return Optional.empty();
  }
}
