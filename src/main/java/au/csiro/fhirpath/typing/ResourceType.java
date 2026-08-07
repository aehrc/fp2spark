package au.csiro.fhirpath.typing;

/**
 * Marker interface for FHIR resource types.
 *
 * <p>Distinguishes resources from data types, which is needed for {@code ofType()}, {@code
 * resolve()}, and Analyzer root identification.
 *
 * <p>Implementations:
 *
 * <ul>
 *   <li>{@link FhirResourceType} — HAPI FHIR-backed resource with lazy field resolution
 *   <li>{@link InlineResourceType} — explicit field definitions (for tests and inline use)
 * </ul>
 */
public sealed interface ResourceType extends ComplexType
    permits FhirResourceType, InlineResourceType {

  /** Returns the resource name (same as {@link #getName()}). */
  default String getResourceName() {
    return getName();
  }
}
