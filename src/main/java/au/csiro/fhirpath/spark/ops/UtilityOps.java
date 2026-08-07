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
package au.csiro.fhirpath.spark.ops;

import au.csiro.fhirpath.spark.SparkOperationRegistry;
import jakarta.annotation.Nonnull;

/**
 * Utility function registrations (FHIRPath §5.9).
 *
 * <p>Currently only {@code trace()}, which returns the column of its input collection untouched.
 * See {@link au.csiro.fhirpath.operation.signature.Signatures#diagnosticPassThrough} for why.
 */
public final class UtilityOps {

  private UtilityOps() {}

  /**
   * Registers all utility functions into the given registry.
   *
   * @param registry the registry to register operations into
   */
  public static void register(@Nonnull final SparkOperationRegistry registry) {
    // trace(name [, projection]) returns the input collection unaltered.
    registry.register("trace", ctx -> ctx.arg(0));
  }
}
