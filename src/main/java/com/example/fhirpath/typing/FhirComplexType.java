package com.example.fhirpath.typing;

import ca.uhn.fhir.context.BaseRuntimeChildDefinition;
import ca.uhn.fhir.context.BaseRuntimeElementCompositeDefinition;
import ca.uhn.fhir.context.BaseRuntimeElementDefinition;
import ca.uhn.fhir.context.RuntimeChildChoiceDefinition;
import ca.uhn.fhir.context.RuntimePrimitiveDatatypeDefinition;
import jakarta.annotation.Nonnull;
import java.util.Optional;

/**
 * A complex type backed by a HAPI FHIR runtime composite definition.
 *
 * <p>Resolves fields lazily by delegating to the HAPI definition tree. Child complex types are
 * returned as new {@code FhirComplexType} instances wrapping the child's composite definition,
 * enabling natural propagation through the HAPI definition tree.
 *
 * <p>Works across FHIR versions (R4, R5) since the definition tree is version-specific. Backbone
 * types work naturally — HAPI includes them as composite children.
 */
public non-sealed class FhirComplexType implements ComplexType {

  private final BaseRuntimeElementCompositeDefinition<?> definition;

  /**
   * Constructs a FHIR complex type from a HAPI composite definition.
   *
   * @param definition the HAPI runtime composite definition
   */
  public FhirComplexType(@Nonnull final BaseRuntimeElementCompositeDefinition<?> definition) {
    this.definition = definition;
  }

  @Override
  public String getName() {
    return definition.getName();
  }

  @Override
  public Optional<FieldSpec> resolveField(final String fieldName) {
    // Look up the child by name
    final BaseRuntimeChildDefinition childDef;
    try {
      childDef = definition.getChildByName(fieldName);
    } catch (final IllegalArgumentException e) {
      return Optional.empty();
    }
    if (childDef == null) {
      return Optional.empty();
    }

    // Skip choice types (deferred to issue #42)
    if (childDef instanceof RuntimeChildChoiceDefinition) {
      return Optional.empty();
    }

    // Determine cardinality
    final Cardinality cardinality = childDef.getMax() != 1 ? Cardinality.MANY : Cardinality.SINGLE;

    // Resolve the element type from the child definition
    final BaseRuntimeElementDefinition<?> elementDef = resolveElementDefinition(childDef);
    if (elementDef == null) {
      return Optional.empty();
    }

    final Type fieldType = toFhirPathType(elementDef);
    final Shape shape = Shape.of(fieldType, cardinality);
    return Optional.of(new FieldSpec(fieldName, shape));
  }

  /**
   * Resolves the element definition from a child definition.
   *
   * @param childDef the child definition
   * @return the element definition, or null if not resolvable
   */
  private static BaseRuntimeElementDefinition<?> resolveElementDefinition(
      @Nonnull final BaseRuntimeChildDefinition childDef) {
    final var validNames = childDef.getValidChildNames();
    if (validNames.isEmpty()) {
      return null;
    }
    final String childName = validNames.iterator().next();
    return childDef.getChildByName(childName);
  }

  /**
   * Maps a HAPI element definition to a FHIRPath type.
   *
   * @param elementDef the HAPI element definition
   * @return the corresponding FHIRPath type
   */
  @Nonnull
  private static Type toFhirPathType(@Nonnull final BaseRuntimeElementDefinition<?> elementDef) {
    if (elementDef instanceof RuntimePrimitiveDatatypeDefinition primDef) {
      final String fhirTypeName = primDef.getName();
      if (FhirPrimitiveType.isKnown(fhirTypeName)) {
        return FhirPrimitiveType.of(fhirTypeName);
      }
      // Unknown primitive — fall back to STRING
      return FhirPrimitiveType.of("string");
    }

    if (elementDef instanceof BaseRuntimeElementCompositeDefinition<?> compDef) {
      // Return a new FhirComplexType wrapping the child definition
      return new FhirComplexType(compDef);
    }

    // Fallback for unexpected types
    return FhirPrimitiveType.of("string");
  }
}
