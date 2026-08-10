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

import static org.apache.spark.sql.functions.when;

import jakarta.annotation.Nonnull;
import org.apache.spark.sql.Column;

/**
 * Null-propagation helper for operations whose result is empty (SQL {@code NULL}) whenever any of
 * their inputs is empty — the common FHIRPath rule that an operation applied to an empty collection
 * yields an empty result.
 */
final class NullSupport {

  private NullSupport() {}

  /**
   * Returns {@code result} if every column in {@code inputs} is non-null, otherwise {@code NULL}.
   */
  @Nonnull
  static Column propagateNull(@Nonnull final Column result, @Nonnull final Column... inputs) {
    Column allNotNull = inputs[0].isNotNull();
    for (int i = 1; i < inputs.length; i++) {
      allNotNull = allNotNull.and(inputs[i].isNotNull());
    }
    return when(allNotNull, result);
  }
}
