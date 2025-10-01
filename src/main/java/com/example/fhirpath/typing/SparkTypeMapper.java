package com.example.fhirpath.typing;

import org.apache.spark.sql.types.DataType;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.DecimalType;

public final class SparkTypeMapper {

    public static final DecimalType DECIMAL_TYPE = DataTypes.createDecimalType(38, 6);

    private SparkTypeMapper() {
    }

    public static DataType toSparkDataType(Type t) {
        return switch (t) {
            case INTEGER -> DataTypes.IntegerType;
            case DECIMAL -> DECIMAL_TYPE;
            case BOOLEAN -> DataTypes.BooleanType;
            case STRING -> DataTypes.StringType;
            case NULL -> DataTypes.NullType;
            default -> throw new IllegalArgumentException("Unknown type " + t);
        };
    }
}
