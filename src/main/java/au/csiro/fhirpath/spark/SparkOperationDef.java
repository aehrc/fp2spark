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
package au.csiro.fhirpath.spark;

import jakarta.annotation.Nonnull;
import org.apache.spark.sql.Column;

/**
 * Functional interface for Spark code generation of a FHIRPath operation.
 *
 * <p>Each operation is a pure function that takes a {@link SparkOpContext} containing evaluated
 * argument columns, the original IR nodes (for metadata like type/cardinality), the result type,
 * and a reference to the code generator (for operations like where/iif that need to evaluate lambda
 * bodies).
 */
@FunctionalInterface
public interface SparkOperationDef {

  /**
   * Generate a Spark Column expression for this operation.
   *
   * @param ctx the operation context containing arguments, metadata, and generator
   * @return the generated Spark Column
   */
  @Nonnull
  Column generate(@Nonnull SparkOpContext ctx);
}
