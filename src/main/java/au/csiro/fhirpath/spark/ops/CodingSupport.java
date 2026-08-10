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

import au.csiro.fhirpath.spark.SparkTypeMapper;
import jakarta.annotation.Nonnull;
import org.apache.spark.sql.Column;

/**
 * Coding equality helpers.
 *
 * <p>Two Codings are equal when their {@code system} and {@code code} fields match. Version,
 * display, and userSelected are not compared for equality (following Pathling semantics).
 *
 * <p>Inputs are cast to {@link SparkTypeMapper#CODING_TYPE} to handle null columns (Spark's VOID
 * type) which cannot have fields extracted directly.
 */
final class CodingSupport {

  private CodingSupport() {}

  @Nonnull
  static Column codingEquals(@Nonnull final Column left, @Nonnull final Column right) {
    final Column l = left.cast(SparkTypeMapper.CODING_TYPE);
    final Column r = right.cast(SparkTypeMapper.CODING_TYPE);
    return l.getField("system")
        .equalTo(r.getField("system"))
        .and(l.getField("code").equalTo(r.getField("code")));
  }
}
