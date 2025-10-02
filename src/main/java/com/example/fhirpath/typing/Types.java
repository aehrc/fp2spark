package com.example.fhirpath.typing;

/**
 * Common Type constants for backward compatibility and convenience
 */
public final class Types {
    public static final Type INTEGER = PrimitiveType.INTEGER;
    public static final Type DECIMAL = PrimitiveType.DECIMAL;
    public static final Type QUANTITY = PrimitiveType.QUANTITY;
    public static final Type DATE = PrimitiveType.DATE;
    public static final Type DATE_TIME = PrimitiveType.DATE_TIME;
    public static final Type BOOLEAN = PrimitiveType.BOOLEAN;
    public static final Type STRING = PrimitiveType.STRING;
    public static final Type NULL = PrimitiveType.NULL;
    public static final Type UNKNOWN = PrimitiveType.UNKNOWN;

    private Types() {
        // Utility class
    }
}
