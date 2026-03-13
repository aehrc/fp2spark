package com.example.fhirpath.typing;

import jakarta.annotation.Nonnull;
import java.util.stream.Stream;

/** Utilities for working with the FHIRPath type system. */
public final class TypeSystem {
  private TypeSystem() {}

  /**
   * Checks if a value of type {@code from} can be adapted/cast to type {@code to}.
   *
   * <p>Implements the adaptation rules from the FHIRPath type system:
   *
   * <ul>
   *   <li>INTEGER → DECIMAL
   *   <li>DATE → DATE_TIME
   *   <li>NULL → any type
   * </ul>
   */
  public static boolean canCast(final Type from, final Type to) {
    if (from == to) return true;
    if (from == PrimitiveType.NULL) return true;

    if (from == PrimitiveType.INTEGER && to == PrimitiveType.DECIMAL) return true;
    if (from == PrimitiveType.DATE && to == PrimitiveType.DATE_TIME) return true;

    return false;
  }

  /**
   * Returns a stream of all primitive types, including ANY.
   *
   * @return stream of all {@link PrimitiveType} values
   */
  @Nonnull
  public static Stream<Type> allTypes() {
    return Stream.of(PrimitiveType.values());
  }

  /**
   * Returns a stream of all defined primitive types, excluding the ANY wildcard.
   *
   * @return stream of concrete {@link PrimitiveType} values
   */
  @Nonnull
  public static Stream<Type> definedTypes() {
    return Stream.of(PrimitiveType.values())
        .map(Type.class::cast)
        .filter(t -> t != PrimitiveType.ANY);
  }
}
