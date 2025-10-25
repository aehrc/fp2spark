package com.example.fhirpath.test;

import com.example.fhirpath.typing.*;
import jakarta.annotation.Nonnull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Infers ResourceType from Map-based test data structures.
 *
 * <p>This class analyzes the structure and values in a {@code Map<String, Object>}
 * to derive field specifications (name, type, cardinality) and construct a complete
 * ResourceType definition.
 *
 * <p><b>Type Inference Rules:</b>
 * <ul>
 *   <li>String values → {@link PrimitiveType#STRING}</li>
 *   <li>Integer values → {@link PrimitiveType#INTEGER}</li>
 *   <li>Double values → {@link PrimitiveType#DECIMAL}</li>
 *   <li>Boolean values → {@link PrimitiveType#BOOLEAN}</li>
 *   <li>null values → {@link PrimitiveType#NULL}</li>
 *   <li>List values → {@link Cardinality#MANY} with element type from first item</li>
 *   <li>Map values → {@link ComplexType} with recursive inference</li>
 * </ul>
 *
 * <p><b>Cardinality Rules:</b>
 * <ul>
 *   <li>Single values (String, Integer, etc.) → {@link Cardinality#SINGLE}</li>
 *   <li>List values → {@link Cardinality#MANY}</li>
 *   <li>Empty lists → {@link Cardinality#MANY} with {@link PrimitiveType#ANY}</li>
 * </ul>
 *
 * <p><b>Example:</b>
 * <pre>{@code
 * Map<String, Object> data = new ResourceDataBuilder()
 *     .string("id", "patient-1")
 *     .integer("age", 30)
 *     .element("name", n -> n.string("family", "Smith"))
 *     .build();
 *
 * ResourceType patientType = ResourceTypeInference.infer("Patient", data);
 * // Result:
 * // Patient {
 * //   id: ?string
 * //   age: ?integer
 * //   name: ?ComplexType { family: ?string }
 * // }
 * }</pre>
 */
class ResourceTypeInference {

    /**
     * Infer a ResourceType from the given resource name and Map data.
     *
     * @param resourceTypeName The name of the resource type (e.g., "Patient")
     * @param data             The Map data structure to infer from
     * @return A ResourceType with inferred field specifications
     */
    @Nonnull
    static ResourceType infer(
            @Nonnull final String resourceTypeName,
            @Nonnull final Map<String, Object> data
    ) {
        final List<FieldSpec> fieldSpecs = new ArrayList<>();

        for (final Map.Entry<String, Object> entry : data.entrySet()) {
            final String fieldName = entry.getKey();
            final Object value = entry.getValue();
            final Shape shape = inferShape(value);
            fieldSpecs.add(new FieldSpec(fieldName, shape));
        }

        return new ResourceType(resourceTypeName, fieldSpecs);
    }

    /**
     * Infer the shape (type + cardinality) from a value.
     *
     * @param value The value to analyze (can be primitive, List, Map, or null)
     * @return The inferred Shape
     */
    @Nonnull
    private static Shape inferShape(@Nonnull final Object value) {
        if (value == null) {
            return Shape.single(PrimitiveType.NULL);
        }

        if (value instanceof List<?> list) {
            return inferListShape(list);
        }

        if (value instanceof Map<?, ?> map) {
            return inferMapShape(map);
        }

        // Primitive value - infer type and use SINGLE cardinality
        final Type elementType = inferPrimitiveType(value);
        return Shape.single(elementType);
    }

    /**
     * Infer shape from a List value (always MANY cardinality).
     *
     * @param list The list to analyze
     * @return Shape with MANY cardinality
     */
    @Nonnull
    private static Shape inferListShape(@Nonnull final List<?> list) {
        if (list.isEmpty()) {
            // Empty list - use ANY type
            return Shape.many(PrimitiveType.ANY);
        }

        // Use first element to determine type
        final Object firstElement = list.get(0);
        if (firstElement instanceof Map<?, ?> map) {
            // List of complex types
            final ComplexType elementType = inferComplexType(map);
            return Shape.many(elementType);
        } else {
            // List of primitives
            final Type elementType = inferPrimitiveType(firstElement);
            return Shape.many(elementType);
        }
    }

    /**
     * Infer shape from a Map value (complex type with SINGLE cardinality).
     *
     * @param map The map to analyze
     * @return Shape with ComplexType element type and SINGLE cardinality
     */
    @Nonnull
    private static Shape inferMapShape(@Nonnull final Map<?, ?> map) {
        @SuppressWarnings("unchecked")
        final Map<String, Object> typedMap = (Map<String, Object>) map;
        final ComplexType complexType = inferComplexType(typedMap);
        return Shape.single(complexType);
    }

    /**
     * Infer a ComplexType from a Map structure.
     *
     * @param map The map representing a complex type
     * @return The inferred ComplexType
     */
    @Nonnull
    private static ComplexType inferComplexType(@Nonnull final Map<?, ?> map) {
        @SuppressWarnings("unchecked")
        final Map<String, Object> typedMap = (Map<String, Object>) map;

        final List<FieldSpec> fieldSpecs = new ArrayList<>();
        for (final Map.Entry<String, Object> entry : typedMap.entrySet()) {
            final String fieldName = entry.getKey();
            final Object value = entry.getValue();
            final Shape shape = inferShape(value);
            fieldSpecs.add(new FieldSpec(fieldName, shape));
        }

        return new ComplexType(fieldSpecs);
    }

    /**
     * Infer primitive type from a Java value.
     *
     * @param value The value to analyze
     * @return The inferred PrimitiveType
     */
    @Nonnull
    private static Type inferPrimitiveType(@Nonnull final Object value) {
        return switch (value) {
            case String s -> PrimitiveType.STRING;
            case Integer i -> PrimitiveType.INTEGER;
            case Double d -> PrimitiveType.DECIMAL;
            case Boolean b -> PrimitiveType.BOOLEAN;
            case null -> PrimitiveType.NULL;
            default -> throw new IllegalArgumentException(
                    "Unsupported value type: " + value.getClass().getName()
            );
        };
    }
}
