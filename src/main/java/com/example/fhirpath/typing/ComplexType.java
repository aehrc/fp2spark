package com.example.fhirpath.typing;

/**
 * Represents a complex FHIRPath type with named fields (e.g., a FHIR resource or data type).
 *
 * <p>This is a sealed interface with two implementation strategies:
 *
 * <ul>
 *   <li>{@link FhirComplexType} — resolves fields lazily from HAPI FHIR runtime definitions
 *   <li>{@link InlineComplexType} — stores fields explicitly in a map (for tests and inline
 *       definitions)
 * </ul>
 *
 * <p>Resource types extend this interface via {@link ResourceType} marker interface with
 * corresponding implementations {@link FhirResourceType} and {@link InlineResourceType}.
 */
public sealed interface ComplexType extends Type
    permits FhirComplexType, InlineComplexType, ResourceType {

  @Override
  default boolean isPrimitive() {
    return false;
  }

  @Override
  default boolean isComplex() {
    return true;
  }
}
