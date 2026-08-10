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
import java.util.stream.Stream;

/** Utilities for working with the FHIRPath type system. */
public final class TypeSystem {
  private TypeSystem() {}

  /**
   * Checks if a value of type {@code from} can be adapted/cast to type {@code to}.
   *
   * <p>Implements the adaptation rules from the FHIRPath type system:
   *
   * <ul>
   *   <li>INTEGER → DECIMAL
   *   <li>INTEGER → QUANTITY
   *   <li>DECIMAL → QUANTITY
   *   <li>DATE → DATE_TIME
   *   <li>NULL → any type
   * </ul>
   */
  public static boolean canCast(final Type from, final Type to) {
    if (from == to) return true;
    if (from == SystemType.NULL) return true;

    // FhirPrimitiveType delegates to its underlying System type
    if (from instanceof FhirPrimitiveType fpt) {
      return canCast(fpt.getSystemType(), to);
    }

    if (from == SystemType.INTEGER && to == SystemType.DECIMAL) return true;
    if (from == SystemType.DATE && to == SystemType.DATE_TIME) return true;
    if (from == SystemType.INTEGER && to == SystemType.QUANTITY) return true;
    if (from == SystemType.DECIMAL && to == SystemType.QUANTITY) return true;

    return false;
  }

  /**
   * Returns a stream of all primitive types, including ANY.
   *
   * @return stream of all {@link SystemType} values
   */
  @Nonnull
  public static Stream<Type> allTypes() {
    return Stream.of(SystemType.values());
  }

  /**
   * Returns a stream of all defined primitive types, excluding the ANY wildcard.
   *
   * @return stream of concrete {@link SystemType} values
   */
  @Nonnull
  public static Stream<Type> definedTypes() {
    return Stream.of(SystemType.values()).map(Type.class::cast).filter(t -> t != SystemType.ANY);
  }
}
