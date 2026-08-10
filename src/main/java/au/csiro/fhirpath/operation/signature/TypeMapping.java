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
package au.csiro.fhirpath.operation.signature;

import au.csiro.fhirpath.typing.Type;
import jakarta.annotation.Nonnull;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Type group that applies a signature-generating function over a set of types.
 *
 * <p>This enables the pattern: forTypes(NUMERIC).define(Signatures::unaryOp) which expands to one
 * signature per type in the set.
 *
 * <p>Example:
 *
 * <pre>
 * forTypes(TypeSets.NUMERIC).define(Signatures::unaryOp)
 * // Expands to:
 * // abs(Integer) → Integer
 * // abs(Decimal) → Decimal
 * </pre>
 *
 * @param types the set of types to generate signatures for
 * @param mapper the function to apply to each type to produce a signature
 */
public record TypeMapping(
    @Nonnull Set<Type> types, @Nonnull Function<Type, SignatureDefinition> mapper)
    implements TypeGroup {

  /** Expands this type mapping by applying the mapper function to each type. */
  @Nonnull
  @Override
  public Stream<SignatureDefinition> expand() {
    return types.stream().map(mapper);
  }
}
