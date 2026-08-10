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

import au.csiro.fhirpath.typing.LambdaType;
import au.csiro.fhirpath.typing.Type;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.List;
import java.util.stream.Stream;

/**
 * Signature definition: parameters and result with explicit cardinality.
 *
 * <p>Each parameter has a concrete type + cardinality via {@link ParamSpec}. The result is
 * specified via {@link ResultTypeSpec}, which supports both static and dynamic resolution:
 *
 * <ul>
 *   <li>Static: {@code ResultTypeSpec.single(INTEGER)} for fixed types
 *   <li>Dynamic: {@code ResultTypeSpec.inputType(MANY)} to preserve input type
 * </ul>
 *
 * <p>Polymorphism is expressed by enumeration via {@code forTypes(GROUP).define(...)} rather than
 * by type variables — see #260 for the refactor that would introduce type variables and type-set
 * constraints.
 *
 * <p>Implements TypeGroup to enable zero-overhead usage in registry: a SignatureDefinition IS a
 * TypeGroup that expands to itself.
 *
 * @param parameters the list of parameter specifications
 * @param resultSpec the result type specification
 * @param minArity the minimum number of arguments required
 * @param lambdaBinding the lambda binding strategy, or null if no lambda parameters
 */
public record SignatureDefinition(
    @Nonnull List<ParamSpec> parameters,
    @Nonnull ResultTypeSpec resultSpec,
    int minArity,
    @Nullable LambdaBindingStrategy lambdaBinding)
    implements TypeGroup {
  /** Constructor for non-lambda signatures with fixed arity. */
  public SignatureDefinition(
      @Nonnull final List<ParamSpec> parameters, @Nonnull final ResultTypeSpec resultSpec) {
    this(parameters, resultSpec, parameters.size(), null);
  }

  /** Constructor for non-lambda signatures with variable arity. */
  public SignatureDefinition(
      @Nonnull final List<ParamSpec> parameters,
      @Nonnull final ResultTypeSpec resultSpec,
      final int minArity) {
    this(parameters, resultSpec, minArity, null);
  }

  /** Returns the number of parameters in this signature. */
  public int arity() {
    return parameters.size();
  }

  /**
   * TypeGroup implementation: a signature expands to itself. This enables zero-overhead usage in
   * registry - no wrapper needed.
   */
  @Nonnull
  @Override
  public Stream<SignatureDefinition> expand() {
    return Stream.of(this);
  }

  /** Returns true if any parameter type is a LambdaType. */
  public boolean hasLambdaParameters() {
    return parameters.stream().anyMatch(p -> p.type() instanceof LambdaType);
  }

  /**
   * Returns true if this signature can be applied to the given number of arguments. A signature
   * matches if argCount is within [minArity, arity].
   */
  public boolean canApplyToArgumentCount(final int argCount) {
    return argCount >= minArity && argCount <= arity();
  }

  /** Gets the parameter spec at the given index. */
  @Nonnull
  public ParamSpec parameter(final int index) {
    return parameters.get(index);
  }

  /**
   * Gets all parameter types (without cardinality). Convenience method for backward compatibility.
   */
  @Nonnull
  public List<Type> parameterTypes() {
    return parameters.stream().map(ParamSpec::type).toList();
  }

  @Override
  public String toString() {
    return parameters.stream()
        .map(ParamSpec::toString)
        .reduce((a, b) -> a + ", " + b)
        .map(params -> "(" + params + ") → " + resultSpec)
        .orElse("() → " + resultSpec);
  }
}
