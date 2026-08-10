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

import static au.csiro.fhirpath.spark.SparkDefs.binary;
import static au.csiro.fhirpath.spark.SparkDefs.byResultType;
import static au.csiro.fhirpath.spark.SparkDefs.types;
import static au.csiro.fhirpath.spark.SparkDefs.unary;
import static au.csiro.fhirpath.typing.SystemType.DECIMAL;
import static au.csiro.fhirpath.typing.SystemType.INTEGER;
import static au.csiro.fhirpath.typing.SystemType.QUANTITY;

import au.csiro.fhirpath.spark.SparkOperationRegistry;
import jakarta.annotation.Nonnull;
import org.apache.spark.sql.functions;

/**
 * Math function registrations (abs, ceiling, floor, round, truncate, exp, ln, log, power, sqrt).
 *
 * <p>Delegates column-level logic to {@link MathSupport}.
 */
public final class MathOps {

  private MathOps() {}

  /**
   * Registers all math functions into the given registry.
   *
   * @param registry the registry to register operations into
   */
  public static void register(@Nonnull final SparkOperationRegistry registry) {
    registry.register(
        "abs",
        byResultType()
            .when(types(INTEGER, DECIMAL), unary(functions::abs))
            .when(types(QUANTITY), unary(MathSupport::quantityAbs)));
    registry.register("ceiling", unary(MathSupport::ceiling));
    registry.register("floor", unary(MathSupport::floor));
    registry.register("truncate", unary(MathSupport::truncate));
    registry.register("round", binary(MathSupport::round));
    registry.register("exp", unary(functions::exp));
    registry.register("ln", unary(MathSupport::ln));
    registry.register("log", binary(MathSupport::logarithm));
    registry.register(
        "power",
        byResultType()
            .when(types(INTEGER), binary(MathSupport::integerPower))
            .when(types(DECIMAL), binary(MathSupport::decimalPower)));
    registry.register("sqrt", unary(MathSupport::sqrt));
  }
}
