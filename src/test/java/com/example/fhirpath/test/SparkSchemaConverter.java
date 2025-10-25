package com.example.fhirpath.test;

import com.example.fhirpath.typing.*;
import jakarta.annotation.Nonnull;
import org.apache.spark.sql.types.DataType;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.StructField;
import org.apache.spark.sql.types.StructType;

import java.util.ArrayList;
import java.util.List;

/**
 * Converts FHIRPath type system elements to Spark SQL schema types.
 *
 * <p>This class provides type-safe conversion from the FHIRPath type system
 * (ResourceType, ComplexType, Shape, PrimitiveType) to Spark's DataType hierarchy
 * (StructType, DataType, ArrayType).
 *
 * <p><b>Conversion Rules:</b>
 * <ul>
 *   <li>PrimitiveType.INTEGER → IntegerType</li>
 *   <li>PrimitiveType.DECIMAL → DoubleType</li>
 *   <li>PrimitiveType.BOOLEAN → BooleanType</li>
 *   <li>PrimitiveType.STRING → StringType</li>
 *   <li>PrimitiveType.NULL → NullType</li>
 *   <li>PrimitiveType.ANY → StringType (fallback)</li>
 *   <li>ComplexType → StructType (recursive)</li>
 *   <li>Shape.MANY → ArrayType wrapper</li>
 * </ul>
 *
 * <p><b>Example:</b>
 * <pre>{@code
 * ResourceType patientType = new ResourceType("Patient",
 *     new FieldSpec("id", Shape.single(PrimitiveType.STRING)),
 *     new FieldSpec("age", Shape.single(PrimitiveType.INTEGER))
 * );
 *
 * SparkSchemaConverter converter = new SparkSchemaConverter();
 * StructType schema = converter.toStructType(patientType);
 * // Result: StructType with fields: id(StringType), age(IntegerType)
 * }</pre>
 */
class SparkSchemaConverter {

    /**
     * Convert a ResourceType to a Spark StructType schema.
     *
     * @param resourceType The ResourceType to convert
     * @return The corresponding Spark StructType
     */
    @Nonnull
    StructType toStructType(@Nonnull final ResourceType resourceType) {
        final List<StructField> fields = new ArrayList<>();

        for (final FieldSpec fieldSpec : resourceType.getFields()) {
            final String fieldName = fieldSpec.getName();
            final DataType sparkType = toDataType(fieldSpec.getShape());
            final boolean nullable = true; // FHIRPath fields can be empty
            fields.add(DataTypes.createStructField(fieldName, sparkType, nullable));
        }

        return DataTypes.createStructType(fields);
    }

    /**
     * Convert a ComplexType to a Spark StructType.
     *
     * @param complexType The ComplexType to convert
     * @return The corresponding Spark StructType
     */
    @Nonnull
    StructType toStructType(@Nonnull final ComplexType complexType) {
        final List<StructField> fields = new ArrayList<>();

        for (final FieldSpec fieldSpec : complexType.getFields()) {
            final String fieldName = fieldSpec.getName();
            final DataType sparkType = toDataType(fieldSpec.getShape());
            final boolean nullable = true;
            fields.add(DataTypes.createStructField(fieldName, sparkType, nullable));
        }

        return DataTypes.createStructType(fields);
    }

    /**
     * Convert a FHIRPath Shape to a Spark DataType.
     *
     * <p>If the shape has MANY cardinality, wraps the base type in ArrayType.
     *
     * @param shape The Shape to convert
     * @return The corresponding Spark DataType
     */
    @Nonnull
    DataType toDataType(@Nonnull final Shape shape) {
        final Type elementType = shape.elementType();
        final DataType baseType = toBaseType(elementType);

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
    DataType toBaseType(@Nonnull final Type type) {
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
            return toStructType(complexType);
        }

        throw new IllegalArgumentException("Unsupported type: " + type.getClass().getName());
    }
}
