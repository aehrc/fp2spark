package au.csiro.fhirpath.compat;

import au.csiro.fhirpath.test.ResourceDataBuilder;
import au.csiro.fhirpath.test.TypedNull;
import au.csiro.fhirpath.typing.DateTimeValue;
import au.csiro.fhirpath.typing.DateValue;
import au.csiro.fhirpath.typing.TimeValue;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.hl7.fhir.r4.model.Enumerations.FHIRDefinedType;

/**
 * Adapter that provides Pathling's {@code FhirPathModelBuilder} API for building test subject data.
 *
 * <p>Delegates to {@link ResourceDataBuilder} for supported types and adds:
 *
 * <ul>
 *   <li>Empty helpers: {@code stringEmpty()}, {@code integerEmpty()}, etc. (store a {@link
 *       TypedNull} to preserve type information)
 *   <li>Primitive varargs: {@code integerArray(String, int...)}, etc. (boxes to wrapper types)
 *   <li>Temporal/quantity/coding methods via {@link ResourceDataBuilder}
 *   <li>Type annotations: {@code choice()}, {@code fhirType()}, {@code fhirReference()} store
 *       metadata keys that {@code ResourceTypeInference} interprets
 * </ul>
 */
public class CompatModelBuilder {

  /** Annotation key marking a choice element; value is the base element name. */
  public static final String CHOICE_ANNOTATION = "__CHOICE__";

  /** Annotation key setting an explicit FHIR type; value is the type code (e.g., "Reference"). */
  public static final String FHIR_TYPE_ANNOTATION = "__FHIR_TYPE__";

  private final Map<String, Object> model = new HashMap<>();

  @Nonnull
  Map<String, Object> getModel() {
    return model;
  }

  @Nonnull
  private CompatModelBuilder typedEmpty(
      @Nonnull final String name, @Nonnull final TypedNull value) {
    model.put(name, value);
    return this;
  }

  // --- String ---

  @Nonnull
  public CompatModelBuilder string(@Nonnull final String name, @Nullable final String value) {
    model.put(name, value);
    return this;
  }

  @Nonnull
  public CompatModelBuilder stringEmpty(@Nonnull final String name) {
    return typedEmpty(name, TypedNull.STRING);
  }

  @Nonnull
  public CompatModelBuilder stringArray(@Nonnull final String name, final String... values) {
    model.put(name, List.of(values));
    return this;
  }

  // --- Integer ---

  @Nonnull
  public CompatModelBuilder integer(@Nonnull final String name, @Nullable final Integer value) {
    model.put(name, value);
    return this;
  }

  @Nonnull
  public CompatModelBuilder integerEmpty(@Nonnull final String name) {
    return typedEmpty(name, TypedNull.INTEGER);
  }

  /** Accepts primitive {@code int...} to match Pathling's API. */
  @Nonnull
  public CompatModelBuilder integerArray(@Nonnull final String name, @Nonnull final int... values) {
    final List<Integer> list = new ArrayList<>(values.length);
    for (final int v : values) {
      list.add(v);
    }
    model.put(name, list);
    return this;
  }

  // --- Decimal ---

  @Nonnull
  public CompatModelBuilder decimal(@Nonnull final String name, @Nullable final Double value) {
    model.put(name, value);
    return this;
  }

  @Nonnull
  public CompatModelBuilder decimalEmpty(@Nonnull final String name) {
    return typedEmpty(name, TypedNull.DECIMAL);
  }

  /** Accepts primitive {@code double...} to match Pathling's API. */
  @Nonnull
  public CompatModelBuilder decimalArray(
      @Nonnull final String name, @Nonnull final double... values) {
    final List<Double> list = new ArrayList<>(values.length);
    for (final double v : values) {
      list.add(v);
    }
    model.put(name, list);
    return this;
  }

  // --- Boolean ---

  @Nonnull
  public CompatModelBuilder bool(@Nonnull final String name, @Nullable final Boolean value) {
    model.put(name, value);
    return this;
  }

  @Nonnull
  public CompatModelBuilder boolEmpty(@Nonnull final String name) {
    return typedEmpty(name, TypedNull.BOOLEAN);
  }

  /** Accepts primitive {@code boolean...} to match Pathling's API. */
  @Nonnull
  public CompatModelBuilder boolArray(
      @Nonnull final String name, @Nonnull final boolean... values) {
    final List<Boolean> list = new ArrayList<>(values.length);
    for (final boolean v : values) {
      list.add(v);
    }
    model.put(name, list);
    return this;
  }

  // --- Temporal types ---

  @Nonnull
  public CompatModelBuilder time(@Nonnull final String name, @Nullable final String value) {
    model.put(name, value != null ? new TimeValue(value) : null);
    return this;
  }

  @Nonnull
  public CompatModelBuilder timeEmpty(@Nonnull final String name) {
    return typedEmpty(name, TypedNull.TIME);
  }

  @Nonnull
  public CompatModelBuilder timeArray(@Nonnull final String name, @Nonnull final String... values) {
    model.put(name, Arrays.stream(values).map(TimeValue::new).toList());
    return this;
  }

  @Nonnull
  public CompatModelBuilder date(@Nonnull final String name, @Nullable final String value) {
    model.put(name, value != null ? new DateValue(value) : null);
    return this;
  }

