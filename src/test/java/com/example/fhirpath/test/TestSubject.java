package com.example.fhirpath.test;

import com.example.fhirpath.typing.ResourceType;
import jakarta.annotation.Nonnull;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;

/**
 * Abstraction for test data sources used in FHIRPath test cases.
 *
 * <p>Unifies Map-based test data ({@link MapTestSubject}) and HAPI FHIR resource objects ({@link
 * HapiTestSubject}) behind a common interface.
 */
sealed interface TestSubject permits MapTestSubject, HapiTestSubject {

  /** Returns the resource type name (e.g., "Patient"). */
  @Nonnull
  String getResourceTypeName();

  /** Returns the resource type definition for the Analyzer. */
  @Nonnull
  ResourceType getResourceType();

  /** Converts the test data to a Spark Dataset. */
  @Nonnull
  Dataset<Row> toDataset(@Nonnull SparkSession spark);
}
