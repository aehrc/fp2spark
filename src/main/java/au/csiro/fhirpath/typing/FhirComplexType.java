/*
 * Copyright © 2025-2026 Commonwealth Scientific and Industrial Research
 * Organisation (CSIRO) ABN 41 687 119 230.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package au.csiro.fhirpath.typing;

import ca.uhn.fhir.context.BaseRuntimeChildDefinition;
import ca.uhn.fhir.context.BaseRuntimeElementCompositeDefinition;
import ca.uhn.fhir.context.BaseRuntimeElementDefinition;
import ca.uhn.fhir.context.RuntimeChildChoiceDefinition;
import ca.uhn.fhir.context.RuntimeChildExtension;
import ca.uhn.fhir.context.RuntimePrimitiveDatatypeDefinition;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.Optional;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Quantity;
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

  /**
   * Returns {@code true} if this FHIR complex type is structurally compatible with {@link
   * SystemType#QUANTITY}, i.e. it is Quantity or one of its HAPI subclasses (Duration, Age, Count,
   * Distance, Money, SimpleQuantity).
   *
   * <p>These types share the {@code (value, unit, system, code)} struct layout in Spark storage, so
   * they can be substituted for {@code System.Quantity} wherever a quantity is expected.
   */
  public boolean isQuantityCompatible() {
    return Quantity.class.isAssignableFrom(definition.getImplementingClass());
  }

  /**
   * Returns {@code true} if this FHIR complex type is structurally compatible with {@link
   * SystemType#CODING}, i.e. it is Coding or one of its HAPI subclasses.
   *
   * <p>Tested by HAPI class assignability rather than by type name so that subclasses are
   * recognised, mirroring {@link #isQuantityCompatible()}.
   */
  public boolean isCodingCompatible() {
    return Coding.class.isAssignableFrom(definition.getImplementingClass());
  }

  /**
   * Returns {@code true} if this FHIR complex type is a {@code CodeableConcept} or one of its HAPI
   * subclasses.
   *
   * <p>Unlike {@link #isCodingCompatible()} there is no corresponding {@link SystemType}, so
   * callers that accept both shapes — such as the terminology functions — must branch on this
   * separately.
   */
  public boolean isCodeableConcept() {
    return CodeableConcept.class.isAssignableFrom(definition.getImplementingClass());
  }

  @Override
  public Optional<FieldSpec> resolveField(final String fieldName) {
    // Look up the child by name (try exact name first, then choice type name with [x] suffix)
    final BaseRuntimeChildDefinition childDef = lookupChild(fieldName);
    if (childDef == null) {
      return Optional.empty();
    }

    // Determine cardinality (shared by both choice and non-choice paths)
    final Cardinality cardinality = childDef.getMax() != 1 ? Cardinality.MANY : Cardinality.SINGLE;

    // Choice types (e.g., value[x]) — return ChoiceType for narrowing via ofType/is/as.
    // RuntimeChildExtension extends RuntimeChildChoiceDefinition in HAPI but is NOT a
    // polymorphic choice type — it is a composite Extension element. Exclude it here so
    // it falls through to the general composite resolution path below.
    if (childDef instanceof RuntimeChildChoiceDefinition choiceDef
        && !(childDef instanceof RuntimeChildExtension)) {
      return Optional.of(
          new FieldSpec(fieldName, Shape.of(new ChoiceType(choiceDef, fieldName), cardinality)));
    }

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
  @Nullable
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
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    return definition.getName().equals(((FhirComplexType) o).definition.getName());
  }

  @Override
  public int hashCode() {
    return definition.getName().hashCode();
  }

  @Override
  public String toString() {
    return getName();
  }
}