  @Nonnull
  public CompatModelBuilder dateEmpty(@Nonnull final String name) {
    return typedEmpty(name, TypedNull.DATE);
  }

  @Nonnull
  public CompatModelBuilder dateArray(@Nonnull final String name, @Nonnull final String... values) {
    model.put(name, Arrays.stream(values).map(DateValue::new).toList());
    return this;
  }

  @Nonnull
  public CompatModelBuilder dateTime(@Nonnull final String name, @Nullable final String value) {
    model.put(name, value != null ? new DateTimeValue(value) : null);
    return this;
  }

  @Nonnull
  public CompatModelBuilder dateTimeEmpty(@Nonnull final String name) {
    return typedEmpty(name, TypedNull.DATE_TIME);
  }

  @Nonnull
  public CompatModelBuilder dateTimeArray(
      @Nonnull final String name, @Nonnull final String... values) {
    model.put(name, Arrays.stream(values).map(DateTimeValue::new).toList());
    return this;
  }

  // --- Quantity ---

  @Nonnull
  public CompatModelBuilder quantity(@Nonnull final String name, @Nullable final String value) {
    model.put(name, value != null ? ResourceDataBuilder.parseQuantity(value) : null);
    return this;
  }

  @Nonnull
  public CompatModelBuilder quantityEmpty(@Nonnull final String name) {
    return typedEmpty(name, TypedNull.QUANTITY);
  }

  @Nonnull
  public CompatModelBuilder quantityArray(
      @Nonnull final String name, @Nonnull final String... values) {
    model.put(name, Arrays.stream(values).map(ResourceDataBuilder::parseQuantity).toList());
    return this;
  }

  // --- Coding ---

  @Nonnull
  public CompatModelBuilder coding(@Nonnull final String name, @Nullable final String value) {
    model.put(name, value != null ? ResourceDataBuilder.parseCodingValue(value) : null);
    return this;
  }

  @Nonnull
  public CompatModelBuilder codingEmpty(@Nonnull final String name) {
    return typedEmpty(name, TypedNull.CODING);
  }

  @Nonnull
  public CompatModelBuilder codingArray(
      @Nonnull final String name, @Nonnull final String... values) {
    model.put(name, Arrays.stream(values).map(ResourceDataBuilder::parseCodingValue).toList());
    return this;
  }

  // --- Complex types ---

  @Nonnull
  public CompatModelBuilder element(
      @Nonnull final String name, @Nonnull final Consumer<CompatModelBuilder> builderConsumer) {
    final CompatModelBuilder nested = new CompatModelBuilder();
    builderConsumer.accept(nested);
    model.put(name, nested.model);
    return this;
  }

  // Uses plain null because TypedNull only holds SystemType and complex types have no
  // equivalent. This is safe: empty complex fields resolve to SystemType.NULL in the type
  // system, which correctly propagates emptiness without risking false overload matches (unlike
  // empty primitives, complex types are never operands to math/comparison operators).
  @Nonnull
  public CompatModelBuilder elementEmpty(@Nonnull final String name) {
    model.put(name, null);
    return this;
  }

  @Nonnull
  @SafeVarargs
  public final CompatModelBuilder elementArray(
      @Nonnull final String name, @Nonnull final Consumer<CompatModelBuilder>... builders) {
    final List<Map<String, Object>> list = new ArrayList<>();
    for (final Consumer<CompatModelBuilder> builderConsumer : builders) {
      final CompatModelBuilder nested = new CompatModelBuilder();
      builderConsumer.accept(nested);
      list.add(nested.model);
    }
    model.put(name, list);
    return this;
  }

  // --- Type annotation methods ---

  /**
   * Marks the sibling fields as variants of a choice element with the given base name.
   *
   * <p>Stores a {@value #CHOICE_ANNOTATION} annotation in the model map. {@code
   * ResourceTypeInference} interprets this to create an {@code InlineChoiceType}.
   *
   * @param name the base element name (e.g., "value" for value[x])
   */
  @Nonnull
  public CompatModelBuilder choice(@Nonnull final String name) {
    model.put(CHOICE_ANNOTATION, name);
    return this;
  }

  /**
   * Sets an explicit FHIR type on this element.
   *
   * <p>Stores a {@value #FHIR_TYPE_ANNOTATION} annotation in the model map. {@code
   * ResourceTypeInference} interprets this to create a named complex type.
   *
   * @param fhirType the FHIR type (e.g., {@code FHIRDefinedType.REFERENCE})
   */
  @Nonnull
  public CompatModelBuilder fhirType(@Nonnull final FHIRDefinedType fhirType) {
    model.put(FHIR_TYPE_ANNOTATION, fhirType.toCode());
    return this;
  }

  /**
   * Convenience method that sets fhirType to REFERENCE and adds empty reference/type fields.
   *
   * <p>Equivalent to {@code fhirType(FHIRDefinedType.REFERENCE).stringEmpty("reference")
   * .stringEmpty("type")}.
   */
  @Nonnull
  public CompatModelBuilder fhirReference() {
    return fhirType(FHIRDefinedType.REFERENCE).stringEmpty("reference").stringEmpty("type");
  }
}
