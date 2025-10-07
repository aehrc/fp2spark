package com.example.fhirpath.typing;

public enum PrimitiveType implements Type {
    INTEGER("integer"),
    DECIMAL("decimal"),
    QUANTITY("quantity"),
    DATE("date"),
    DATE_TIME("dateTime"),
    TIME("time"),
    BOOLEAN("boolean"),
    STRING("string"),
    NULL("null"),
    UNKNOWN("unknown");

    private final String name;

    PrimitiveType(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public boolean isPrimitive() {
        return true;
    }

    @Override
    public boolean isComplex() {
        return false;
    }

    @Override
    public boolean isCollection() {
        return false;
    }
}
