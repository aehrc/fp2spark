package com.example.fhirpath.test;

import com.example.fhirpath.typing.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Nonnull;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.types.DataType;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.StructField;
import org.apache.spark.sql.types.StructType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Converts Map-based resource test data to Spark Datasets.
 *
 * <p>This class takes a {@link ResourceTestData} instance (containing Map data and type name)
 * and creates a Spark Dataset with the appropriate schema inferred from the ResourceType.
 *
 * <p><b>Conversion Strategy:</b>
 * <ol>
 *   <li>Infer ResourceType from Map data using {@link ResourceTypeInference}</li>
 *   <li>Convert ResourceType to Spark StructType schema</li>
 *   <li>Serialize Map data to JSON</li>
 *   <li>Create Dataset by reading JSON with inferred schema</li>
 * </ol>
 *
 * <p><b>Example:</b>
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

    /**
     * Convert ResourceTestData to a Spark Dataset.
     *
     * <p>Creates a Dataset with a single column named after the resource type,
     * containing a struct with all the resource fields.
     *
     * @param spark    The SparkSession to use
     * @param resource The resource test data to convert
     * @return A Dataset with a single row containing the resource data
     */
    @Nonnull
    static Dataset<Row> toDataset(
            @Nonnull final SparkSession spark,
            @Nonnull final ResourceTestData resource
    ) {
        try {
            // Step 1: Infer ResourceType from Map data
            final ResourceType resourceType = resource.inferResourceType();

            // Step 2: Convert ResourceType to Spark schema (struct of fields)
            final StructType fieldsSchema = toSparkSchema(resourceType);

            // Step 3: Create outer schema with resource type name as column
            final StructType outerSchema = DataTypes.createStructType(new StructField[]{
                    DataTypes.createStructField(resource.getResourceTypeName(), fieldsSchema, true)
            });

            // Step 4: Wrap the data in an outer object with resource type name as key
            final Map<String, Object> wrappedData = Map.of(
                    resource.getResourceTypeName(),
                    resource.getData()
            );

            // Step 5: Convert wrapped Map to JSON string
            final String jsonData = JSON_MAPPER.writeValueAsString(wrappedData);

            // Step 6: Create single-element JSON list
            final String jsonArray = "[" + jsonData + "]";

            // Step 7: Create Dataset from JSON with outer schema
            final Dataset<Row> dataset = spark.read()
                    .schema(outerSchema)
                    .json(spark.createDataset(List.of(jsonArray), org.apache.spark.sql.Encoders.STRING()));

            return dataset;
        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to convert ResourceTestData to Dataset: " + e.getMessage(),
                    e
            );
        }
    }

    /**
     * Convert a ResourceType to a Spark StructType schema.
     *
     * @param resourceType The ResourceType to convert
     * @return The corresponding Spark StructType
     */
    @Nonnull
    private static StructType toSparkSchema(@Nonnull final ResourceType resourceType) {
        final List<StructField> fields = new ArrayList<>();

        for (final FieldSpec fieldSpec : resourceType.getFields()) {
            final String fieldName = fieldSpec.getName();
            final DataType sparkType = toSparkDataType(fieldSpec.getShape());
            final boolean nullable = true; // FHIRPath fields can be empty
            fields.add(DataTypes.createStructField(fieldName, sparkType, nullable));
        }

        return DataTypes.createStructType(fields);
    }

    /**
     * Convert a FHIRPath Shape to a Spark DataType.
     *
     * @param shape The Shape to convert
     * @return The corresponding Spark DataType
     */
    @Nonnull
    private static DataType toSparkDataType(@Nonnull final Shape shape) {
        final Type elementType = shape.elementType();
        final DataType baseType = toSparkBaseType(elementType);

        // If cardinality is MANY, wrap in ArrayType
        if (shape.isMany()) {
            return DataTypes.createArrayType(baseType, true);
        }

        return baseType;
    }

    /**
     * Convert a FHIRPath Type to a Spark base DataType (non-array).
     *
     * @param type The Type to convert
     * @return The corresponding Spark DataType
     */
    @Nonnull
    private static DataType toSparkBaseType(@Nonnull final Type type) {
        if (type instanceof PrimitiveType primitiveType) {
            return switch (primitiveType) {
                case INTEGER -> DataTypes.IntegerType;
                case DECIMAL -> DataTypes.DoubleType;
                case BOOLEAN -> DataTypes.BooleanType;
                case STRING -> DataTypes.StringType;
                case NULL -> DataTypes.NullType;
                case ANY -> DataTypes.StringType; // Default to String for ANY
            };
        }

        if (type instanceof ComplexType complexType) {
            return toSparkSchema(complexType);
        }

        throw new IllegalArgumentException("Unsupported type: " + type.getClass().getName());
    }

    /**
     * Convert a ComplexType to a Spark StructType.
     *
     * @param complexType The ComplexType to convert
     * @return The corresponding Spark StructType
     */
    @Nonnull
    private static StructType toSparkSchema(@Nonnull final ComplexType complexType) {
        final List<StructField> fields = new ArrayList<>();

        for (final FieldSpec fieldSpec : complexType.getFields()) {
            final String fieldName = fieldSpec.getName();
            final DataType sparkType = toSparkDataType(fieldSpec.getShape());
            final boolean nullable = true;
            fields.add(DataTypes.createStructField(fieldName, sparkType, nullable));
        }

        return DataTypes.createStructType(fields);
    }
}
