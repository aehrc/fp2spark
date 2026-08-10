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

import jakarta.annotation.Nonnull;
import java.util.stream.Stream;

/**
 * Represents a group of signature definitions that can be expanded.
 *
 * <p>This interface enables uniform handling of both single signatures and collections of related
 * signatures. Key implementations:
 *
 * <p>- SignatureDefinition: A single signature expands to itself - TypeMapping: Applies a function
 * over multiple types to generate signatures
 *
 * <p>This design eliminates wrapper overhead for single signatures while enabling elegant
 * composition of multi-type patterns.
 */
public sealed interface TypeGroup permits SignatureDefinition, TypeMapping {

  /**
   * Expands this type group into a stream of signature definitions.
   *
   * <p>For single signatures, returns a stream containing just the signature. For type mappings,
   * applies the mapping function to each type.
   *
   * @return stream of signature definitions
   */
  @Nonnull
  Stream<SignatureDefinition> expand();
}
