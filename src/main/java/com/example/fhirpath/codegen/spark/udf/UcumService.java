package com.example.fhirpath.codegen.spark.udf;

import io.github.fhnaumann.funcs.CanonicalizerService;
import io.github.fhnaumann.funcs.ConverterService;
import io.github.fhnaumann.funcs.RelationCheckerService;
import io.github.fhnaumann.funcs.UCUMService;
import io.github.fhnaumann.model.UCUMExpression.CanonicalTerm;
import io.github.fhnaumann.util.PreciseDecimal;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.math.BigDecimal;

/**
 * Wraps the ucumate UCUM service for unit canonicalization and conversion.
 *
 * <p>Modeled after Pathling's {@code Ucum.java}. Returns {@code null} on failure (invalid units,
 * incompatible dimensions) rather than throwing.
 */
final class UcumService {

  /** A canonical value+code pair. */
  record Canonical(@Nonnull BigDecimal value, @Nonnull String code) {}

  private static final UCUMService SERVICE = new UCUMService();
  private static final String NO_UNIT_CODE = "1";

  private UcumService() {}

  /**
   * Canonicalizes a value and UCUM code to their base units.
   *
   * @param value the numeric value
   * @param code the UCUM unit code
   * @return canonical value+code, or {@code null} if canonicalization fails
   */
  @Nullable
  static Canonical canonicalize(@Nullable final BigDecimal value, @Nullable final String code) {
    if (value == null || code == null) {
      return null;
    }
    try {
      final CanonicalizerService.CanonicalizationResult result =
          SERVICE.canonicalize(new PreciseDecimal(value.toPlainString()), code);

      if (!(result
          instanceof
          CanonicalizerService.Success(PreciseDecimal magnitude, CanonicalTerm canonicalTerm))) {
        return null;
      }
      if (magnitude == null) {
        return null;
      }

      @Nullable final String canonicalCode = SERVICE.print(canonicalTerm);
      if (canonicalCode == null) {
        return null;
      }

      final String adjustedCode = canonicalCode.isEmpty() ? NO_UNIT_CODE : canonicalCode;
      return new Canonical(magnitude.getValue(), adjustedCode);
    } catch (final Exception e) {
      return null;
    }
  }

  /**
   * Checks whether two UCUM codes are commensurable (same dimension).
   *
   * @param code1 the first UCUM code
   * @param code2 the second UCUM code
   * @return true if the codes have the same dimension, false otherwise
   */
  static boolean areCommensurable(@Nonnull final String code1, @Nonnull final String code2) {
    try {
      final RelationCheckerService.CommensurableResult result =
          SERVICE.checkCommensurable(code1, code2);
      return result instanceof RelationCheckerService.IsCommensurable;
    } catch (final Exception e) {
      return false;
    }
  }

  /**
   * Computes the UCUM code resulting from multiplying two unit codes.
   *
   * @param code1 the first UCUM code
   * @param code2 the second UCUM code
   * @return the product unit code (e.g., "cm.cm" → "cm2"), or {@code null} on failure
   */
  @Nullable
  static String multiplyUnits(@Nonnull final String code1, @Nonnull final String code2) {
    try {
      return SERVICE.print(code1 + "." + code2);
    } catch (final Exception e) {
      return null;
    }
  }

  /**
   * Computes the UCUM code resulting from dividing two unit codes.
   *
   * @param code1 the numerator UCUM code
   * @param code2 the denominator UCUM code
   * @return the quotient unit code (e.g., "cm2/cm" → "cm"), or {@code null} on failure
   */
  @Nullable
  static String divideUnits(@Nonnull final String code1, @Nonnull final String code2) {
    try {
      final String resultCode = SERVICE.print(code1 + "/" + code2);
      return resultCode == null || resultCode.isEmpty() ? NO_UNIT_CODE : resultCode;
    } catch (final Exception e) {
      return null;
    }
  }

  /**
   * Converts a value from one UCUM unit to another.
   *
   * @param value the numeric value
   * @param fromCode the source UCUM code
   * @param toCode the target UCUM code
   * @return the converted value, or {@code null} if conversion is not possible
   */
  @Nullable
  static BigDecimal convertValue(
      @Nullable final BigDecimal value,
      @Nullable final String fromCode,
      @Nullable final String toCode) {
    if (value == null || fromCode == null || toCode == null) {
      return null;
    }
    try {
      final ConverterService.ConversionResult conversionResult =
          SERVICE.convert(new PreciseDecimal(value.toPlainString()), fromCode, toCode);

      if (!(conversionResult instanceof ConverterService.Success(var convertedValue))
          || convertedValue == null) {
        return null;
      }
      return convertedValue.getValue();
    } catch (final Exception e) {
      return null;
    }
  }
}
