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
package au.csiro.fhirpath.compat.yaml;

import au.csiro.fhirpath.compat.yaml.format.ExcludeRule;
import jakarta.annotation.Nonnull;
import java.util.Optional;
import org.apache.spark.sql.SparkSession;

/** Runs a single YAML-derived FHIRPath test case against a Spark session. */
public interface YamlTestExecutor {

  /** Human-readable display name for this test case (shown in JUnit output). */
  @Nonnull
  String getDisplayName();

  /** The exclusion rule matching this test case, if any. */
  @Nonnull
  Optional<ExcludeRule> getExclusion();

  /** Executes the test case. May throw AssertionError / RuntimeException on failure. */
  void check(@Nonnull SparkSession spark);
}
