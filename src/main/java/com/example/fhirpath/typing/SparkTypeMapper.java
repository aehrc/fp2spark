package com.example.fhirpath.typing;

public final class SparkTypeMapper {

    public static final String DECIMAL_TYPE = "DECIMAL(38, 6)";

    private SparkTypeMapper() {}

    public static String toSparkTypeName(Type t) {
        return switch (t) {
            case INTEGER -> "int";
            case DECIMAL -> DECIMAL_TYPE;
            case DATE -> "date";
            case DATE_TIME -> "timestamp";
            case BOOLEAN -> "boolean";
            case STRING, UNKNOWN -> "string";
            case NULL -> "void";
            default -> throw new IllegalArgumentException("Unknown type " + t);
        };
    }
}

