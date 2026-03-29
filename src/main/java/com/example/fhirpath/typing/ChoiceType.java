package com.example.fhirpath.typing;

import ca.uhn.fhir.context.BaseRuntimeElementDefinition;
import ca.uhn.fhir.context.RuntimeChildChoiceDefinition;
import jakarta.annotation.Nonnull;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents a FHIR choice type (e.g., {@code value[x]} on Observation).
 *
 * <p>Choice types are polymorphic — the actual type is determined at runtime. This type wraps a
 * HAPI {@link RuntimeChildChoiceDefinition} and provides variant resolution to narrow the choice to
 * a specific type via {@code ofType()}, {@code is}, or {@code as}.
 *
 * <p>Direct field traversal on a choice type is disallowed; users must first narrow to a specific
 * variant.
 */
public final class ChoiceType implements ChoiceTypeLike {

  private static final Logger LOG = LoggerFactory.getLogger(ChoiceType.class);

  private final RuntimeChildChoiceDefinition childDefinition;
  private final String elementName;

  /**
   * Constructs a choice type from a HAPI choice definition.
   *
   * @param childDefinition the HAPI runtime choice definition
   * @param elementName the base element name (e.g., "value", "deceased")
   */
  public ChoiceType(
      @Nonnull final RuntimeChildChoiceDefinition childDefinition,
      @Nonnull final String elementName) {
    this.childDefinition = childDefinition;
    this.elementName = elementName;
  }

  @Override
  public String getName() {
    return "Choice(" + elementName + ")";
  }

  @Override
  public boolean isPrimitive() {
    return false;
  }

  @Override
  public boolean isComplex() {
    return false;
  }

  /**
   * Direct field traversal is disallowed on choice types. Users must narrow via {@code ofType()},
   * {@code is}, or {@code as} first.
   */
  @Override
  public Optional<FieldSpec> resolveField(@Nonnull final String fieldName) {
    return Optional.empty();
  }

  /**
   * Resolves a specific variant by FHIR type name.
   *
   * <p>The column name is formed by concatenating the element name with the capitalized type name
   * (e.g., "value" + "Quantity" → "valueQuantity").
   *
   * @param typeName the FHIR type name (e.g., "Quantity", "string", "boolean")
   * @return the field specification for the variant, or empty if the type is not a valid variant
   */
  @Nonnull
  public Optional<FieldSpec> resolveVariant(@Nonnull final String typeName) {
    final String columnName = ChoiceTypeLike.variantColumnName(elementName, typeName);

    // Check if this is a valid variant.
    // HAPI throws AssertionError (not IllegalArgumentException) for invalid child names
    // on RuntimeChildChoiceDefinition — this is an upstream quirk we must work around.
    final BaseRuntimeElementDefinition<?> elementDef;
    try {
      elementDef = childDefinition.getChildByName(columnName);
    } catch (final IllegalArgumentException | AssertionError e) {
      LOG.warn(
          "Variant '{}' not found on choice element '{}': {}",
          columnName,
          elementName,
          e.getMessage());
      return Optional.empty();
    }
    if (elementDef == null) {
      return Optional.empty();
    }

    final Type variantType = FhirComplexType.toFhirPathType(elementDef);
    return Optional.of(new FieldSpec(columnName, Shape.single(variantType)));
  }

  @Override
  public String toString() {
    return getName();
  }
}
