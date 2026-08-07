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
package au.csiro.fhirpath.test;

import jakarta.annotation.Nonnull;
import org.apache.spark.sql.SparkSession;

/**
 * Factory for creating SparkSession instances configured for testing.
 *
 * <p>This factory centralizes SparkSession configuration, making it reusable across different test
 * contexts (unit tests, integration tests, benchmarks).
 *
 * <p><b>Configuration:</b>
 *
 * <ul>
 *   <li>Master: local[*] (all available cores)
 *   <li>UI: disabled (no web UI during tests)
 *   <li>Warehouse: target/spark-warehouse (Maven build directory)
 * </ul>
 *
 * <p><b>Usage:</b>
 *
 * <pre>{@code
 * SparkSession spark = SparkSessionFactory.createTestSession();
 * try {
 *     // Run tests
 * } finally {
 *     spark.stop();
 * }
 * }</pre>
 */
public class SparkSessionFactory {

  /**
   * Create a SparkSession configured for testing.
   *
   * <p>This method uses {@code getOrCreate()} to reuse existing sessions when possible, which
   * improves test performance.
   *
   * @return A SparkSession configured for test execution
   */
  @Nonnull
  public static SparkSession createTestSession() {
    return SparkSession.builder()
        .appName("fhirpath-test")
        .master("local[*]")
        .config("spark.ui.enabled", "false")
        .config("spark.sql.warehouse.dir", "target/spark-warehouse")
        .getOrCreate();
  }

  /** Private constructor to prevent instantiation. */
  private SparkSessionFactory() {
    throw new UnsupportedOperationException("Utility class");
  }
}
