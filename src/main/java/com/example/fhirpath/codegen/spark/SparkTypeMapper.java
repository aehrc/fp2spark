package com.example.fhirpath.codegen.spark;

import com.example.fhirpath.typing.FhirPrimitiveType;
import com.example.fhirpath.typing.PrimitiveType;
import com.example.fhirpath.typing.Shape;
import com.example.fhirpath.typing.Type;
import org.apache.spark.sql.types.DataType;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.DecimalType;
import org.apache.spark.sql.types.StructField;
import org.apache.spark.sql.types.StructType;

/** Maps FHIRPath shapes to Spark DataTypes. */
public final class SparkTypeMapper {

  public static final DecimalType DECIMAL_TYPE = DataTypes.createDecimalType(38, 6);

  public static final StructType QUANTITY_TYPE =
      DataTypes.createStructType(
          new StructField[] {
            DataTypes.createStructField("value", DECIMAL_TYPE, true),
            DataTypes.createStructField("unit", DataTypes.StringType, true),
            DataTypes.createStructField("system", DataTypes.StringType, true),
            DataTypes.createStructField("code", DataTypes.StringType, true),
          });

  private SparkTypeMapper() {}

  /**
   * Maps a FHIRPath shape to a Spark DataType. MANY cardinality maps to ArrayType, SINGLE maps to
   * the element type directly.
   */
  public static DataType toSparkDataType(final Shape shape) {
    final DataType elementType = toSparkElementType(shape.elementType());
    return shape.isMany() ? DataTypes.createArrayType(elementType) : elementType;
  }

  /**
   * Maps a FHIRPath type (without cardinality) to a Spark DataType.
   *
   * @deprecated Use toSparkDataType(Shape) instead
   */
  @Deprecated
  public static DataType toSparkDataType(final Type t) {
    return toSparkElementType(t);
  }

  private static DataType toSparkElementType(final Type t) {
    if (t instanceof FhirPrimitiveType fpt) {
      return toSparkElementType(fpt.getSystemType());
    }
    if (t instanceof PrimitiveType pt) {
      return switch (pt) {
        case INTEGER -> DataTypes.IntegerType;
        case DECIMAL -> DECIMAL_TYPE;
        case BOOLEAN -> DataTypes.BooleanType;
        case STRING -> DataTypes.StringType;
        case DATE, DATE_TIME, TIME -> DataTypes.StringType;
        case QUANTITY -> QUANTITY_TYPE;
        case NULL -> DataTypes.NullType;
        default -> throw new IllegalArgumentException("Unknown primitive type " + t);
      };
    } else {
      throw new IllegalArgumentException("Unsupported non primitive type " + t);
    }
  }
}
