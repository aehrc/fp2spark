package com.example.fhirpath.codegen.spark.ops;

import static org.apache.spark.sql.functions.lit;
import static org.apache.spark.sql.functions.when;

import com.example.fhirpath.codegen.spark.SparkOperationDef;
import com.example.fhirpath.codegen.spark.SparkOperationRegistry;
import com.example.fhirpath.codegen.spark.SparkTypeMapper;
import com.example.fhirpath.typing.PrimitiveType;
import com.example.fhirpath.typing.QuantityValue;
import jakarta.annotation.Nonnull;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.functions;
import org.apache.spark.sql.types.DataTypes;

/**
 * Spark code generation for FHIRPath type conversion and validation functions.
 *
 * <p>Implements explicit conversion functions ({@code toBoolean}, {@code toInteger}, etc.) and
 * their validation counterparts ({@code convertsToBoolean}, {@code convertsToInteger}, etc.) per
 * FHIRPath spec section 5.7.
 *
 * <p>Conversion functions return the converted value or empty (null) on failure. Validation
 * functions return true/false indicating whether conversion would succeed.
 *
 * <p>Conversion logic is dispatched by source type (the input's FHIRPath type), following the
 * FHIRPath conversion matrix. Adapted from Pathling's ConversionLogic and ValidationLogic.
 */
public final class ConversionOps {

  // Regex patterns for string validation (from FHIRPath spec)
  private static final String INTEGER_REGEX = "^(\\+|-)?\\d+$";
  private static final String DECIMAL_REGEX = "^(\\+|-)?\\d+(\\.\\d+)?$";
  private static final String DATE_REGEX = "^\\d{4}(-\\d{2}(-\\d{2})?)?$";
  private static final String DATETIME_REGEX =
      "^\\d{4}(-\\d{2}(-\\d{2}(T\\d{2}(:\\d{2}(:\\d{2}(\\.\\d+)?)?)?"
          + "(Z|[+\\-]\\d{2}:\\d{2})?)?)?)?$";
  private static final String TIME_REGEX = "^\\d{2}(:\\d{2}(:\\d{2}(\\.\\d+)?)?)?$";
  private static final String QUANTITY_REGEX =
      "^[+-]?\\d+(?:\\.\\d+)?\\s*(?:'[^']+'|"
          + "(?i:years?|months?|weeks?|days?|hours?|minutes?|seconds?|milliseconds?))?$";

  private ConversionOps() {}

  /**
   * Registers all conversion and validation operations into the given registry.
   *
   * @param registry the registry to register operations into
   */
  public static void register(final SparkOperationRegistry registry) {
    // Conversion functions
    registry.register("toBoolean", conversion(ConversionOps::convertToBoolean));
    registry.register("toInteger", conversion(ConversionOps::convertToInteger));
    registry.register("toDecimal", conversion(ConversionOps::convertToDecimal));
    registry.register("toString", conversion(ConversionOps::convertToString));
    registry.register("toDate", conversion(ConversionOps::convertToDate));
    registry.register("toDateTime", conversion(ConversionOps::convertToDateTime));
    registry.register("toTime", conversion(ConversionOps::convertToTime));
    registry.register("toQuantity", conversion(ConversionOps::convertToQuantity));

    // Validation functions
    registry.register("convertsToBoolean", validation(ConversionOps::validateToBoolean));
    registry.register("convertsToInteger", validation(ConversionOps::validateToInteger));
    registry.register("convertsToDecimal", validation(ConversionOps::validateToDecimal));
    registry.register("convertsToString", validation(ConversionOps::validateToString));
    registry.register("convertsToDate", validation(ConversionOps::validateToDate));
    registry.register("convertsToDateTime", validation(ConversionOps::validateToDateTime));
    registry.register("convertsToTime", validation(ConversionOps::validateToTime));
    registry.register("convertsToQuantity", validation(ConversionOps::validateToQuantity));
  }

  /**
   * Wraps a type-dispatched conversion function as a SparkOperationDef. Identity conversions (same
   * source and target type) return the input unchanged.
   */
  @Nonnull
  private static SparkOperationDef conversion(@Nonnull final ConversionFunction conversionFn) {
    return ctx -> {
      final Column input = ctx.arg(0);
      final PrimitiveType sourceType = ctx.primitiveArgType(0);
      final PrimitiveType targetType = ctx.primitiveResultType();
      // Identity: same type returns unchanged
      if (sourceType == targetType) {
        return input;
      }
      return conversionFn.convert(sourceType, input);
    };
  }

