package com.example.fhirpath.typing;

import jakarta.annotation.Nonnull;
import java.util.List;

/**
 * A resource type with explicitly defined fields stored in a map.
 *
 * <p>Extends {@link InlineComplexType} with the {@link ResourceType} marker. Used for tests and
 * explicit resource type definitions.
 */
public final class InlineResourceType extends InlineComplexType implements ResourceType {

  /** Sentinel value representing an empty/unspecified resource type. */
  public static final InlineResourceType EMPTY = new InlineResourceType("<empty>", List.of());

  /**
   * Constructs a resource type with the given name and field specifications.
   *
   * @param name the resource type name (e.g., "Patient")
   * @param fieldSpecs the list of field specifications
   */
  public InlineResourceType(@Nonnull final String name, @Nonnull final List<FieldSpec> fieldSpecs) {
    super(name, fieldSpecs);
  }

  /**
   * Convenience constructor for varargs field specifications.
   *
   * @param name the resource type name
   * @param fieldSpecs the field specifications
   */
  public InlineResourceType(@Nonnull final String name, @Nonnull final FieldSpec... fieldSpecs) {
    this(name, List.of(fieldSpecs));
  }
}
