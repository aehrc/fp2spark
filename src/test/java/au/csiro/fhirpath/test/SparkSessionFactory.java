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