  /**
   * Wraps a type-dispatched validation function as a SparkOperationDef. Identity validations (same
   * source and target type) return true.
   */
  @Nonnull
  private static SparkOperationDef validation(@Nonnull final ValidationFunction validationFn) {
    return ctx -> {
      final PrimitiveType sourceType = ctx.primitiveArgType(0);
      // Empty input → empty result (FHIRPath spec: empty propagation)
      if (sourceType == PrimitiveType.NULL) {
        return lit(null);
      }
      final Column input = ctx.arg(0);
      return validationFn.validate(sourceType, input);
    };
  }

  @FunctionalInterface
  private interface ConversionFunction {
    Column convert(PrimitiveType sourceType, Column value);
  }

  @FunctionalInterface
  private interface ValidationFunction {
    Column validate(PrimitiveType sourceType, Column value);
  }

  // ========== Conversion Functions ==========

  /**
   * Converts to Boolean. String: 'true'/'false'/'t'/'f'/'1'/'0'/'1.0'/'0.0'. Integer: 0→false,
   * 1→true. Decimal: 0.0→false, 1.0→true.
   */
  @Nonnull
  private static Column convertToBoolean(
      @Nonnull final PrimitiveType sourceType, @Nonnull final Column value) {
    return switch (sourceType) {
      case STRING ->
          when(value.equalTo(lit("1.0")), lit(true))
              .when(value.equalTo(lit("0.0")), lit(false))
              .otherwise(value.try_cast(DataTypes.BooleanType));
      case INTEGER ->
          when(value.equalTo(lit(1)), lit(true)).when(value.equalTo(lit(0)), lit(false));
      case DECIMAL ->
          when(value.equalTo(lit(1.0)), lit(true)).when(value.equalTo(lit(0.0)), lit(false));
      default -> lit(null);
    };
  }

  /** Converts to Integer. Boolean: true→1, false→0. String: integer regex then cast. */
  @Nonnull
  private static Column convertToInteger(
      @Nonnull final PrimitiveType sourceType, @Nonnull final Column value) {
    return switch (sourceType) {
      case BOOLEAN -> value.try_cast(DataTypes.IntegerType);
      case STRING -> when(value.rlike(INTEGER_REGEX), value.try_cast(DataTypes.IntegerType));
      default -> lit(null);
    };
  }

  /** Converts to Decimal. Boolean/Integer/String: cast to decimal. */
  @Nonnull
  private static Column convertToDecimal(
      @Nonnull final PrimitiveType sourceType, @Nonnull final Column value) {
    return switch (sourceType) {
      case BOOLEAN, INTEGER, STRING -> value.try_cast(SparkTypeMapper.DECIMAL_TYPE);
      default -> lit(null);
    };
  }

  /**
   * Converts to String. All primitive types can be converted. Temporal types (Date, DateTime, Time)
   * are already stored as strings in Spark, so cast is identity. Quantity requires struct
   * formatting.
   */
  @Nonnull
  private static Column convertToString(
      @Nonnull final PrimitiveType sourceType, @Nonnull final Column value) {
    return switch (sourceType) {
      case BOOLEAN, INTEGER, DATE, DATE_TIME, TIME -> value.try_cast(DataTypes.StringType);
      case DECIMAL -> decimalToString(value);
      case QUANTITY -> quantityToString(value);
      default -> lit(null);
    };
  }

  /** Converts to Date. Only String with valid date format. */
  @Nonnull
  private static Column convertToDate(
      @Nonnull final PrimitiveType sourceType, @Nonnull final Column value) {
    return switch (sourceType) {
      case STRING -> when(value.rlike(DATE_REGEX), value);
      case DATE_TIME -> extractDateFromDateTime(value);
      default -> lit(null);
    };
  }

  /** Converts to DateTime. String with valid dateTime format. Date is implicitly converted. */
  @Nonnull
  private static Column convertToDateTime(
      @Nonnull final PrimitiveType sourceType, @Nonnull final Column value) {
    return switch (sourceType) {
      case STRING -> when(value.rlike(DATETIME_REGEX), value);
      case DATE -> value; // Date→DateTime: date string is valid dateTime (partial precision)
      default -> lit(null);
    };
  }

