package com.example.fhirpath.compat;

import com.example.fhirpath.test.ResourceDataBuilder;
import com.example.fhirpath.typing.DateTimeValue;
import com.example.fhirpath.typing.DateValue;
import com.example.fhirpath.typing.TimeValue;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Adapter that provides Pathling's {@code FhirPathModelBuilder} API for building test subject data.
 *
 * <p>Delegates to {@link ResourceDataBuilder} for supported types and adds:
 *
 * <ul>
 *   <li>Empty helpers: {@code stringEmpty()}, {@code integerEmpty()}, etc. (map to null)
 *   <li>Primitive varargs: {@code integerArray(String, int...)}, etc. (boxes to wrapper types)
 *   <li>Temporal/quantity/coding methods via {@link ResourceDataBuilder}
 * </ul>
 *
 * <p>Unsupported methods ({@code choice()}, {@code fhirType()}, {@code fhirReference()}) throw
 * {@link UnsupportedOperationException}. Tests using these should be {@code @Disabled}.
 */
public class CompatModelBuilder {

  private final Map<String, Object> model = new HashMap<>();

  @Nonnull
  Map<String, Object> getModel() {
    return model;
  }

  // --- String ---

  @Nonnull
  public CompatModelBuilder string(@Nonnull final String name, @Nullable final String value) {
    model.put(name, value);
    return this;
  }

  @Nonnull
  public CompatModelBuilder stringEmpty(@Nonnull final String name) {
    model.put(name, null);
    return this;
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
    model.put(name, null);
    return this;
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
    model.put(name, null);
    return this;
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
    model.put(name, null);
    return this;
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
    model.put(name, null);
    return this;
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
    model.put(name, null);
    return this;
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
    model.put(name, null);
    return this;
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
    model.put(name, null);
    return this;
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
    model.put(name, value != null ? ResourceDataBuilder.parseCoding(value) : null);
    return this;
  }

  @Nonnull
  public CompatModelBuilder codingEmpty(@Nonnull final String name) {
    model.put(name, null);
    return this;
  }

  @Nonnull
  public CompatModelBuilder codingArray(
      @Nonnull final String name, @Nonnull final String... values) {
    model.put(name, Arrays.stream(values).map(ResourceDataBuilder::parseCoding).toList());
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

  // --- Unsupported Pathling-specific methods ---

  @Nonnull
  public CompatModelBuilder choice(@Nonnull final String name) {
    throw new UnsupportedOperationException("choice() is not supported in compat tests");
  }

  @Nonnull
  public CompatModelBuilder fhirType(@Nonnull final Object fhirType) {
    throw new UnsupportedOperationException("fhirType() is not supported in compat tests");
  }

  @Nonnull
  public CompatModelBuilder fhirReference() {
    throw new UnsupportedOperationException("fhirReference() is not supported in compat tests");
  }
}
