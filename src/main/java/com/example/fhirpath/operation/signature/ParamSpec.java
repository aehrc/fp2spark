package com.example.fhirpath.operation.signature;

import com.example.fhirpath.typing.Cardinality;
import com.example.fhirpath.typing.Type;
import jakarta.annotation.Nonnull;

/**
 * Parameter specification: a concrete element type plus cardinality.
 *
 * <p>Polymorphism is currently expressed by enumeration via {@code forTypes(GROUP).define(...)}
 * rather than by type variables — see #260 for the refactor that would introduce type variables and
 * type-set constraints if/when the enumeration approach starts to bite.
 *
 * @param type the element type of the parameter
 * @param cardinality the cardinality of the parameter (SINGLE or MANY)
 */
public record ParamSpec(@Nonnull Type type, @Nonnull Cardinality cardinality) {
  /** Creates a parameter spec for a SINGLE cardinality parameter (?T). */
  @Nonnull
  public static ParamSpec single(@Nonnull final Type type) {
    return new ParamSpec(type, Cardinality.SINGLE);
  }

  /** Creates a parameter spec for a MANY cardinality parameter (*T). */
  @Nonnull
  public static ParamSpec many(@Nonnull final Type type) {
    return new ParamSpec(type, Cardinality.MANY);
  }

  @Override
  public String toString() {
    return (cardinality == Cardinality.SINGLE ? "?" : "*") + type.getName();
  }
}