  /** Converts to Time. Only String with valid time format. */
  @Nonnull
  private static Column convertToTime(
      @Nonnull final PrimitiveType sourceType, @Nonnull final Column value) {
    return switch (sourceType) {
      case STRING -> when(value.rlike(TIME_REGEX), value);
      default -> lit(null);
    };
  }

  /**
   * Converts to Quantity. Boolean/Integer/Decimal: wrap with default unit '1'. String: parse
   * quantity literal.
   */
  @Nonnull
  private static Column convertToQuantity(
      @Nonnull final PrimitiveType sourceType, @Nonnull final Column value) {
    return switch (sourceType) {
      case BOOLEAN -> numericToQuantity(value.try_cast(SparkTypeMapper.DECIMAL_TYPE));
      case INTEGER, DECIMAL -> numericToQuantity(value.cast(SparkTypeMapper.DECIMAL_TYPE));
      case STRING -> stringToQuantity(value);
      default -> lit(null).cast(SparkTypeMapper.QUANTITY_TYPE);
    };
  }

  // ========== Validation Functions ==========

  /** Validates conversion to Boolean. */
  @Nonnull
  private static Column validateToBoolean(
      @Nonnull final PrimitiveType sourceType, @Nonnull final Column value) {
    return switch (sourceType) {
      case BOOLEAN -> lit(true);
      case STRING -> {
        final Column is10or00 = value.equalTo(lit("1.0")).or(value.equalTo(lit("0.0")));
        final Column castSucceeds = value.try_cast(DataTypes.BooleanType).isNotNull();
        yield value.isNotNull().and(is10or00.or(castSucceeds));
      }
      case INTEGER -> value.equalTo(lit(0)).or(value.equalTo(lit(1)));
      case DECIMAL -> value.equalTo(lit(0.0)).or(value.equalTo(lit(1.0)));
      default -> lit(false);
    };
  }

  /** Validates conversion to Integer. */
  @Nonnull
  private static Column validateToInteger(
      @Nonnull final PrimitiveType sourceType, @Nonnull final Column value) {
    return switch (sourceType) {
      case INTEGER -> lit(true);
      case BOOLEAN -> lit(true);
      case STRING -> value.rlike(INTEGER_REGEX);
      default -> lit(false);
    };
  }

  /** Validates conversion to Decimal. */
  @Nonnull
  private static Column validateToDecimal(
      @Nonnull final PrimitiveType sourceType, @Nonnull final Column value) {
    return switch (sourceType) {
      case DECIMAL -> lit(true);
      case BOOLEAN, INTEGER -> lit(true);
      case STRING -> value.rlike(DECIMAL_REGEX);
      default -> lit(false);
    };
  }

  /** Validates conversion to String. All primitive types can convert to String. */
  @Nonnull
  private static Column validateToString(
      @Nonnull final PrimitiveType sourceType, @Nonnull final Column value) {
    return switch (sourceType) {
      case STRING, BOOLEAN, INTEGER, DECIMAL, DATE, DATE_TIME, TIME, QUANTITY -> lit(true);
      default -> lit(false);
    };
  }

  /** Validates conversion to Date. */
  @Nonnull
  private static Column validateToDate(
      @Nonnull final PrimitiveType sourceType, @Nonnull final Column value) {
    return switch (sourceType) {
      case DATE -> lit(true);
      case DATE_TIME -> lit(true);
      case STRING -> value.rlike(DATE_REGEX);
      default -> lit(false);
    };
  }

  /** Validates conversion to DateTime. */
  @Nonnull
  private static Column validateToDateTime(
      @Nonnull final PrimitiveType sourceType, @Nonnull final Column value) {
    return switch (sourceType) {
      case DATE_TIME -> lit(true);
      case DATE -> lit(true);
      case STRING -> value.rlike(DATETIME_REGEX);
      default -> lit(false);
    };
  }

  /** Validates conversion to Time. */
  @Nonnull
  private static Column validateToTime(
      @Nonnull final PrimitiveType sourceType, @Nonnull final Column value) {
    return switch (sourceType) {
      case TIME -> lit(true);
      case STRING -> value.rlike(TIME_REGEX);
      default -> lit(false);
    };
  }

  /** Validates conversion to Quantity. */
  @Nonnull
  private static Column validateToQuantity(
      @Nonnull final PrimitiveType sourceType, @Nonnull final Column value) {
    return switch (sourceType) {
      case QUANTITY -> lit(true);
      case BOOLEAN, INTEGER, DECIMAL -> lit(true);
      case STRING -> value.rlike(QUANTITY_REGEX);
      default -> lit(false);
    };
  }

