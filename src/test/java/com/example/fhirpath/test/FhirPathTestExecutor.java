package com.example.fhirpath.test;

import com.example.fhirpath.FhirPath;
import com.example.fhirpath.typing.ResourceType;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.math.BigDecimal;
import java.util.List;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Executes individual FHIRPath test cases by compiling expressions, running Spark queries,
 * extracting results, and verifying outcomes against assertions.
 */
class FhirPathTestExecutor {

  private static final Logger log = LoggerFactory.getLogger(FhirPathTestExecutor.class);

  private final SparkSession spark;

  FhirPathTestExecutor(@Nonnull final SparkSession spark) {
    this.spark = spark;
  }

  void executeTest(@Nonnull final TestCase testCase) {
    log.debug("Executing test: {}", testCase.description());

    try {
      // Determine if we need resource type info
      final ResourceType resourceType =
          testCase.resource() != null ? testCase.resource().inferResourceType() : null;

      // Use FhirPath API to compile expression
      final Column column;
      if (resourceType != null && testCase.context() != null) {
        column =
            FhirPath.toColumn(testCase.expression(), testCase.context().expression(), resourceType);
      } else if (resourceType != null) {
        column = FhirPath.toColumn(testCase.expression(), resourceType);
      } else if (testCase.context() != null) {
        column = FhirPath.toColumn(testCase.expression(), testCase.context().expression());
      } else {
        column = FhirPath.toColumn(testCase.expression());
      }

      // Execute with Spark
      final Dataset<Row> inputDataset =
          testCase.resource() != null
              ? ResourceDatasetConverter.toDataset(spark, testCase.resource())
              : spark.range(1).toDF();

      final String resultAlias =
          testCase.resource() != null ? testCase.resource().getResourceTypeName() : "result";

      final Dataset<Row> result = inputDataset.select(column.alias(resultAlias));

      // Extract and normalize result value
      final Object actualValue = extractResult(result);

      // Perform assertion
      testCase.assertion().assertResult(actualValue);

    } catch (final Exception exception) {
      // Delegate error handling to assertion (unified interface)
      testCase.assertion().assertError(exception);
    }
  }

  @Nullable
  private Object extractResult(@Nonnull final Dataset<Row> result) {
    final List<Row> rows = result.collectAsList();
    if (rows.isEmpty() || rows.get(0).isNullAt(0)) {
      return null;
    }

    final Object value = rows.get(0).get(0);

    // Handle Spark arrays (convert to Java List and normalize elements)
    if (value instanceof scala.collection.Seq<?> seq) {
      final List<?> javaList = scala.jdk.javaapi.CollectionConverters.asJava(seq);
      return javaList.stream().map(this::normalizeValue).toList();
    }

    return normalizeValue(value);
  }

  @Nullable
  private Object normalizeValue(@Nullable final Object value) {
    if (value instanceof BigDecimal bd) {
      return bd.stripTrailingZeros();
    }
    return value;
  }
}
