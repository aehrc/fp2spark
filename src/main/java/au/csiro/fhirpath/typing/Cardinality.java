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
 * Represents cardinality in the FHIRPath type system.
 *
 * <p>FHIRPath uses an element-first type model where cardinality is separate from element type.
 * This matches the spec notation:
 *
 * <ul>
 *   <li>{@code ?T} (single) - zero or one element (0..1)
 *   <li>{@code *T} (many) - zero or more elements (0..*)
 * </ul>
 *
 * <p>Cardinality is metadata about the result shape, not part of the type hierarchy.
 */
public enum Cardinality {
  /** Single cardinality (0..1). Corresponds to the spec's {@code ?T} notation. */
  SINGLE,

  /** Many cardinality (0..*). Corresponds to the spec's {@code *T} notation. */
  MANY;

  /**
   * Join (union) of two cardinalities. Used when combining results from different branches (e.g.,
   * iif, union).
   *
   * @param other the other cardinality
   * @return MANY if either cardinality is MANY, otherwise SINGLE
   */
  public Cardinality join(final Cardinality other) {
    return (this == MANY || other == MANY) ? MANY : SINGLE;
  }

  /** Returns whether this cardinality represents a single element (0..1). */
  public boolean isSingle() {
    return this == SINGLE;
  }

  /** Returns whether this cardinality represents many elements (0..*). */
  public boolean isMany() {
    return this == MANY;
  }

  @Override
  public String toString() {
    return switch (this) {
      case SINGLE -> "?";
      case MANY -> "*";
    };
  }
}
