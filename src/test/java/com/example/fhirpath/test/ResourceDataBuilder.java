package com.example.fhirpath.test;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Fluent builder for creating resource test data as Map-based structures.
 *
 * <p>Provides type-specific methods for building JSON-like data structures used in FHIRPath
 * resource tests. The builder creates a {@code Map<String, Object>} where values can be primitives,
 * Lists, or nested Maps.
 *
 * <p><b>Usage example:</b>
 *
 * <pre>{@code
 * Map<String, Object> patient = new ResourceDataBuilder()
 *     .string("id", "patient-1")
 *     .integer("age", 30)
 *     .elementArray("name",
 *         n -> n.string("family", "Smith").stringArray("given", "John"),
 *         n -> n.string("family", "Doe").stringArray("given", "Jane")
 *     )
 *     .build();
 * }</pre>
 */
public class ResourceDataBuilder {

  private final Map<String, Object> data = new HashMap<>();

  /**
   * Add a string field.
   *
   * @param name Field name
   * @param value String value (null allowed)
   * @return This builder for chaining
   */
  @Nonnull
  public ResourceDataBuilder string(@Nonnull final String name, @Nullable final String value) {
    data.put(name, value);
    return this;
  }

  /**
   * Add a string array field.
   *
   * @param name Field name
   * @param values String values
   * @return This builder for chaining
   */
  @Nonnull
  public ResourceDataBuilder stringArray(
      @Nonnull final String name, @Nonnull final String... values) {
    data.put(name, List.of(values));
    return this;
  }

  /**
   * Add an integer field.
   *
   * @param name Field name
   * @param value Integer value (null allowed)
   * @return This builder for chaining
   */
  @Nonnull
  public ResourceDataBuilder integer(@Nonnull final String name, @Nullable final Integer value) {
    data.put(name, value);
    return this;
  }

  /**
   * Add an integer array field.
   *
   * @param name Field name
   * @param values Integer values
   * @return This builder for chaining
   */
  @Nonnull
  public ResourceDataBuilder integerArray(
      @Nonnull final String name, @Nonnull final Integer... values) {
    data.put(name, List.of(values));
    return this;
  }

  /**
   * Add a decimal field.
   *
   * @param name Field name
   * @param value Double value (null allowed)
   * @return This builder for chaining
   */
  @Nonnull
  public ResourceDataBuilder decimal(@Nonnull final String name, @Nullable final Double value) {
    data.put(name, value);
    return this;
  }

  /**
   * Add a decimal array field.
   *
   * @param name Field name
   * @param values Double values
   * @return This builder for chaining
   */
  @Nonnull
  public ResourceDataBuilder decimalArray(
      @Nonnull final String name, @Nonnull final Double... values) {
    data.put(name, List.of(values));
    return this;
  }

  /**
   * Add a boolean field.
   *
   * @param name Field name
   * @param value Boolean value (null allowed)
   * @return This builder for chaining
   */
  @Nonnull
  public ResourceDataBuilder bool(@Nonnull final String name, @Nullable final Boolean value) {
    data.put(name, value);
    return this;
  }

  /**
   * Add a boolean array field.
   *
   * @param name Field name
   * @param values Boolean values
   * @return This builder for chaining
   */
  @Nonnull
  public ResourceDataBuilder boolArray(
      @Nonnull final String name, @Nonnull final Boolean... values) {
    data.put(name, List.of(values));
    return this;
  }

  /**
   * Add a nested element (complex type).
   *
   * @param name Field name
   * @param builderConsumer Consumer that builds the nested element
   * @return This builder for chaining
   */
  @Nonnull
  public ResourceDataBuilder element(
      @Nonnull final String name, @Nonnull final Consumer<ResourceDataBuilder> builderConsumer) {
    final ResourceDataBuilder nestedBuilder = new ResourceDataBuilder();
    builderConsumer.accept(nestedBuilder);
    data.put(name, nestedBuilder.build());
    return this;
  }

  /**
   * Add an array of nested elements (complex type array).
   *
   * @param name Field name
   * @param builders Varargs of consumers that build each array element
   * @return This builder for chaining
   */
  @Nonnull
  @SafeVarargs
  public final ResourceDataBuilder elementArray(
      @Nonnull final String name, @Nonnull final Consumer<ResourceDataBuilder>... builders) {
    final List<Map<String, Object>> elements = new ArrayList<>();
    for (final Consumer<ResourceDataBuilder> builderConsumer : builders) {
      final ResourceDataBuilder nestedBuilder = new ResourceDataBuilder();
      builderConsumer.accept(nestedBuilder);
      elements.add(nestedBuilder.build());
    }
    data.put(name, elements);
    return this;
  }

  /**
   * Build the Map representation of the resource data.
   *
   * @return Unmodifiable Map containing the resource data
   */
  @Nonnull
  public Map<String, Object> build() {
    return Map.copyOf(data);
  }
}
