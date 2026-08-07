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

/**
 * Sealed interface for FHIRPath temporal literal value wrappers.
 *
 * <p>Unifies {@link DateValue}, {@link DateTimeValue}, and {@link TimeValue} so that code needing
 * to unwrap temporal literals can use a single {@code instanceof TemporalValue} check.
 */
public sealed interface TemporalValue permits DateValue, DateTimeValue, TimeValue {

  /** Returns the ISO 8601 string value with the FHIRPath prefix stripped. */
  String value();
}
