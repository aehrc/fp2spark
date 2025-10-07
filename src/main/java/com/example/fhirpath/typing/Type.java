package com.example.fhirpath.typing;

public interface Type {
    String getName();
    boolean isPrimitive();
    boolean isComplex();
    boolean isCollection();

    default Type effectiveType() {
        return this;
    }

    // Constants for backward compatibility
    Type INTEGER = PrimitiveType.INTEGER;
    Type DECIMAL = PrimitiveType.DECIMAL;
    Type QUANTITY = PrimitiveType.QUANTITY;
    Type DATE = PrimitiveType.DATE;
    Type DATE_TIME = PrimitiveType.DATE_TIME;
    Type TIME = PrimitiveType.TIME;
    Type BOOLEAN = PrimitiveType.BOOLEAN;
    Type STRING = PrimitiveType.STRING;
    Type NULL = PrimitiveType.NULL;
    Type UNKNOWN = PrimitiveType.UNKNOWN;
}
