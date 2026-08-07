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

import jakarta.annotation.Nonnull;
import java.util.List;

/**
 * Represents FHIRPath type reflection information (namespace, name, baseType).
 *
 * <p>Used by the {@code type()} function to return type metadata as a struct. Maps FHIRPath types
 * to their reflection representation following the FHIRPath specification and Pathling's simplified
 * type hierarchy:
 *
 * <ul>
 *   <li>System types → namespace="System", baseType="System.Any"
 *   <li>FHIR resource types → namespace="FHIR", baseType="FHIR.Resource"
 *   <li>FHIR element types → namespace="FHIR", baseType="FHIR.Element"
 * </ul>
 *
 * @param namespace the type namespace ("System" or "FHIR")
 * @param name the unqualified type name (e.g., "String", "boolean", "Patient")
 * @param baseType the base type specifier (e.g., "System.Any", "FHIR.Element", "FHIR.Resource")
 */
public record TypeInfoValue(
    @Nonnull String namespace, @Nonnull String name, @Nonnull String baseType) {

  /** Base type for all System types. */
  private static final String SYSTEM_ANY_BASE = "System.Any";

  /** Base type for FHIR element/data types. */
  private static final String FHIR_ELEMENT_BASE = "FHIR.Element";

  /** Base type for FHIR resource types. */
  private static final String FHIR_RESOURCE_BASE = "FHIR.Resource";

  /**
   * The FHIRPath type of the TypeInfo struct itself, with fields: namespace, name, baseType (all
   * STRING).
   */
  public static final InlineComplexType TYPE_INFO_TYPE =
      new InlineComplexType(
          "TypeInfo",
          List.of(
              new FieldSpec("namespace", Shape.single(SystemType.STRING)),
              new FieldSpec("name", Shape.single(SystemType.STRING)),
              new FieldSpec("baseType", Shape.single(SystemType.STRING))));

  /**
   * Creates a TypeInfoValue from a FHIRPath type.
   *
   * @param type the FHIRPath type to reflect
   * @return the type reflection information
   */
  @Nonnull
  public static TypeInfoValue fromType(@Nonnull final Type type) {
    // TypeInfo struct itself is treated as System.Object (following Pathling)
    if (type == TYPE_INFO_TYPE) {
      return new TypeInfoValue(TypeSpecifier.SYSTEM_NAMESPACE, "Object", SYSTEM_ANY_BASE);
    }
    if (type instanceof ResourceType rt) {
      return new TypeInfoValue(TypeSpecifier.FHIR_NAMESPACE, rt.getName(), FHIR_RESOURCE_BASE);
    }
    if (type instanceof ComplexType ct) {
      return new TypeInfoValue(TypeSpecifier.FHIR_NAMESPACE, ct.getName(), FHIR_ELEMENT_BASE);
    }
    if (type instanceof FhirPrimitiveType fpt) {
      return new TypeInfoValue(TypeSpecifier.FHIR_NAMESPACE, fpt.getFhirName(), FHIR_ELEMENT_BASE);
    }
    if (type instanceof SystemType st) {
      return new TypeInfoValue(TypeSpecifier.SYSTEM_NAMESPACE, st.getName(), SYSTEM_ANY_BASE);
    }
    // Fallback for unknown types (callers should not pass ChoiceTypeLike directly)
    return new TypeInfoValue(TypeSpecifier.SYSTEM_NAMESPACE, "Object", SYSTEM_ANY_BASE);
  }
}
