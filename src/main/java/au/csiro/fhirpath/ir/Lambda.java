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
import jakarta.annotation.Nonnull;

/**
 * IR node representing a lambda expression (closure). Used by functions like where(), select(),
 * repeat() that take criteria/projection expressions.
 *
 * <p>The lambda body can reference special variables like $this (current element) and $index
 * (position). Parameter binding is handled dynamically during code generation - the Lambda doesn't
 * store parameter names or types, only the body expression.
 *
 * <p>Example: name.where(use = 'official')
 *
 * <pre>
 * body: Operation("equals", [Traversal($this, "use"), Literal("official")])
 * </pre>
 *
 * @param body the body expression evaluated within the lambda scope
 */
public record Lambda(@Nonnull IRNode body) implements IRNode {

  /**
   * Returns the shape of the lambda body (what it evaluates to). Does NOT return LambdaType -
   * that's only used in signatures for matching. The OverloadResolver uses instanceof Lambda to
   * detect lambda nodes.
   */
  @Override
  @Nonnull
  public Shape getShape() {
    return body.getShape();
  }

  @Override
  @Nonnull
  public <T> T accept(@Nonnull final IRNodeVisitor<T> visitor) {
    return visitor.visitLambda(this);
  }

  @Override
  public String toString() {
    return "Lambda($this -> " + body + ")";
  }
}
