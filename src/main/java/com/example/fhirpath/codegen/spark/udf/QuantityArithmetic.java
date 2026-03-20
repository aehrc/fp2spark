package com.example.fhirpath.codegen.spark.udf;

import static com.example.fhirpath.codegen.spark.SparkTypeMapper.QUANTITY_TYPE;

import com.example.fhirpath.typing.QuantityValue;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Map;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.RowFactory;
import org.apache.spark.sql.api.java.UDF3;
import org.apache.spark.sql.expressions.UserDefinedFunction;
import org.apache.spark.sql.functions;

/**
 * Spark UDF for quantity arithmetic operations (+, -, *, /).
 *
 * <p>Handles UCUM-aware unit conversion and unit algebra:
 *
 * <ul>
 *   <li><b>Addition/subtraction:</b> requires same dimension (commensurable units). Converts both
 *       operands to the most granular unit and computes the result.
 *   <li><b>Multiplication:</b> multiplies values and combines unit codes (e.g., cm * cm → cm2).
 *   <li><b>Division:</b> divides values and combines unit codes (e.g., cm2 / cm → cm). Division by
 *       zero returns empty ({@code null}).
 * </ul>
 *
 * <p>Returns {@code null} (empty collection) when units are incompatible, when either operand is
 * null, or when the operation is not possible.
 */
public final class QuantityArithmetic {

  private QuantityArithmetic() {}

  /** Operation codes passed as the third argument to the UDF. */
  public static final String OP_ADD = "add";

  public static final String OP_SUB = "sub";
  public static final String OP_MUL = "mul";
  public static final String OP_DIV = "div";

  /**
   * Calendar duration codes that have definite UCUM equivalents. Mirrors {@link
   * QuantityCanonicalize#CALENDAR_TO_UCUM}.
   */
  private static final Map<String, String> CALENDAR_TO_UCUM =
      Map.of(
          "second", "s",
          "millisecond", "ms");

  /** The UDF instance: (Row, Row, String) → Row. */
  @Nonnull
  public static final UserDefinedFunction UDF =
      functions.udf((UDF3<Row, Row, String, Row>) QuantityArithmetic::compute, QUANTITY_TYPE);

  @Nullable
  static Row compute(
      @Nullable final Row left, @Nullable final Row right, @Nullable final String op) {
    if (left == null || right == null || op == null) {
      return null;
    }

    final BigDecimal leftValue = left.getDecimal(0);
    final String leftUnit = left.getString(1);
    final String leftSystem = left.getString(2);
    final String leftCode = left.getString(3);

    final BigDecimal rightValue = right.getDecimal(0);
    final String rightUnit = right.getString(1);
    final String rightSystem = right.getString(2);
    final String rightCode = right.getString(3);

    if (leftValue == null
        || leftCode == null
        || leftSystem == null
        || rightValue == null
        || rightCode == null
        || rightSystem == null) {
      return null;
    }

    return switch (op) {
      case OP_ADD, OP_SUB ->
          addOrSubtract(
              leftValue,
              leftUnit,
              leftSystem,
              leftCode,
              rightValue,
              rightUnit,
              rightSystem,
              rightCode,
              op.equals(OP_ADD));
      case OP_MUL ->
          multiply(
              leftValue,
              leftUnit,
              leftSystem,
              leftCode,
              rightValue,
              rightUnit,
              rightSystem,
              rightCode);
      case OP_DIV ->
          divide(
              leftValue,
              leftUnit,
              leftSystem,
              leftCode,
              rightValue,
              rightUnit,
              rightSystem,
              rightCode);
      default -> null;
    };
  }

