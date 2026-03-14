package com.example.fhirpath.typing;

import jakarta.annotation.Nonnull;
import java.util.Optional;

/**
 * Resolves field specifications for a given parent type and field name.
 *
 * <p>This interface decouples field resolution from the type representation, allowing multiple
 * sources of type definitions:
 *
 * <ul>
 *   <li>{@link ComplexTypeResolver} — delegates to explicit {@link ComplexType} field definitions
 *   <li>{@link HapiTypeResolver} — resolves fields lazily from HAPI FHIR R4 runtime model
 * </ul>
 */
public interface TypeResolver {

  /**
   * Resolves a field specification for the given parent type and field name.
   *
   * @param parentType the type of the parent element being traversed
   * @param fieldName the name of the field to resolve
   * @return the field specification, or empty if the field cannot be resolved
   */
  @Nonnull
  Optional<FieldSpec> resolveField(@Nonnull Type parentType, @Nonnull String fieldName);
}
