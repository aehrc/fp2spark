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

import au.csiro.fhirpath.typing.Shape;
import au.csiro.fhirpath.typing.Type;
import jakarta.annotation.Nonnull;

/**
 * Represents a type cast operation. Preserves the cardinality of the child while changing the
 * element type.
 *
 * @param child the expression being cast
 * @param targetType the target element type
 */
public record Cast(IRNode child, Type targetType) implements IRNode {
  @Override
  @Nonnull
  public Shape getShape() {
    // Preserve cardinality from child, but change element type
    return Shape.of(targetType, child.getCardinality());
  }

  @Override
  @Nonnull
  public <T> T accept(@Nonnull final IRNodeVisitor<T> visitor) {
    return visitor.visitCast(this);
  }
}