  /**
   * Addition or subtraction of two quantities.
   *
   * <p>Both must be UCUM (or calendar definite durations convertible to UCUM). They must be
   * commensurable (same dimension). The result uses the most granular unit.
   */
  @Nullable
  private static Row addOrSubtract(
      @Nonnull final BigDecimal leftValue,
      @Nullable final String leftUnit,
      @Nonnull final String leftSystem,
      @Nonnull final String leftCode,
      @Nonnull final BigDecimal rightValue,
      @Nullable final String rightUnit,
      @Nonnull final String rightSystem,
      @Nonnull final String rightCode,
      final boolean isAdd) {

    // Resolve both operands to UCUM codes
    final String leftUcum = toUcumCode(leftSystem, leftCode);
    final String rightUcum = toUcumCode(rightSystem, rightCode);
    if (leftUcum == null || rightUcum == null) {
      return null;
    }

    // Same code: no conversion needed
    if (leftUcum.equals(rightUcum)) {
      final BigDecimal result = isAdd ? leftValue.add(rightValue) : leftValue.subtract(rightValue);
      return quantityRow(result, leftUnit, leftSystem, leftCode);
    }

    // Check commensurability
    if (!UcumService.areCommensurable(leftUcum, rightUcum)) {
      return null;
    }

    // Find most granular unit: the one whose canonical value for 1 unit is smaller
    final String granularUcum = mostGranularUnit(leftUcum, rightUcum);
    if (granularUcum == null) {
      return null;
    }

    // Convert both values to the granular unit
    final BigDecimal leftConverted = UcumService.convertValue(leftValue, leftUcum, granularUcum);
    final BigDecimal rightConverted = UcumService.convertValue(rightValue, rightUcum, granularUcum);
    if (leftConverted == null || rightConverted == null) {
      return null;
    }

    final BigDecimal result =
        isAdd ? leftConverted.add(rightConverted) : leftConverted.subtract(rightConverted);

    // Resolve unit/system from the granular UCUM code
    final String resultUnit = granularUcum;
    return quantityRow(result, resultUnit, QuantityValue.UCUM_SYSTEM, granularUcum);
  }

  /** Multiplication of two quantities. Values are multiplied, unit codes are combined. */
  @Nullable
  private static Row multiply(
      @Nonnull final BigDecimal leftValue,
      @Nullable final String leftUnit,
      @Nonnull final String leftSystem,
      @Nonnull final String leftCode,
      @Nonnull final BigDecimal rightValue,
      @Nullable final String rightUnit,
      @Nonnull final String rightSystem,
      @Nonnull final String rightCode) {

    final String leftUcum = toUcumCode(leftSystem, leftCode);
    final String rightUcum = toUcumCode(rightSystem, rightCode);
    if (leftUcum == null || rightUcum == null) {
      return null;
    }

    final BigDecimal result = leftValue.multiply(rightValue);
    final String resultCode = UcumService.multiplyUnits(leftUcum, rightUcum);
    if (resultCode == null) {
      return null;
    }

    return quantityRow(result, resultCode, QuantityValue.UCUM_SYSTEM, resultCode);
  }

  /**
   * Division of two quantities. Values are divided, unit codes are combined. Division by zero
   * returns null (empty).
   */
  @Nullable
  private static Row divide(
      @Nonnull final BigDecimal leftValue,
      @Nullable final String leftUnit,
      @Nonnull final String leftSystem,
      @Nonnull final String leftCode,
      @Nonnull final BigDecimal rightValue,
      @Nullable final String rightUnit,
      @Nonnull final String rightSystem,
      @Nonnull final String rightCode) {

    if (rightValue.compareTo(BigDecimal.ZERO) == 0) {
      return null;
    }

    final String leftUcum = toUcumCode(leftSystem, leftCode);
    final String rightUcum = toUcumCode(rightSystem, rightCode);
    if (leftUcum == null || rightUcum == null) {
      return null;
    }

    final BigDecimal result = leftValue.divide(rightValue, MathContext.DECIMAL128);
    final String resultCode = UcumService.divideUnits(leftUcum, rightUcum);
    if (resultCode == null) {
      return null;
    }

    return quantityRow(result, resultCode, QuantityValue.UCUM_SYSTEM, resultCode);
  }

  /**
   * Resolves a system+code pair to a UCUM code. Calendar definite durations are mapped to their
   * UCUM equivalents. Returns {@code null} for non-convertible systems.
   */
  @Nullable
  private static String toUcumCode(@Nonnull final String system, @Nonnull final String code) {
    return switch (system) {
      case QuantityValue.UCUM_SYSTEM -> code;
      case QuantityValue.CALENDAR_SYSTEM -> CALENDAR_TO_UCUM.get(code);
      default -> null;
    };
  }

  /**
   * Determines the most granular of two UCUM codes by comparing their canonical values for 1 unit.
   * The code with the smaller canonical value is more granular (e.g., cm < m).
   */
  @Nullable
  private static String mostGranularUnit(@Nonnull final String code1, @Nonnull final String code2) {
    final UcumService.Canonical canon1 = UcumService.canonicalize(BigDecimal.ONE, code1);
    final UcumService.Canonical canon2 = UcumService.canonicalize(BigDecimal.ONE, code2);
    if (canon1 == null || canon2 == null) {
      return null;
    }
    return canon1.value().compareTo(canon2.value()) <= 0 ? code1 : code2;
  }

  @Nonnull
  private static Row quantityRow(
      @Nonnull final BigDecimal value,
      @Nullable final String unit,
      @Nonnull final String system,
      @Nonnull final String code) {
    return RowFactory.create(value, unit, system, code);
  }
}
