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

/**
 * Represents a FHIRPath shape: the combination of an element type and cardinality.
 *
 * <p>In the FHIRPath type system, shapes describe what kind of values an expression produces:
 *
 * <ul>
 *   <li>{@code ?T} - single element of type T (0..1)
 *   <li>{@code *T} - collection of elements of type T (0..*)
 * </ul>
 *
 * <p>Shape is an element-first type model where cardinality is metadata, not part of the type
 * hierarchy. This replaces the previous {@code Collection<T>} approach.
 *
 * <p>Examples:
 *
 * <pre>
 *   Shape.single(INTEGER)      // ?integer
 *   Shape.many(STRING)         // *string
 *   Shape.single(BOOLEAN)      // ?boolean
 * </pre>
 */
public sealed interface Shape {

  /** The element type of this shape. */
  @Nonnull
  Type elementType();

  /** The cardinality of this shape. */
  @Nonnull
  Cardinality cardinality();

  /** Returns whether this shape represents a single element (0..1). */
  default boolean isSingle() {
    return cardinality().isSingle();
  }

  /** Returns whether this shape represents many elements (0..*). */
  default boolean isMany() {
    return cardinality().isMany();
  }

  /** Creates a single-element shape (?T). */
  static Shape single(Type elementType) {
    return new Single(elementType);
  }

  /** Creates a many-element shape (*T). */
  static Shape many(Type elementType) {
    return new Many(elementType);
  }

  /** Creates a shape with the specified cardinality. */
  static Shape of(Type elementType, Cardinality cardinality) {
    return switch (cardinality) {
      case SINGLE -> single(elementType);
      case MANY -> many(elementType);
    };
  }

  /**
   * Single-element shape (?T).
   *
   * @param elementType the element type
   */
  record Single(@Nonnull Type elementType) implements Shape {
    @Override
    @Nonnull
    public Cardinality cardinality() {
      return Cardinality.SINGLE;
    }

    @Override
    public String toString() {
      return "?" + elementType.getName();
    }
  }

  /**
   * Many-element shape (*T).
   *
   * @param elementType the element type
   */
  record Many(@Nonnull Type elementType) implements Shape {
    @Override
    @Nonnull
    public Cardinality cardinality() {
      return Cardinality.MANY;
    }

    @Override
    public String toString() {
      return "*" + elementType.getName();
    }
  }
}
