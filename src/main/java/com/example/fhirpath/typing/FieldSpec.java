package com.example.fhirpath.typing;

/**
 * Specification for a field in a complex type.
 *
 * <p>Each field has a name and a shape (element type + cardinality).
 */
public class FieldSpec {
    private final String name;
    private final Shape shape;

    public FieldSpec(String name, Shape shape) {
        this.name = name;
        this.shape = shape;
    }

    public String getName() {
        return name;
    }

    public Shape getShape() {
        return shape;
    }

    /**
     * Returns the element type of this field.
     */
    public Type getType() {
        return shape.elementType();
    }

    /**
     * Returns the cardinality of this field.
     */
    public Cardinality getCardinality() {
        return shape.cardinality();
    }

    /**
     * Returns whether this field has single cardinality (0..1).
     */
    public boolean isSingular() {
        return shape.isSingle();
    }
}
