package com.example.fhirpath.typing;

import ca.uhn.fhir.context.BaseRuntimeChildDefinition;
import ca.uhn.fhir.context.BaseRuntimeElementCompositeDefinition;
import ca.uhn.fhir.context.BaseRuntimeElementDefinition;
import ca.uhn.fhir.context.RuntimeChildChoiceDefinition;
import ca.uhn.fhir.context.RuntimePrimitiveDatatypeDefinition;
import jakarta.annotation.Nonnull;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

  private static final Logger LOG = LoggerFactory.getLogger(FhirComplexType.class);

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
    // Look up the child by name (try exact name first, then choice type name with [x] suffix)
    final BaseRuntimeChildDefinition childDef = lookupChild(fieldName);
    if (childDef == null) {
      return Optional.empty();
    }

    // Choice types (e.g., value[x]) — return ChoiceType for narrowing via ofType/is/as
    if (childDef instanceof RuntimeChildChoiceDefinition choiceDef) {
      final Cardinality cardinality =
          childDef.getMax() != 1 ? Cardinality.MANY : Cardinality.SINGLE;
      return Optional.of(
          new FieldSpec(fieldName, Shape.of(new ChoiceType(choiceDef, fieldName), cardinality)));
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
   * Looks up a child definition by name, with fallback for choice types.
   *
   * <p>HAPI's {@code getChildByName()} does not resolve the base name of choice types (e.g.,
   * "value" returns null for Observation.value[x]). Following Pathling's approach, we fall back to
   * appending "[x]" to find unqualified choice fields.
   *
   * @param fieldName the field name to look up
   * @return the child definition, or null if not found
   */
  @jakarta.annotation.Nullable
  private BaseRuntimeChildDefinition lookupChild(@Nonnull final String fieldName) {
    try {
      final BaseRuntimeChildDefinition childDef = definition.getChildByName(fieldName);
      if (childDef != null) {
        return childDef;
      }
      // Fallback: try choice type name with [x] suffix
      return definition.getChildByName(fieldName + "[x]");
    } catch (final IllegalArgumentException e) {
      LOG.debug("Field '{}' not found on type '{}': {}", fieldName, getName(), e.getMessage());
      return null;
    }
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
    // Non-choice children have exactly one valid name; take it.
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
  static Type toFhirPathType(@Nonnull final BaseRuntimeElementDefinition<?> elementDef) {
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

  @Override
  public String toString() {
    return getName();
  }
}
