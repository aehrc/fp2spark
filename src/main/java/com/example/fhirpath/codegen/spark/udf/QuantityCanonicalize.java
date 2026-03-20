package com.example.fhirpath.codegen.spark.udf;

import com.example.fhirpath.codegen.spark.SparkTypeMapper;
import com.example.fhirpath.typing.QuantityValue;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.math.BigDecimal;
import java.util.Map;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.api.java.UDF1;
import org.apache.spark.sql.expressions.UserDefinedFunction;
import org.apache.spark.sql.functions;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.StructField;
import org.apache.spark.sql.types.StructType;

/**
 * Spark UDF that canonicalizes a FHIR Quantity to its UCUM base units.
 *
 * <p>Input: a quantity struct {@code (value, unit, system, code)}. Output: a struct {@code (value:
 * Decimal(38,6), code: String)} with the canonical value and code, or {@code null} if
 * canonicalization is not possible.
 *
 * <p>Handling by system:
 *
 * <ul>
 *   <li>UCUM system → canonicalize directly via {@link UcumService}
 *   <li>Calendar system, definite duration ({@code second}, {@code millisecond}) → convert to UCUM
 *       equivalent and canonicalize
 *   <li>Calendar system, non-definite duration ({@code year}, {@code month}, {@code week}, {@code
 *       day}, {@code hour}, {@code minute}) → {@code null} (per spec, not comparable with UCUM)
 *   <li>Other/null → {@code null}
 * </ul>
 */
public final class QuantityCanonicalize {

  private QuantityCanonicalize() {}

  /** Output schema: canonical value + canonical code. */
  static final StructType OUTPUT_TYPE =
      DataTypes.createStructType(
          new StructField[] {
            DataTypes.createStructField("value", SparkTypeMapper.DECIMAL_TYPE, true),
            DataTypes.createStructField("code", DataTypes.StringType, true),
          });

  /** Spark UDF that canonicalizes a quantity struct. */
  @Nonnull
  public static final UserDefinedFunction UDF =
      functions.udf((UDF1<Row, Row>) QuantityCanonicalize::canonicalize, OUTPUT_TYPE);

  /**
   * Calendar duration codes that have definite UCUM equivalents. Non-definite durations (year,
   * month, week, day, hour, minute) are intentionally absent — they cannot be canonicalized.
   */
  private static final Map<String, String> CALENDAR_TO_UCUM =
      Map.of(
          "second", "s",
          "millisecond", "ms");

  @Nullable
  static Row canonicalize(@Nullable final Row row) {
    if (row == null) {
      return null;
    }

    final BigDecimal value = row.getDecimal(0);
    final String system = row.getString(2);
    final String code = row.getString(3);

    if (value == null || system == null || code == null) {
      return null;
    }

    return switch (system) {
      case QuantityValue.UCUM_SYSTEM -> canonicalizeUcum(value, code);
      case QuantityValue.CALENDAR_SYSTEM -> canonicalizeCalendar(value, code);
      default -> null;
    };
  }

  @Nullable
  private static Row canonicalizeUcum(@Nonnull final BigDecimal value, @Nonnull final String code) {
    final UcumService.Canonical canonical = UcumService.canonicalize(value, code);
    return canonical != null ? toRow(canonical) : null;
  }

  @Nullable
  private static Row canonicalizeCalendar(
      @Nonnull final BigDecimal value, @Nonnull final String code) {
    final String ucumCode = CALENDAR_TO_UCUM.get(code);
    if (ucumCode == null) {
      // Non-definite duration — cannot canonicalize
      return null;
    }
    return canonicalizeUcum(value, ucumCode);
  }

  @Nonnull
  private static Row toRow(@Nonnull final UcumService.Canonical canonical) {
    return org.apache.spark.sql.RowFactory.create(canonical.value(), canonical.code());
  }
}
