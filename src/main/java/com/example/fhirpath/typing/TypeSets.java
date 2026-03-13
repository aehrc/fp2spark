package com.example.fhirpath.typing;

import static com.example.fhirpath.typing.Types.BOOLEAN;
import static com.example.fhirpath.typing.Types.DECIMAL;
import static com.example.fhirpath.typing.Types.INTEGER;
import static com.example.fhirpath.typing.Types.STRING;

import java.util.List;

/**
 * Predefined type sets representing semantic categories of FHIRPath types.
 *
 * <p>These sets group types that share common semantic properties (e.g., numeric types, comparable
 * types). They serve as the authoritative definition of type categories in the FHIRPath type
 * system.
 *
 * <p>These categories are used by the operation signature system and may be used for type
 * validation, error messages, and static analysis.
 */
public final class TypeSets {

  private TypeSets() {
    throw new AssertionError("No instances");
  }

  /**
   * Numeric types: Integer and Decimal. Used for: mod, ceiling, floor, truncate, exp, ln, log,
   * sqrt, add, sub, multiply, divide, abs
   */
  public static final List<Type> NUMERIC = List.of(INTEGER, DECIMAL);

  /**
   * Comparable types that support ordering operations. Used for: gt, lt, geq, leq
   *
   * <p>Temporal types (Date, DateTime, Time) will be added when temporal comparison is implemented.
   */
  public static final List<Type> COMPARABLE = List.of(INTEGER, DECIMAL, STRING);

  /**
   * Equatable types that support equality operations. Used for: equals, notEquals
   *
   * <p>Temporal types (Date, DateTime, Time) will be added when temporal equality is implemented.
   */
  public static final List<Type> EQUATABLE = List.of(INTEGER, DECIMAL, STRING, BOOLEAN);

  /** String-like types. Used for string operations like add. */
  public static final List<Type> STRING_LIKE = List.of(STRING);
}
