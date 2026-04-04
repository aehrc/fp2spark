package com.example.fhirpath.codegen.spark;

import com.example.fhirpath.typing.FhirPrimitiveType;
import com.example.fhirpath.typing.SystemType;
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

  /** Field indices for the quantity struct: {@code (value, unit, system, code)}. */
  public static final int Q_VALUE = 0;

  public static final int Q_UNIT = 1;
  public static final int Q_SYSTEM = 2;
  public static final int Q_CODE = 3;

  public static final StructType CODING_TYPE =
      DataTypes.createStructType(
          new StructField[] {
            DataTypes.createStructField("system", DataTypes.StringType, true),
            DataTypes.createStructField("code", DataTypes.StringType, true),
            DataTypes.createStructField("version", DataTypes.StringType, true),
            DataTypes.createStructField("display", DataTypes.StringType, true),
            DataTypes.createStructField("userSelected", DataTypes.BooleanType, true),
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

  private static DataType toSparkElementType(final Type t) {
    if (t instanceof FhirPrimitiveType fpt) {
      return toSparkElementType(fpt.getSystemType());
    }
    if (t instanceof SystemType pt) {
      return switch (pt) {
        case INTEGER -> DataTypes.IntegerType;
        case DECIMAL -> DECIMAL_TYPE;
        case BOOLEAN -> DataTypes.BooleanType;
        case STRING -> DataTypes.StringType;
        case DATE, DATE_TIME, TIME -> DataTypes.StringType;
        case QUANTITY -> QUANTITY_TYPE;
        case CODING -> CODING_TYPE;
        case NULL -> DataTypes.NullType;
        default -> throw new IllegalArgumentException("Unknown primitive type " + t);
      };
    } else {
      throw new IllegalArgumentException("Unsupported non primitive type " + t);
    }
  }
}
