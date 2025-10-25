package com.example.fhirpath.test;

import com.example.fhirpath.FhirPath;
import com.example.fhirpath.typing.ResourceType;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.math.BigDecimal;
import java.util.List;

/**
 * Executes individual FHIRPath test cases by compiling expressions, running Spark queries,
 * extracting results, and verifying outcomes against assertions.
 *
 * <p>This class is responsible for the complete test execution pipeline:
 * <ol>
 *   <li>Compile FHIRPath expression using {@link FhirPath#toColumn(String)}</li>
 *   <li>Execute generated SQL with Spark</li>
 *   <li>Extract and normalize results</li>
 *   <li>Verify results against test assertions</li>
 * </ol>
 */
class FhirPathTestExecutor {

    private static final Logger log = LoggerFactory.getLogger(FhirPathTestExecutor.class);

    private final SparkSession spark;

    /**
     * Create a new test executor.
     *
     * @param spark The SparkSession for query execution
     */
    FhirPathTestExecutor(@Nonnull SparkSession spark) {
        this.spark = spark;
    }

    /**
     * Execute a single test case.
     *
     * @param testCase The test case to execute
     * @throws AssertionError if the test fails
     */
    void executeTest(@Nonnull TestCase testCase) {
        log.debug("Executing test: {}", testCase.description());

        try {
            // Determine if we need resource type info
            final ResourceType resourceType = testCase.resource() != null
                    ? testCase.resource().inferResourceType()
                    : null;

            // Use FhirPath API to compile expression
            // Choose appropriate overload based on context and resource presence
            final Column column;
            if (resourceType != null && testCase.context() != null) {
                // Both resource and context
                column = FhirPath.toColumn(
                        testCase.expression(),
                        testCase.context().expression(),
                        resourceType
                );
            } else if (resourceType != null) {
                // Resource only
                column = FhirPath.toColumn(testCase.expression(), resourceType);
            } else if (testCase.context() != null) {
                // Context only
                column = FhirPath.toColumn(
                        testCase.expression(),
                        testCase.context().expression()
                );
            } else {
                // Neither - literal expression
                column = FhirPath.toColumn(testCase.expression());
            }

            // Execute with Spark
            // If resource is provided, use the resource dataset
            // Otherwise use range(1) for literal expressions
            final Dataset<Row> inputDataset = testCase.resource() != null
                    ? ResourceDatasetConverter.toDataset(spark, testCase.resource())
                    : spark.range(1).toDF();

            // Select result column (use resource type name as column name if available)
            final String resultAlias = testCase.resource() != null
                    ? testCase.resource().getResourceTypeName()
                    : "result";

            final Dataset<Row> result = inputDataset.select(column.alias(resultAlias));

            // Extract and normalize result value
            Object actualValue = extractResult(result);

            // Perform assertion
            testCase.assertion().assertResult(actualValue);

        } catch (Exception exception) {
            // Delegate error handling to assertion (unified interface)
            testCase.assertion().assertError(exception);
        }
    }

    /**
     * Extract the result value from a Spark query result and normalize for comparison.
     * <p>
     * Normalizes BigDecimal values by stripping trailing zeros for consistent comparison.
     *
     * @param result The Spark Dataset containing the query result
     * @return The extracted value (null for empty collections)
     */
    @Nullable
    private Object extractResult(@Nonnull Dataset<Row> result) {
        List<Row> rows = result.collectAsList();
        if (rows.isEmpty() || rows.get(0).isNullAt(0)) {
            return null;
        }

        Object value = rows.get(0).get(0);

        // Handle Spark arrays (convert to Java List and normalize elements)
        if (value instanceof scala.collection.Seq<?> seq) {
            List<?> javaList = scala.jdk.javaapi.CollectionConverters.asJava(seq);
            // Normalize BigDecimal values in list
            return javaList.stream()
                    .map(this::normalizeValue)
                    .toList();
        }

        return normalizeValue(value);
    }

    /**
     * Normalize a value for comparison.
     * <p>
     * Strips trailing zeros from BigDecimal values to ensure consistent comparison
     * (e.g., 6.3 and 6.300000 are considered equal).
     *
     * @param value The value to normalize
     * @return The normalized value
     */
    @Nullable
    private Object normalizeValue(@Nullable Object value) {
        if (value instanceof BigDecimal bd) {
            return bd.stripTrailingZeros();
        }
        return value;
    }
}
