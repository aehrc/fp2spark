package com.example.fhirpath.test;

import com.example.fhirpath.typing.CodingValue;
import com.example.fhirpath.typing.QuantityValue;
import com.example.fhirpath.typing.ResourceType;
import com.example.fhirpath.typing.TemporalValue;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

      // Step 3: Convert wrapper types to JSON-friendly forms, then serialize to JSON
      final Map<String, Object> jsonFriendlyData = toJsonFriendly(resource.getData());
      final String jsonData = JSON_MAPPER.writeValueAsString(jsonFriendlyData);

      // Step 4: Create single-element JSON list
      final String jsonArray = "[" + jsonData + "]";

      // Step 5: Create Dataset from JSON with flat schema
      return spark
          .read()
          .schema(fieldsSchema)
          .json(spark.createDataset(List.of(jsonArray), org.apache.spark.sql.Encoders.STRING()));
    } catch (Exception e) {
      throw new RuntimeException(
          "Failed to convert ResourceTestData to Dataset: " + e.getMessage(), e);
    }
  }

  /**
   * Converts a Map containing wrapper types to a JSON-friendly Map.
   *
   * <p>Wrapper types are converted as follows:
   *
   * <ul>
   *   <li>{@link TemporalValue} (DateValue, TimeValue, DateTimeValue) → plain string
   *   <li>{@link QuantityValue} → Map with {value, unit, system, code} fields
   *   <li>Nested Maps and Lists are processed recursively
   * </ul>
   */
  @Nonnull
  private static Map<String, Object> toJsonFriendly(@Nonnull final Map<String, Object> data) {
    final Map<String, Object> result = new HashMap<>();
    for (final Map.Entry<String, Object> entry : data.entrySet()) {
      result.put(entry.getKey(), convertValue(entry.getValue()));
    }
    return result;
  }

  @Nullable
  @SuppressWarnings("unchecked")
  private static Object convertValue(@Nullable final Object value) {
    return switch (value) {
      case null -> null;
      case TemporalValue tv -> tv.value();
      case QuantityValue qv ->
          Map.of("value", qv.value(), "unit", qv.unit(), "system", qv.system(), "code", qv.code());
      case CodingValue cv -> codingValueToMap(cv);
      case List<?> list -> list.stream().map(ResourceDatasetConverter::convertValue).toList();
      case Map<?, ?> map -> toJsonFriendly((Map<String, Object>) map);
      default -> value;
    };
  }

  @Nonnull
  private static Map<String, Object> codingValueToMap(@Nonnull final CodingValue cv) {
    final Map<String, Object> map = new HashMap<>();
    map.put("system", cv.system());
    map.put("code", cv.code());
    map.put("version", cv.version());
    map.put("display", cv.display());
    map.put("userSelected", cv.userSelected());
    return map;
  }
}
