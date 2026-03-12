package com.example.fhirpath.operation.signature;

import com.example.fhirpath.typing.Cardinality;
import com.example.fhirpath.typing.Type;
import jakarta.annotation.Nonnull;

/**
 * Parameter specification for Phase 1: simple type + cardinality.
 *
 * <p>In Phase 1, we enumerate types explicitly without type variables. Each parameter has a
 * concrete type and cardinality.
 *
 * <p>Phase 2 will add support for type variables and constraints.
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
