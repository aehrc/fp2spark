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
import static au.csiro.fhirpath.spark.SparkDefs.ternary;
import static au.csiro.fhirpath.spark.SparkDefs.unary;
import static org.apache.spark.sql.functions.call_function;
import static org.apache.spark.sql.functions.concat;
import static org.apache.spark.sql.functions.lit;

import au.csiro.fhirpath.spark.SparkOpContext;
import au.csiro.fhirpath.spark.SparkOperationRegistry;
import jakarta.annotation.Nonnull;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.functions;

/**
 * String function registrations (length, upper, lower, trim, startsWith, endsWith, contains,
 * indexOf, substring, replace, matches, replaceMatches, split, join, toChars).
 */
public final class StringOps {

  private StringOps() {}

  /** Registers all string functions into the given registry. */
  public static void register(@Nonnull final SparkOperationRegistry registry) {
    // Simple unary functions
    registry.register("length", unary(functions::length));
    registry.register("upper", unary(functions::upper));
    registry.register("lower", unary(functions::lower));
    registry.register("trim", unary(functions::trim));
    registry.register("toChars", unary(StringSupport::toChars));

    // Binary string functions
    registry.register("startsWith", binary(Column::startsWith));
    registry.register("endsWith", binary(Column::endsWith));
    registry.register("contains", binary(Column::contains));
    // FHIRPath spec §5.6.8 requires DOTALL semantics (. matches newlines).
    registry.register(
        "matches",
        binary((input, pattern) -> functions.rlike(input, concat(lit("(?s)"), pattern))));
    registry.register("indexOf", binary(StringSupport::indexOf));

    // Ternary string functions
    registry.register("substring", ternary(StringSupport::substring));
    registry.register("replace", ternary(StringSupport::replace));
    registry.register("replaceMatches", ternary(functions::regexp_replace));

    // split(separator): literal string separator quoted for Spark's regex-based split
    registry.register(
        "split", ctx -> functions.split(ctx.arg(0), StringSupport.quoteLiteral(ctx.arg(1))));

    // join([separator]): *STRING → ?STRING (needs cardinality dispatch)
    registry.register("join", StringOps::generateJoin);
  }

  /**
   * join([separator]): joins a string collection into a single string.
   *
   * <p>For arrays: uses Spark's array_join(). For singular: returns the value as-is.
   */
  @Nonnull
  private static Column generateJoin(@Nonnull final SparkOpContext ctx) {
    final Column sep = functions.coalesce(ctx.arg(1), lit(""));
    return ctx.collectionArg(0).apply(c -> call_function("array_join", c, sep), c -> c);
  }
}
