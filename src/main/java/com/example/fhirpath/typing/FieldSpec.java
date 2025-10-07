package com.example.fhirpath.typing;

public class FieldSpec {
    private final String name;
    private final Type type;

    public FieldSpec(String name, Type type) {
        this.name = name;
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public Type getType() {
        return type;
    }

    public boolean isSingular() {
        return !type.isCollection();
    }
}
