package com.example.fhirpath.typing;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ComplexType implements Type {
    private final Map<String, FieldSpec> fields;

    public ComplexType(List<FieldSpec> fieldSpecs) {
        this.fields = fieldSpecs.stream()
                .collect(Collectors.toMap(FieldSpec::getName, Function.identity()));
    }

    // Convenience constructor for varargs
    public ComplexType(FieldSpec... fieldSpecs) {
        this(List.of(fieldSpecs));
    }

    @Override
    public String getName() {
        return "ComplexType";
    }

    @Override
    public boolean isPrimitive() {
        return false;
    }

    @Override
    public boolean isComplex() {
        return true;
    }

    @Override
    public boolean isCollection() {
        return false;
    }

    public Set<String> getFieldNames() {
        return fields.keySet();
    }

    public Optional<FieldSpec> getField(String fieldName) {
        return Optional.ofNullable(fields.get(fieldName));
    }

    public boolean hasField(String fieldName) {
        return fields.containsKey(fieldName);
    }

    public List<FieldSpec> getFields() {
        return List.copyOf(fields.values());
    }
}
