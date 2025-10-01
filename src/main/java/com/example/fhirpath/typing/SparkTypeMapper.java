package com.example.fhirpath.typing;

public final class SparkTypeMapper {
    private SparkTypeMapper() {}

    public static String toSparkTypeName(Type t) {
        return switch (t) {
            case INTEGER -> "int";
            case DECIMAL, QUANTITY -> "double";
            case DATE -> "date";
            case DATE_TIME -> "timestamp";
            case BOOLEAN -> "boolean";
            case STRING, UNKNOWN -> "string";
        };
    }
}

