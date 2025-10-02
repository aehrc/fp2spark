package com.example.fhirpath.typing;

import java.util.List;

public class ResourceType extends ComplexType {

    public static final ResourceType EMPTY = new ResourceType("<empty>", List.of());

    private final String name;

    public ResourceType(String name, List<FieldSpec> fieldSpecs) {
        super(fieldSpecs);
        this.name = name;
    }

    // Convenience constructor for varargs
    public ResourceType(String name, FieldSpec... fieldSpecs) {
        this(name, List.of(fieldSpecs));
    }

    @Override
    public String getName() {
        return name;
    }

    public String getResourceName() {
        return name;
    }
}
