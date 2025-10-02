package com.example.fhirpath.typing;

public class FieldSpec {
    private final String name;
    private final Type type;
    private final boolean isSingular;

    public FieldSpec(String name, Type type, boolean isSingular) {
        this.name = name;
        this.type = type;
        this.isSingular = isSingular;
    }

    // Convenience constructors
    public static FieldSpec singular(String name, Type type) {
        return new FieldSpec(name, type, true);
    }

    public static FieldSpec collection(String name, Type type) {
        return new FieldSpec(name, type, false);
    }

    public String getName() {
        return name;
    }

    public Type getType() {
        return type;
    }

    public boolean isSingular() {
        return isSingular;
    }

    public boolean isCollection() {
        return !isSingular;
    }
}
