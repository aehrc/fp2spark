package com.example.fhirpath.codegen.spark.udf;

import io.github.fhnaumann.funcs.CanonicalizerService;
import io.github.fhnaumann.funcs.ConverterService;
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
