package com.example.fhirpath.typing;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Represents a complex FHIRPath type with named fields (e.g., a FHIR resource or data type). */
public class ComplexType implements Type {
  private final String name;
  private final Map<String, FieldSpec> fields;

  /**
   * Constructs a named complex type with the given field specifications.
   *
   * @param name the type name (e.g., "HumanName", "Coding")
   * @param fieldSpecs the list of field specifications
   */
  public ComplexType(final String name, final List<FieldSpec> fieldSpecs) {
    this.name = name;
    this.fields =
        fieldSpecs.stream().collect(Collectors.toMap(FieldSpec::getName, Function.identity()));
  }

  /**
   * Constructs a complex type with the given field specifications and a default name.
   *
   * @param fieldSpecs the list of field specifications
   */
  public ComplexType(final List<FieldSpec> fieldSpecs) {
    this("ComplexType", fieldSpecs);
  }

  /**
   * Convenience constructor accepting varargs field specifications.
   *
   * @param fieldSpecs the field specifications
   */
  public ComplexType(final FieldSpec... fieldSpecs) {
    this(List.of(fieldSpecs));
  }

  /**
   * Constructs a named complex type with no fields. Used as a marker type for lazy resolution via
   * {@link TypeResolver}.
   *
   * @param name the type name (e.g., "HumanName")
   */
  public ComplexType(final String name) {
    this(name, List.of());
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public boolean isPrimitive() {
    return false;
  }

  @Override
  public boolean isComplex() {
    return true;
  }

  /** Returns the set of field names defined on this complex type. */
  public Set<String> getFieldNames() {
    return fields.keySet();
  }

  /**
   * Returns the field specification for the given field name, if present.
   *
   * @param fieldName the field name to look up
   * @return an Optional containing the field spec, or empty if not found
   */
  public Optional<FieldSpec> getField(final String fieldName) {
    return Optional.ofNullable(fields.get(fieldName));
  }

  /**
   * Returns whether this complex type has a field with the given name.
   *
   * @param fieldName the field name to check
   * @return true if the field exists
   */
  public boolean hasField(final String fieldName) {
    return fields.containsKey(fieldName);
  }

  /** Returns all field specifications defined on this complex type. */
  public List<FieldSpec> getFields() {
    return List.copyOf(fields.values());
  }
}
