package com.example.fhirpath.typing;

import ca.uhn.fhir.context.BaseRuntimeChildDefinition;
import ca.uhn.fhir.context.BaseRuntimeElementCompositeDefinition;
import ca.uhn.fhir.context.BaseRuntimeElementDefinition;
import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.RuntimeChildChoiceDefinition;
import ca.uhn.fhir.context.RuntimePrimitiveDatatypeDefinition;
import ca.uhn.fhir.context.RuntimeResourceDefinition;
import jakarta.annotation.Nonnull;
import java.util.Optional;

/**
 * HAPI FHIR-backed type resolver that resolves fields lazily from HAPI's runtime model.
 *
 * <p>This resolver uses HAPI FHIR R4 definitions to look up field types and cardinalities. When it
 * encounters a complex child type (e.g., HumanName), it returns a {@link FieldSpec} with a
 * name-only {@link ComplexType} marker. On subsequent traversals, the resolver looks up that type
 * name in HAPI to resolve its fields, enabling lazy resolution without eager tree building.
 *
 * <p>Choice types ({@link RuntimeChildChoiceDefinition}) are skipped (deferred to issue #42).
 */
public class HapiTypeResolver implements TypeResolver {

  private final FhirContext fhirContext;

  /**
   * Creates a resolver using the given FHIR context.
   *
   * @param fhirContext the HAPI FHIR context (typically R4)
   */
  public HapiTypeResolver(@Nonnull final FhirContext fhirContext) {
    this.fhirContext = fhirContext;
  }

  @Override
  @Nonnull
  public Optional<FieldSpec> resolveField(
      @Nonnull final Type parentType, @Nonnull final String fieldName) {
    // FhirPrimitiveType has no child fields
    if (parentType instanceof FhirPrimitiveType) {
      return Optional.empty();
    }

    // Look up the HAPI composite definition for this parent type
    final BaseRuntimeElementCompositeDefinition<?> compositeDef =
        resolveCompositeDefinition(parentType);
    if (compositeDef == null) {
      return Optional.empty();
    }

    // Look up the child by name
    final BaseRuntimeChildDefinition childDef;
    try {
      childDef = compositeDef.getChildByName(fieldName);
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
    // For non-choice children, there is a single valid child name
    final BaseRuntimeElementDefinition<?> elementDef = resolveElementDefinition(childDef);
    if (elementDef == null) {
      return Optional.empty();
    }

    final Type fieldType = toFhirPathType(elementDef);
    final Shape shape = Shape.of(fieldType, cardinality);
    return Optional.of(new FieldSpec(fieldName, shape));
  }

  /**
   * Resolves the HAPI composite definition for a parent type.
   *
   * @param parentType the FHIRPath type to resolve
   * @return the composite definition, or null if not resolvable
   */
  private BaseRuntimeElementCompositeDefinition<?> resolveCompositeDefinition(
      @Nonnull final Type parentType) {
    if (parentType instanceof ResourceType rt && rt != ResourceType.EMPTY) {
      try {
        return fhirContext.getResourceDefinition(rt.getResourceName());
      } catch (final Exception e) {
        return null;
      }
    }

    if (parentType instanceof ComplexType ct) {
      final String typeName = ct.getName();
      if ("ComplexType".equals(typeName)) {
        // Anonymous complex type — cannot resolve via HAPI
        return null;
      }
      // Try as a resource first, then as a datatype
      try {
        final RuntimeResourceDefinition resDef = fhirContext.getResourceDefinition(typeName);
        return resDef;
      } catch (final Exception e) {
        // Not a resource, try as element/datatype
      }
      try {
        final BaseRuntimeElementDefinition<?> elemDef = fhirContext.getElementDefinition(typeName);
        if (elemDef instanceof BaseRuntimeElementCompositeDefinition<?> compDef) {
          return compDef;
        }
      } catch (final Exception e) {
        // Not found
      }
      return null;
    }

    return null;
  }

  /**
   * Resolves the element definition from a child definition.
   *
   * @param childDef the child definition
   * @return the element definition, or null if not resolvable
   */
  private static BaseRuntimeElementDefinition<?> resolveElementDefinition(
      @Nonnull final BaseRuntimeChildDefinition childDef) {
    // For non-choice children, use getChildByName to get the element definition
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
      // Return a name-only ComplexType marker for lazy resolution
      return new ComplexType(compDef.getName());
    }

    // Fallback for unexpected types
    return FhirPrimitiveType.of("string");
  }
}
