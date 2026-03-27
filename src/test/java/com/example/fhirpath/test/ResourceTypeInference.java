package com.example.fhirpath.test;

import com.example.fhirpath.typing.Cardinality;
import com.example.fhirpath.typing.CodingValue;
import com.example.fhirpath.typing.ComplexType;
import com.example.fhirpath.typing.DateTimeValue;
import com.example.fhirpath.typing.DateValue;
import com.example.fhirpath.typing.FieldSpec;
import com.example.fhirpath.typing.InlineComplexType;
import com.example.fhirpath.typing.InlineResourceType;
import com.example.fhirpath.typing.PrimitiveType;
import com.example.fhirpath.typing.QuantityValue;
import com.example.fhirpath.typing.ResourceType;
import com.example.fhirpath.typing.Shape;
import com.example.fhirpath.typing.TimeValue;
import com.example.fhirpath.typing.Type;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Infers ResourceType from Map-based test data structures.
 *
 * <p>This class analyzes the structure and values in a {@code Map<String, Object>} to derive field
 * specifications (name, type, cardinality) and construct a complete ResourceType definition.
 *
 * <p><b>Type Inference Rules:</b>
 *
 * <ul>
 *   <li>String values → {@link PrimitiveType#STRING}
 *   <li>Integer values → {@link PrimitiveType#INTEGER}
 *   <li>Double values → {@link PrimitiveType#DECIMAL}
 *   <li>Boolean values → {@link PrimitiveType#BOOLEAN}
 *   <li>null values → {@link PrimitiveType#NULL}
 *   <li>{@link TypedNull} values → the wrapped {@link PrimitiveType} with {@link
 *       Cardinality#SINGLE}
 *   <li>List values → {@link Cardinality#MANY} with element type merged across all items
 *   <li>Map values → {@link ComplexType} with recursive inference
 * </ul>
 *
 * <p><b>Cardinality Rules:</b>
 *
 * <ul>
 *   <li>Single values (String, Integer, etc.) → {@link Cardinality#SINGLE}
 *   <li>List values → {@link Cardinality#MANY}
 *   <li>Empty lists → {@link Cardinality#MANY} with {@link PrimitiveType#ANY}
 * </ul>
 *
 * <p><b>Example:</b>
 *
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

  /** Maximum nesting depth for type inference to prevent stack overflow. */
  private static final int MAX_DEPTH = 20;

  /**
   * Infer a ResourceType from the given resource name and Map data.
   *
   * @param resourceTypeName The name of the resource type (e.g., "Patient")
   * @param data The Map data structure to infer from
   * @return A ResourceType with inferred field specifications
   * @throws IllegalArgumentException if nesting depth exceeds MAX_DEPTH
   */
  @Nonnull
  static ResourceType infer(
      @Nonnull final String resourceTypeName, @Nonnull final Map<String, Object> data) {
    final List<FieldSpec> fieldSpecs = new ArrayList<>();

    for (final Map.Entry<String, Object> entry : data.entrySet()) {
      final String fieldName = entry.getKey();
      final Object value = entry.getValue();
      final Shape shape = inferShape(value, 0);
      fieldSpecs.add(new FieldSpec(fieldName, shape));
    }

    return new InlineResourceType(resourceTypeName, fieldSpecs);
  }

  /**
   * Infer the shape (type + cardinality) from a value.
   *
   * @param value The value to analyze (can be primitive, List, Map, or null)
   * @param depth Current recursion depth
   * @return The inferred Shape
   * @throws IllegalArgumentException if depth exceeds MAX_DEPTH
   */
  @Nonnull
  private static Shape inferShape(@Nullable final Object value, final int depth) {
    if (depth > MAX_DEPTH) {
      throw new IllegalArgumentException(
          "Maximum nesting depth " + MAX_DEPTH + " exceeded during type inference");
    }

    if (value == null) {
      return Shape.single(PrimitiveType.NULL);
    }

    if (value instanceof TypedNull typedNull) {
      return Shape.single(typedNull.type());
    }

    if (value instanceof List<?> list) {
      return inferListShape(list, depth);
    }

    if (value instanceof Map<?, ?> map) {
      return inferMapShape(map, depth);
    }

    // Primitive value - infer type and use SINGLE cardinality
    final Type elementType = inferPrimitiveType(value);
    return Shape.single(elementType);
  }

  /**
   * Infer shape from a List value (always MANY cardinality).
   *
   * @param list The list to analyze
   * @param depth Current recursion depth
   * @return Shape with MANY cardinality
   */
  @Nonnull
  private static Shape inferListShape(@Nonnull final List<?> list, final int depth) {
    if (list.isEmpty()) {
      // Empty list - use ANY type
      return Shape.many(PrimitiveType.ANY);
    }

    // Use first element to determine type
    final Object firstElement = list.get(0);
    if (firstElement instanceof Map<?, ?> map) {
      // List of complex types - merge field specs across all elements
      // to capture types that are null in the first element but present in others
      final ComplexType elementType = mergeComplexTypes(list, depth + 1);
      return Shape.many(elementType);
    } else {
      // List of primitives
      final Type elementType = inferPrimitiveType(firstElement);
      return Shape.many(elementType);
    }
  }

  /**
   * Merge field specs from all Map elements in a list to produce a complete ComplexType.
   *
   * <p>This handles the case where a field is null in some elements but has a concrete type in
   * others. The merged type uses the first non-null type found for each field.
   *
   * @param list The list of Map elements
   * @param depth Current recursion depth
   * @return A ComplexType with merged field specs
   */
  @Nonnull
  private static ComplexType mergeComplexTypes(@Nonnull final List<?> list, final int depth) {
    final Map<String, Shape> mergedFields = new LinkedHashMap<>();

    for (final Object element : list) {
      if (!(element instanceof Map<?, ?> map)) {
        throw new IllegalArgumentException(
            "Expected Map element in complex type list, found: " + element.getClass().getName());
      }
      for (final Map.Entry<?, ?> entry : map.entrySet()) {
        final String fieldName = (String) entry.getKey();
        final Shape shape = inferShape(entry.getValue(), depth);
        mergedFields.merge(fieldName, shape, ResourceTypeInference::mergeShapes);
      }
    }

    final List<FieldSpec> fieldSpecs =
        mergedFields.entrySet().stream().map(e -> new FieldSpec(e.getKey(), e.getValue())).toList();
    return new InlineComplexType(fieldSpecs);
  }

  /**
   * Merge two shapes, preferring the non-null type. If both are non-null, the existing shape wins.
   *
   * <p>Only single-cardinality fields are expected inside list elements. A cardinality conflict
   * indicates a malformed test data structure.
   *
   * @param existing the shape already recorded for this field
   * @param incoming the shape from the current list element
   * @return the merged shape
   * @throws IllegalStateException if types or cardinalities conflict between two non-null types
   */
  @Nonnull
  private static Shape mergeShapes(@Nonnull final Shape existing, @Nonnull final Shape incoming) {
    if (existing.elementType() == PrimitiveType.NULL) {
      return incoming;
    }
    if (incoming.elementType() != PrimitiveType.NULL) {
      if (existing.elementType() instanceof PrimitiveType
          && incoming.elementType() instanceof PrimitiveType
          && existing.elementType() != incoming.elementType()) {
        throw new IllegalStateException(
            "Type conflict for field: existing=" + existing + ", incoming=" + incoming);
      }
      if (existing.cardinality() != incoming.cardinality()) {
        throw new IllegalStateException(
            "Cardinality conflict for field: existing=" + existing + ", incoming=" + incoming);
      }
    }
    return existing;
  }

  /**
   * Infer shape from a Map value (complex type with SINGLE cardinality).
   *
   * @param map The map to analyze
   * @param depth Current recursion depth
   * @return Shape with ComplexType element type and SINGLE cardinality
   * @throws IllegalArgumentException if map contains non-String keys
   */
  @Nonnull
  private static Shape inferMapShape(@Nonnull final Map<?, ?> map, final int depth) {
    // Validate that all keys are Strings before casting
    for (final Object key : map.keySet()) {
      if (!(key instanceof String)) {
        throw new IllegalArgumentException(
            "Map keys must be Strings for type inference, found: " + key.getClass().getName());
      }
    }

    @SuppressWarnings("unchecked")
    final Map<String, Object> typedMap = (Map<String, Object>) map;
    final ComplexType complexType = inferComplexType(typedMap, depth + 1);
    return Shape.single(complexType);
  }

  /**
   * Infer a ComplexType from a Map structure.
   *
   * @param map The map representing a complex type
   * @param depth Current recursion depth
   * @return The inferred ComplexType
   * @throws IllegalArgumentException if map contains non-String keys
   */
  @Nonnull
  private static ComplexType inferComplexType(@Nonnull final Map<?, ?> map, final int depth) {
    // Validate that all keys are Strings before casting
    for (final Object key : map.keySet()) {
      if (!(key instanceof String)) {
        throw new IllegalArgumentException(
            "Map keys must be Strings for type inference, found: " + key.getClass().getName());
      }
    }

    @SuppressWarnings("unchecked")
    final Map<String, Object> typedMap = (Map<String, Object>) map;

    final List<FieldSpec> fieldSpecs = new ArrayList<>();
    for (final Map.Entry<String, Object> entry : typedMap.entrySet()) {
      final String fieldName = entry.getKey();
      final Object value = entry.getValue();
      final Shape shape = inferShape(value, depth);
      fieldSpecs.add(new FieldSpec(fieldName, shape));
    }

    return new InlineComplexType(fieldSpecs);
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
      case DateValue dv -> PrimitiveType.DATE;
      case DateTimeValue dtv -> PrimitiveType.DATE_TIME;
      case TimeValue tv -> PrimitiveType.TIME;
      case QuantityValue qv -> PrimitiveType.QUANTITY;
      case CodingValue cv -> PrimitiveType.CODING;
      case null -> PrimitiveType.NULL;
      default ->
          throw new IllegalArgumentException(
              "Unsupported value type: " + value.getClass().getName());
    };
  }
}
