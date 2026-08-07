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
package au.csiro.fhirpath.test;

import au.csiro.fhirpath.typing.SystemType;
import jakarta.annotation.Nonnull;

/**
 * Marker for a null value with known type, used in Map-based test data.
 *
 * <p>When a field is empty but has a known type (e.g., {@code stringEmpty("name")}), this marker
 * preserves the type information so that {@link ResourceTypeInference} can infer the correct field
 * type instead of falling back to {@link SystemType#NULL}.
 *
 * @param type the known primitive type of the empty field
 */
public record TypedNull(@Nonnull SystemType type) {

  public static final TypedNull STRING = new TypedNull(SystemType.STRING);
  public static final TypedNull INTEGER = new TypedNull(SystemType.INTEGER);
  public static final TypedNull DECIMAL = new TypedNull(SystemType.DECIMAL);
  public static final TypedNull BOOLEAN = new TypedNull(SystemType.BOOLEAN);
  public static final TypedNull DATE = new TypedNull(SystemType.DATE);
  public static final TypedNull DATE_TIME = new TypedNull(SystemType.DATE_TIME);
  public static final TypedNull TIME = new TypedNull(SystemType.TIME);
  public static final TypedNull QUANTITY = new TypedNull(SystemType.QUANTITY);
  public static final TypedNull CODING = new TypedNull(SystemType.CODING);
}
