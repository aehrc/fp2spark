/*
 * Copyright © 2025-2026 Commonwealth Scientific and Industrial Research
 * Organisation (CSIRO) ABN 41 687 119 230.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package au.csiro.fhirpath.spark;

import au.csiro.fhirpath.typing.FhirPrimitiveType;
import au.csiro.fhirpath.typing.ResolvedReferenceType;
import au.csiro.fhirpath.typing.Shape;
import au.csiro.fhirpath.typing.SystemType;
import au.csiro.fhirpath.typing.Type;
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

  public static final StructType TYPE_INFO_TYPE =
      DataTypes.createStructType(
          new StructField[] {
            DataTypes.createStructField("namespace", DataTypes.StringType, true),
            DataTypes.createStructField("name", DataTypes.StringType, true),
            DataTypes.createStructField("baseType", DataTypes.StringType, true),
          });

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
    if (t instanceof ResolvedReferenceType) {
      // Resolved references are represented as type name strings at runtime
      return DataTypes.StringType;
    }
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
