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

/** Common Type constants for convenience. */
public final class Types {
  public static final Type INTEGER = SystemType.INTEGER;
  public static final Type DECIMAL = SystemType.DECIMAL;
  public static final Type BOOLEAN = SystemType.BOOLEAN;
  public static final Type STRING = SystemType.STRING;
  public static final Type DATE = SystemType.DATE;
  public static final Type DATE_TIME = SystemType.DATE_TIME;
  public static final Type TIME = SystemType.TIME;
  public static final Type QUANTITY = SystemType.QUANTITY;
  public static final Type CODING = SystemType.CODING;
  public static final Type NULL = SystemType.NULL;
  public static final Type ANY = SystemType.ANY;

  private Types() {
    // Utility class
  }
}