  // ========== Helper Methods ==========

  /**
   * Converts a decimal value to string, stripping trailing zeros. Uses Spark's format_number with
   * trim to remove trailing zeros while preserving at least one decimal digit.
   */
  @Nonnull
  private static Column decimalToString(@Nonnull final Column value) {
    // Cast to string, then trim trailing zeros after decimal point
    // Spark's cast to string for Decimal includes trailing zeros (e.g., "1.000000")
    // We use regexp_replace to clean up: remove trailing zeros, and trailing dot
    final Column str = value.cast(DataTypes.StringType);
    return when(
            str.contains(lit(".")),
            functions.regexp_replace(functions.regexp_replace(str, "0+$", ""), "\\.$", ""))
        .otherwise(str);
  }

  /**
   * Converts a Quantity struct to its FHIRPath string representation. Format: {@code value 'code'}
   * for non-default units, or just {@code value} for unit '1'.
   */
  @Nonnull
  private static Column quantityToString(@Nonnull final Column value) {
    final Column qValue = decimalToString(value.getField("value"));
    final Column code = value.getField("code");
    return when(code.equalTo(lit(QuantityValue.DEFAULT_UNIT)), qValue)
        .otherwise(functions.concat(qValue, lit(" '"), code, lit("'")));
  }

  /** Wraps a numeric value as a Quantity struct with default unit '1'. Returns null for null. */
  @Nonnull
  private static Column numericToQuantity(@Nonnull final Column decimalValue) {
    return when(
        decimalValue.isNotNull(),
        functions.struct(
            decimalValue.as("value"),
            lit(QuantityValue.DEFAULT_UNIT).as("unit"),
            lit(QuantityValue.UCUM_SYSTEM).as("system"),
            lit(QuantityValue.DEFAULT_UNIT).as("code")));
  }

  /**
   * Parses a string as a FHIRPath quantity literal. Format: {@code value 'unit'} or {@code value
   * calendarUnit} or just {@code value}. Returns null if the string doesn't match quantity format.
   */
  @Nonnull
  private static Column stringToQuantity(@Nonnull final Column value) {
    // Validate format first
    final Column matches = value.rlike(QUANTITY_REGEX);

    // Extract numeric value and unit parts using regex
    // The value part is everything up to optional whitespace + unit
    final Column numericPart = functions.regexp_extract(value, "^([+-]?\\d+(?:\\.\\d+)?)", 1);
    final Column decimalValue = numericPart.cast(SparkTypeMapper.DECIMAL_TYPE);

    // Extract unit: quoted UCUM unit or bareword calendar unit
    // Group 1: quoted unit (without quotes), Group 2: calendar unit
    final Column quotedUnit = functions.regexp_extract(value, "'([^']+)'", 1);
    final Column calendarUnit =
        functions.regexp_extract(
            value,
            "(?i)(years?|months?|weeks?|days?|hours?|minutes?|seconds?|milliseconds?)\\s*$",
            1);

    // Determine the effective unit code
    final Column hasQuotedUnit = quotedUnit.notEqual(lit(""));
    final Column hasCalendarUnit = calendarUnit.notEqual(lit(""));
    final Column unitCode =
        when(hasQuotedUnit, quotedUnit)
            .when(hasCalendarUnit, calendarUnit)
            .otherwise(lit(QuantityValue.DEFAULT_UNIT));

    // Determine the system URI
    final Column system =
        when(hasCalendarUnit, lit(QuantityValue.CALENDAR_SYSTEM))
            .otherwise(lit(QuantityValue.UCUM_SYSTEM));

    final Column quantityStruct =
        functions.struct(
            decimalValue.as("value"),
            unitCode.as("unit"),
            system.as("system"),
            unitCode.as("code"));

    return when(matches, quantityStruct);
  }

  /**
   * Extracts the date portion from a DateTime string. DateTime strings start with the date part
   * (YYYY, YYYY-MM, or YYYY-MM-DD) followed optionally by 'T' and time components.
   */
  @Nonnull
  private static Column extractDateFromDateTime(@Nonnull final Column value) {
    // Extract everything before 'T' (the date part)
    // If no 'T', the entire string is a date-precision DateTime
    return when(value.contains(lit("T")), functions.regexp_extract(value, "^([^T]+)", 1))
        .otherwise(value);
  }
}
