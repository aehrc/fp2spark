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
package au.csiro.fhirpath.operation.signature;

import au.csiro.fhirpath.typing.Cardinality;
import au.csiro.fhirpath.typing.Type;
import jakarta.annotation.Nonnull;

/**
 * Parameter specification: a concrete element type plus cardinality.
 *
 * <p>Polymorphism is currently expressed by enumeration via {@code forTypes(GROUP).define(...)}
 * rather than by type variables — see #260 for the refactor that would introduce type variables and
 * type-set constraints if/when the enumeration approach starts to bite.
 *
 * @param type the element type of the parameter
 * @param cardinality the cardinality of the parameter (SINGLE or MANY)
 */
public record ParamSpec(@Nonnull Type type, @Nonnull Cardinality cardinality) {
  /** Creates a parameter spec for a SINGLE cardinality parameter (?T). */
  @Nonnull
  public static ParamSpec single(@Nonnull final Type type) {
    return new ParamSpec(type, Cardinality.SINGLE);
  }

  /** Creates a parameter spec for a MANY cardinality parameter (*T). */
  @Nonnull
  public static ParamSpec many(@Nonnull final Type type) {
    return new ParamSpec(type, Cardinality.MANY);
  }

  @Override
  public String toString() {
    return (cardinality == Cardinality.SINGLE ? "?" : "*") + type.getName();
  }
}
