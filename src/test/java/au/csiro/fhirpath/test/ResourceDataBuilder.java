package au.csiro.fhirpath.test;

import au.csiro.fhirpath.typing.CodingValue;
import au.csiro.fhirpath.typing.DateTimeValue;
import au.csiro.fhirpath.typing.DateValue;
import au.csiro.fhirpath.typing.QuantityValue;
import au.csiro.fhirpath.typing.TimeValue;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
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

  // --- Temporal types ---

  /**
   * Add a date field.
   *
   * @param name Field name
   * @param value ISO 8601 date string (e.g., "2014-01-25"), or null for empty
   * @return This builder for chaining
   */
  @Nonnull
  public ResourceDataBuilder date(@Nonnull final String name, @Nullable final String value) {
    data.put(name, value != null ? new DateValue(value) : null);
    return this;
  }

  /**
   * Add a date array field.
   *
   * @param name Field name
   * @param values ISO 8601 date strings
   * @return This builder for chaining
   */
  @Nonnull
  public ResourceDataBuilder dateArray(
      @Nonnull final String name, @Nonnull final String... values) {
    data.put(name, Arrays.stream(values).map(DateValue::new).toList());
    return this;
  }

  /**
   * Add a dateTime field.
   *
   * @param name Field name
   * @param value ISO 8601 dateTime string (e.g., "2014-01-25T14:30:00"), or null for empty
   * @return This builder for chaining
   */
  @Nonnull
  public ResourceDataBuilder dateTime(@Nonnull final String name, @Nullable final String value) {
    data.put(name, value != null ? new DateTimeValue(value) : null);
    return this;
  }

  /**
   * Add a dateTime array field.
   *
   * @param name Field name
   * @param values ISO 8601 dateTime strings
   * @return This builder for chaining
   */
  @Nonnull
  public ResourceDataBuilder dateTimeArray(
      @Nonnull final String name, @Nonnull final String... values) {
    data.put(name, Arrays.stream(values).map(DateTimeValue::new).toList());
    return this;
  }

  /**
   * Add a time field.
   *
   * @param name Field name
   * @param value ISO 8601 time string (e.g., "14:30:00"), or null for empty
   * @return This builder for chaining
   */
  @Nonnull
  public ResourceDataBuilder time(@Nonnull final String name, @Nullable final String value) {
    data.put(name, value != null ? new TimeValue(value) : null);
    return this;
  }

  /**
   * Add a time array field.
   *
   * @param name Field name
   * @param values ISO 8601 time strings
   * @return This builder for chaining
   */
  @Nonnull
  public ResourceDataBuilder timeArray(
      @Nonnull final String name, @Nonnull final String... values) {
    data.put(name, Arrays.stream(values).map(TimeValue::new).toList());
    return this;
  }

  // --- Quantity type ---

  /**
   * Add a quantity field from a FHIRPath quantity literal string.
   *
   * <p>Accepts formats like {@code "10.5 'mg'"} (UCUM) or {@code "4 days"} (calendar duration).
   *
   * @param name Field name
   * @param literal FHIRPath quantity literal string, or null for empty
   * @return This builder for chaining
   */
  @Nonnull
  public ResourceDataBuilder quantity(@Nonnull final String name, @Nullable final String literal) {
    data.put(name, literal != null ? parseQuantity(literal) : null);
    return this;
  }

  /**
   * Add a quantity array field.
   *
   * @param name Field name
   * @param literals FHIRPath quantity literal strings
   * @return This builder for chaining
   */
  @Nonnull
  public ResourceDataBuilder quantityArray(
      @Nonnull final String name, @Nonnull final String... literals) {
    data.put(name, Arrays.stream(literals).map(ResourceDataBuilder::parseQuantity).toList());
    return this;
  }

  // --- Coding type (stored as CodingValue) ---

  /**
   * Add a coding field from a pipe-delimited literal.
   *
   * <p>Format: {@code "system|code[|version[|display[|userSelected]]]"}
   *
   * @param name Field name
   * @param literal Pipe-delimited coding string, or null for empty
   * @return This builder for chaining
   */
  @Nonnull
  public ResourceDataBuilder coding(@Nonnull final String name, @Nullable final String literal) {
    data.put(name, literal != null ? parseCodingValue(literal) : null);
    return this;
  }

  /**
   * Add a coding array field.
   *
   * @param name Field name
   * @param literals Pipe-delimited coding strings
   * @return This builder for chaining
   */
  @Nonnull
  public ResourceDataBuilder codingArray(
      @Nonnull final String name, @Nonnull final String... literals) {
    data.put(name, Arrays.stream(literals).map(ResourceDataBuilder::parseCodingValue).toList());
    return this;
  }

  /**
   * Parses a FHIRPath quantity literal like {@code "10.5 'mg'"} or {@code "4 days"}.
   *
   * @param literal the quantity literal string
   * @return the parsed QuantityValue
   */
  @Nonnull
  public static QuantityValue parseQuantity(@Nonnull final String literal) {
    final String trimmed = literal.trim();
    final int spaceIdx = trimmed.indexOf(' ');
    if (spaceIdx < 0) {
      return QuantityValue.ofDefault(new BigDecimal(trimmed));
    }
    final BigDecimal value = new BigDecimal(trimmed.substring(0, spaceIdx));
    final String unitPart = trimmed.substring(spaceIdx + 1).trim();

    // UCUM unit in single quotes: 'mg'
    if (unitPart.startsWith("'") && unitPart.endsWith("'")) {
      final String ucumCode = unitPart.substring(1, unitPart.length() - 1);
      return QuantityValue.ofUcum(value, ucumCode);
    }
    // Calendar duration keyword: days, year, etc.
    return QuantityValue.ofCalendar(value, unitPart);
  }

  /** Delegates to {@link CodingValue#parse(String)}. */
  @Nonnull
  public static CodingValue parseCodingValue(@Nonnull final String literal) {
    return CodingValue.parse(literal);
  }

  // --- Typed empty fields ---

  /**
   * Add a typed empty field. The field value is null but preserves type information for correct
   * type inference.
   *
   * @param name Field name
   * @param typedNull The typed null marker
   * @return This builder for chaining
   */
  @Nonnull
  public ResourceDataBuilder typedEmpty(
      @Nonnull final String name, @Nonnull final TypedNull typedNull) {
    data.put(name, typedNull);
    return this;
  }

  /** Add an empty Boolean field. */
  @Nonnull
  public ResourceDataBuilder boolEmpty(@Nonnull final String name) {
    return typedEmpty(name, TypedNull.BOOLEAN);
  }

  /** Add an empty Integer field. */
  @Nonnull
  public ResourceDataBuilder integerEmpty(@Nonnull final String name) {
    return typedEmpty(name, TypedNull.INTEGER);
  }

  /** Add an empty Decimal field. */
  @Nonnull
  public ResourceDataBuilder decimalEmpty(@Nonnull final String name) {
    return typedEmpty(name, TypedNull.DECIMAL);
  }

  /** Add an empty String field. */
  @Nonnull
  public ResourceDataBuilder stringEmpty(@Nonnull final String name) {
    return typedEmpty(name, TypedNull.STRING);
  }

  /** Add an empty Date field. */
  @Nonnull
  public ResourceDataBuilder dateEmpty(@Nonnull final String name) {
    return typedEmpty(name, TypedNull.DATE);
  }

  /** Add an empty DateTime field. */
  @Nonnull
  public ResourceDataBuilder dateTimeEmpty(@Nonnull final String name) {
    return typedEmpty(name, TypedNull.DATE_TIME);
  }

  /** Add an empty Time field. */
  @Nonnull
  public ResourceDataBuilder timeEmpty(@Nonnull final String name) {
    return typedEmpty(name, TypedNull.TIME);
  }

  /** Add an empty Quantity field. */
  @Nonnull
  public ResourceDataBuilder quantityEmpty(@Nonnull final String name) {
    return typedEmpty(name, TypedNull.QUANTITY);
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
