package au.csiro.fhirpath.typing;

import ca.uhn.fhir.context.RuntimeResourceDefinition;
import jakarta.annotation.Nonnull;

/**
 * A FHIR resource type backed by a HAPI runtime resource definition.
 *
 * <p>Extends {@link FhirComplexType} with the {@link ResourceType} marker. Created from a {@link
 * ca.uhn.fhir.context.FhirContext} at the entry point.
 */
public final class FhirResourceType extends FhirComplexType implements ResourceType {

  /**
   * Constructs a FHIR resource type from a HAPI runtime resource definition.
   *
   * @param definition the HAPI runtime resource definition
   */
  public FhirResourceType(@Nonnull final RuntimeResourceDefinition definition) {
    super(definition);
  }
}
