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
package au.csiro.fhirpath.compat;

import au.csiro.fhirpath.test.FhirPathTestBuilder;
import au.csiro.fhirpath.test.FhirPathTestExecutor;
import au.csiro.fhirpath.test.SparkSessionFactory;
import jakarta.annotation.Nonnull;
import org.apache.spark.sql.SparkSession;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;

/**
 * Base class for Pathling compatibility tests.
 *
 * <p>Provides a {@link CompatTestBuilder} via {@link #builder()}, bridging Pathling's DSL
 * conventions to fp2sql's test infrastructure.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class CompatTestBase {

  protected SparkSession spark;

  @BeforeAll
  void setupSpark() {
    spark = SparkSessionFactory.createTestSession();
  }

  @AfterAll
  void teardownSpark() {
    if (spark != null) {
      spark.stop();
    }
  }

  /**
   * Create a new compatibility test builder.
   *
   * @return A CompatTestBuilder that provides Pathling's DSL API
   */
  @Nonnull
  protected CompatTestBuilder builder() {
    final FhirPathTestExecutor executor = new FhirPathTestExecutor(spark);
    return new CompatTestBuilder(new FhirPathTestBuilder(executor), getClass());
  }
}
