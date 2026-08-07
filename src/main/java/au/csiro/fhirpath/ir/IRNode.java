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
package au.csiro.fhirpath.ir;

import au.csiro.fhirpath.typing.Cardinality;
import au.csiro.fhirpath.typing.Shape;
import au.csiro.fhirpath.typing.Type;
import jakarta.annotation.Nonnull;

/**
 * Base interface for all IR nodes in the FHIRPath expression tree. IRNodes are target-agnostic and
 * represent typed FHIRPath expressions.
 *
 * <p>Code generation is delegated to target-specific visitors via the accept() method.
 *
 * <p>Each IR node has a {@link Shape} (element type + cardinality) representing the type and
 * cardinality of values it produces.
 */
@SuppressWarnings("checkstyle:AbbreviationAsWordInName")
public sealed interface IRNode
    permits Operation, Literal, Traversal, Cast, Resource, Lambda, ThisReference {

  /**
   * Returns the shape of this expression (element type + cardinality). For operations, this is the
   * result shape from the resolved signature.
   */
  @Nonnull
  Shape getShape();

  /**
   * Returns the element type of this expression. Convenience method equivalent to {@code
   * getShape().elementType()}.
   */
  @Nonnull
  default Type getType() {
    return getShape().elementType();
  }

  /**
   * Returns the cardinality of this expression. Convenience method equivalent to {@code
   * getShape().cardinality()}.
   */
  @Nonnull
  default Cardinality getCardinality() {
    return getShape().cardinality();
  }

  /** Returns whether this expression is singular (single cardinality, 0..1). */
  default boolean isSingular() {
    return getShape().isSingle();
  }

  /**
   * Accepts a visitor for target-specific code generation.
   *
   * @param visitor The visitor to accept
   * @param <T> The return type of the visitor (e.g., Column for Spark, String for SQL)
   * @return The result of visiting this node
   */
  @Nonnull
  <T> T accept(@Nonnull IRNodeVisitor<T> visitor);
}
