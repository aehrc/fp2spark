package com.example.fhirpath.test;

import com.example.fhirpath.typing.ResourceType;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Nonnull;
import java.util.List;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.types.StructType;

/**
 * Converts Map-based resource test data to Spark Datasets.
 *
 * <p>This class takes a {@link ResourceTestData} instance (containing Map data and type name) and
 * creates a Spark Dataset with the appropriate schema inferred from the ResourceType.
 *
 * <p><b>Conversion Strategy:</b>
 *
 * <ol>
 *   <li>Infer ResourceType from Map data using {@link ResourceTypeInference}
 *   <li>Convert ResourceType to Spark StructType schema
 *   <li>Serialize Map data to JSON
 *   <li>Create Dataset by reading JSON with inferred schema
 * </ol>
 *
 * <p><b>Example:</b>
 *
 * <pre>{@code
 * ResourceTestData testData = ResourceTestData.of("Patient",
 *     new ResourceDataBuilder()
 *         .string("id", "patient-1")
 *         .integer("age", 30)
 *         .build()
 * );
 *
 * Dataset<Row> dataset = ResourceDatasetConverter.toDataset(spark, testData);
 * // Result: Dataset with schema: |id: string, age: int|
 * }</pre>
 */
class ResourceDatasetConverter {

  private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
  private static final SparkSchemaConverter SCHEMA_CONVERTER = new SparkSchemaConverter();

  /**
   * Convert ResourceTestData to a Spark Dataset.
   *
   * <p>Creates a Dataset with a flat schema where resource fields are top-level columns.
   *
   * @param spark The SparkSession to use
   * @param resource The resource test data to convert
   * @return A Dataset with a single row containing the resource data
   */
  @Nonnull
  static Dataset<Row> toDataset(
      @Nonnull final SparkSession spark, @Nonnull final ResourceTestData resource) {
    try {
      // Step 1: Infer ResourceType from Map data
      final ResourceType resourceType = resource.inferResourceType();

      // Step 2: Convert ResourceType to Spark schema (struct of fields)
      // ResourceType for datasets is always an InlineComplexType subtype
      final StructType fieldsSchema =
          SCHEMA_CONVERTER.toStructType(
              (com.example.fhirpath.typing.InlineComplexType) resourceType);

      // Step 3: Convert Map data to JSON string (flat schema — no outer wrapping)
      final String jsonData = JSON_MAPPER.writeValueAsString(resource.getData());

      // Step 4: Create single-element JSON list
      final String jsonArray = "[" + jsonData + "]";

      // Step 5: Create Dataset from JSON with flat schema
      final Dataset<Row> dataset =
          spark
              .read()
              .schema(fieldsSchema)
              .json(
                  spark.createDataset(List.of(jsonArray), org.apache.spark.sql.Encoders.STRING()));

      return dataset;
    } catch (Exception e) {
      throw new RuntimeException(
          "Failed to convert ResourceTestData to Dataset: " + e.getMessage(), e);
    }
  }
}
