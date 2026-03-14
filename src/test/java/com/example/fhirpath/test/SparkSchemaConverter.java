package com.example.fhirpath.test;

import com.example.fhirpath.codegen.spark.SparkTypeMapper;
import com.example.fhirpath.typing.FhirPrimitiveType;
import com.example.fhirpath.typing.FieldSpec;
import com.example.fhirpath.typing.InlineComplexType;
import com.example.fhirpath.typing.PrimitiveType;
import com.example.fhirpath.typing.Shape;
import com.example.fhirpath.typing.Type;
import jakarta.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import org.apache.spark.sql.types.DataType;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.StructField;
import org.apache.spark.sql.types.StructType;

/**
 * Converts FHIRPath type system elements to Spark SQL schema types.
 *
 * <p>This class provides type-safe conversion from the FHIRPath type system (ResourceType,
 * ComplexType, Shape, PrimitiveType) to Spark's DataType hierarchy (StructType, DataType,
 * ArrayType).
 *
 * <p><b>Conversion Rules:</b>
 *
 * <ul>
 *   <li>PrimitiveType.INTEGER → IntegerType
 *   <li>PrimitiveType.DECIMAL → DoubleType
 *   <li>PrimitiveType.BOOLEAN → BooleanType
 *   <li>PrimitiveType.STRING → StringType
 *   <li>PrimitiveType.NULL → NullType
 *   <li>PrimitiveType.ANY → StringType (fallback)
 *   <li>ComplexType → StructType (recursive)
 *   <li>Shape.MANY → ArrayType wrapper
 * </ul>
 *
 * <p><b>Example:</b>
 *
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
  StructType toStructType(@Nonnull final InlineComplexType inlineType) {
    final List<StructField> fields = new ArrayList<>();

    for (final FieldSpec fieldSpec : inlineType.getFields()) {
      final String fieldName = fieldSpec.getName();
      final DataType sparkType = toDataType(fieldSpec.getShape());
      fields.add(DataTypes.createStructField(fieldName, sparkType, true));
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
    if (type instanceof FhirPrimitiveType fpt) {
      return toBaseType(fpt.getSystemType());
    }

    if (type instanceof PrimitiveType primitiveType) {
      return switch (primitiveType) {
        case INTEGER -> DataTypes.IntegerType;
        case DECIMAL -> DataTypes.DoubleType;
        case BOOLEAN -> DataTypes.BooleanType;
        case STRING -> DataTypes.StringType;
        case DATE, DATE_TIME, TIME -> DataTypes.StringType;
        case QUANTITY -> SparkTypeMapper.QUANTITY_TYPE;
        case NULL -> DataTypes.NullType;
        case ANY -> DataTypes.StringType; // Default to String for ANY
      };
    }

    if (type instanceof InlineComplexType inlineComplexType) {
      return toStructType(inlineComplexType);
    }

    throw new IllegalArgumentException("Unsupported type: " + type.getClass().getName());
  }
}
