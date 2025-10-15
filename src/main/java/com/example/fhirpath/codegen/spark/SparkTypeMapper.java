package com.example.fhirpath.codegen.spark;

import com.example.fhirpath.typing.CollectionType;
import com.example.fhirpath.typing.PrimitiveType;
import com.example.fhirpath.typing.Type;
import org.apache.spark.sql.types.DataType;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.DecimalType;

public final class SparkTypeMapper {

    public static final DecimalType DECIMAL_TYPE = DataTypes.createDecimalType(38, 6);

    private SparkTypeMapper() {
    }

    public static DataType toSparkDataType(Type t) {
        if (t instanceof CollectionType ct) {
            return DataTypes.createArrayType(toSparkDataType(ct.elementType()));
        } else if (t instanceof PrimitiveType pt) {
            return switch (pt) {
                case INTEGER -> DataTypes.IntegerType;
                case DECIMAL -> DECIMAL_TYPE;
                case BOOLEAN -> DataTypes.BooleanType;
                case STRING -> DataTypes.StringType;
                case NULL -> DataTypes.NullType;
                default -> throw new IllegalArgumentException("Unknown primitive type " + t);
            };
        } else {
            throw new IllegalArgumentException("Unsupported non primitive type " + t);
        }
    }
}
