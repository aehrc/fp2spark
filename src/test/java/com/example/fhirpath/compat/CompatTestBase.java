package com.example.fhirpath.compat;

import com.example.fhirpath.test.FhirPathTestBuilder;
import com.example.fhirpath.test.FhirPathTestExecutor;
import com.example.fhirpath.test.SparkSessionFactory;
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
    return new CompatTestBuilder(new FhirPathTestBuilder(executor));
  }
}
