package com.example.fhirpath.test;

import org.apache.spark.sql.SparkSession;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;

import jakarta.annotation.Nonnull;

/**
 * Base class for FHIRPath DSL tests providing SparkSession setup and test builder factory.
 *
 * <p>Test classes extending this base can use the fluent DSL to create readable,
 * well-organized tests:
 *
 * <pre>{@code
 * @TestFactory
 * Stream<DynamicTest> testArithmetic() {
 *     return builder()
 *         .group("Integer addition")
 *         .testEquals(15, "5 + 10", "Basic addition")
 *         .testEquals(10, "5 + 5", "Equal operands")
 *         .group("Decimal arithmetic")
 *         .testEquals(15.3, "5.1 + 10.2", "Decimal addition")
 *         .build();
 * }
 * }</pre>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class FhirPathTestBase {

    protected SparkSession spark;

    @BeforeAll
    void setupSpark() {
        spark = SparkSession.builder()
                .appName("fhirpath-test")
                .master("local[*]")
                .config("spark.ui.enabled", "false")
                .config("spark.sql.warehouse.dir", "target/spark-warehouse")
                .getOrCreate();
    }

    @AfterAll
    void teardownSpark() {
        if (spark != null) {
            spark.stop();
        }
    }

    /**
     * Create a new test builder for constructing FHIRPath test cases.
     *
     * @return A new FhirPathTestBuilder instance
     */
    @Nonnull
    protected FhirPathTestBuilder builder() {
        return new FhirPathTestBuilder(spark);
    }
}
