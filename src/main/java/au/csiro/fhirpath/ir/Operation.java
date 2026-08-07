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

import au.csiro.fhirpath.operation.signature.ResolvedSignature;
import au.csiro.fhirpath.typing.Shape;
import jakarta.annotation.Nonnull;
import java.util.List;

/**
 * Generic IR node representing any FHIRPath function or operator. Replaces specific operation
 * classes (Add, Abs, etc.).
 *
 * <p>The resolved signature is stored in the node, providing:
 *
 * <ul>
 *   <li>Result shape (via signature.resultShape()) - statically resolved during construction
 *   <li>Parameter types (for validation)
 *   <li>Which overload was selected (for debugging/optimization)
 * </ul>
 *
 * <p>Type resolution happens exactly once during Operation construction via
 * ResolvedSignature.resolve(), converting ResultTypeSpecs to concrete shapes.
 *
 * <p>Examples:
 *
 * <ul>
 *   <li>Operation("add", [leftIR, rightIR], resolvedSig)
 *   <li>Operation("abs", [targetIR], resolvedSig)
 *   <li>Operation("count", [collectionIR], resolvedSig)
 * </ul>
 *
 * @param name the function or operator name
 * @param args the list of arguments
 * @param signature the resolved signature with concrete types and result shape
 */
public record Operation(
    @Nonnull String name, @Nonnull List<IRNode> args, @Nonnull ResolvedSignature signature)
    implements IRNode {

  /**
   * Returns the result shape from the resolved signature. No recalculation needed - single source
   * of truth.
   */
  @Override
  @Nonnull
  public Shape getShape() {
    return signature.resultShape();
  }

  /** Accepts a visitor for target-specific code generation. */
  @Nonnull
  public <T> T accept(@Nonnull final IRNodeVisitor<T> visitor) {
    return visitor.visitOperation(this);
  }

  /** Convenience method to get argument count. */
  public int arity() {
    return args.size();
  }

  @Override
  public String toString() {
    return name + "(" + args + ") : " + getShape();
  }
}
