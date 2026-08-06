package au.csiro.fhirpath.typing;

import static au.csiro.fhirpath.typing.Types.BOOLEAN;
import static au.csiro.fhirpath.typing.Types.CODING;
import static au.csiro.fhirpath.typing.Types.DATE;
import static au.csiro.fhirpath.typing.Types.DATE_TIME;
import static au.csiro.fhirpath.typing.Types.DECIMAL;
import static au.csiro.fhirpath.typing.Types.INTEGER;
import static au.csiro.fhirpath.typing.Types.QUANTITY;
import static au.csiro.fhirpath.typing.Types.STRING;
import static au.csiro.fhirpath.typing.Types.TIME;

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

  /** Comparable types that support ordering operations. Used for: gt, lt, geq, leq */
  public static final List<Type> COMPARABLE =
      List.of(INTEGER, DECIMAL, STRING, QUANTITY, DATE, DATE_TIME, TIME);

  /** Equatable types that support equality operations. Used for: equals, notEquals */
  public static final List<Type> EQUATABLE =
      List.of(INTEGER, DECIMAL, STRING, BOOLEAN, QUANTITY, CODING, DATE, DATE_TIME, TIME);

  /**
   * Temporal types: Date, DateTime, and Time. Used for: precision-aware equality and comparison.
   */
  public static final List<Type> TEMPORAL = List.of(DATE, DATE_TIME, TIME);

  /** String-like types. Used for string operations like add. */
  public static final List<Type> STRING_LIKE = List.of(STRING);
}
