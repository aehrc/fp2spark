package com.example.fhirpath.typing;

import java.util.List;

/** Represents a FHIR resource type with named fields. */
public class ResourceType extends ComplexType {

  /** Sentinel value representing an empty/unspecified resource type. */
  public static final ResourceType EMPTY = new ResourceType("<empty>", List.of());

  private final String name;

  /**
   * Constructs a resource type with the given name and field specifications.
   *
   * @param name the resource type name (e.g., "Patient")
   * @param fieldSpecs the list of field specifications
   */
  public ResourceType(final String name, final List<FieldSpec> fieldSpecs) {
    super(fieldSpecs);
    this.name = name;
  }

  /**
   * Convenience constructor for varargs field specifications.
   *
   * @param name the resource type name
   * @param fieldSpecs the field specifications
   */
  public ResourceType(final String name, final FieldSpec... fieldSpecs) {
    this(name, List.of(fieldSpecs));
  }

  @Override
  public String getName() {
    return name;
  }

  /** Returns the resource name (same as {@link #getName()}). */
  public String getResourceName() {
    return name;
  }
}
