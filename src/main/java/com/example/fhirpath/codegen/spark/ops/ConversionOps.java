package com.example.fhirpath.codegen.spark.ops;

import static org.apache.spark.sql.functions.lit;
import static org.apache.spark.sql.functions.when;

import com.example.fhirpath.codegen.spark.SparkOperationDef;
import com.example.fhirpath.codegen.spark.SparkOperationRegistry;
import com.example.fhirpath.codegen.spark.SparkTypeMapper;
import com.example.fhirpath.codegen.spark.udf.QuantityConvertToUnit;
import com.example.fhirpath.typing.QuantityValue;
import com.example.fhirpath.typing.SystemType;
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
    registry.register("toQuantity", toQuantityWithUnit());

    // Validation functions
    registry.register("convertsToBoolean", validation(ConversionOps::validateToBoolean));
    registry.register("convertsToInteger", validation(ConversionOps::validateToInteger));
    registry.register("convertsToDecimal", validation(ConversionOps::validateToDecimal));
    registry.register("convertsToString", validation(ConversionOps::validateToString));
    registry.register("convertsToDate", validation(ConversionOps::validateToDate));
    registry.register("convertsToDateTime", validation(ConversionOps::validateToDateTime));
    registry.register("convertsToTime", validation(ConversionOps::validateToTime));
    registry.register("convertsToQuantity", convertsToQuantityWithUnit());
  }

  /**
   * Wraps a type-dispatched conversion function as a SparkOperationDef. Identity conversions (same
   * source and target type) return the input unchanged.
   */
  @Nonnull
  private static SparkOperationDef conversion(@Nonnull final ConversionFunction conversionFn) {
    return ctx -> {
      final SystemType sourceType = ctx.systemArgType(0);
      // Empty input → empty result (FHIRPath spec: empty propagation)
      if (sourceType == SystemType.NULL) {
        return lit(null);
      }
      final Column input = ctx.arg(0);
      // Identity: same type returns unchanged
      if (sourceType == ctx.systemResultType()) {
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
      final SystemType sourceType = ctx.systemArgType(0);
      // Empty input → empty result (FHIRPath spec: empty propagation)
      if (sourceType == SystemType.NULL) {
        return lit(null);
      }
      final Column input = ctx.arg(0);
      final Column result = validationFn.validate(sourceType, input);
      // Propagate null at runtime: if the input value is null (empty collection),
      // the result must be empty per FHIRPath spec §5.7, not true/false.
      return when(input.isNotNull(), result);
    };
  }

  /**
   * toQuantity with optional unit argument. First converts to quantity, then if a unit argument is
   * provided, converts the quantity to the target unit via {@link QuantityConvertToUnit}.
   *
   * <p>The unit arg column is null when absent (variadic padding). When present but conversion
   * fails (incompatible units), the UDF returns null → empty per spec.
   */
  @Nonnull
  private static SparkOperationDef toQuantityWithUnit() {
    return ctx -> {
      final SystemType sourceType = ctx.systemArgType(0);
      if (sourceType == SystemType.NULL) {
        return lit(null);
      }
      final Column input = ctx.arg(0);
      final Column unitArg = ctx.arg(1);

      final Column quantity = asQuantity(sourceType, input);

      // When unit arg is null (absent), return quantity unchanged;
      // when present, apply unit conversion (UDF returns null on failure → empty per spec)
      return when(unitArg.isNotNull(), QuantityConvertToUnit.UDF.apply(quantity, unitArg))
          .otherwise(quantity);
    };
  }

  /**
   * convertsToQuantity with optional unit argument. First validates conversion to quantity, then if
   * a unit argument is provided, also checks if the quantity is convertible to the target unit.
   *
   * <p>The unit arg column is null when absent (variadic padding).
   */
  @Nonnull
  private static SparkOperationDef convertsToQuantityWithUnit() {
    return ctx -> {
      final SystemType sourceType = ctx.systemArgType(0);
      if (sourceType == SystemType.NULL) {
        return lit(null);
      }
      final Column input = ctx.arg(0);
      final Column unitArg = ctx.arg(1);
      final Column canConvert = validateToQuantity(sourceType, input);

      // When unit arg is null (absent), just return base validation result;
      // when present, also check that unit conversion succeeds
      final Column converted =
          QuantityConvertToUnit.UDF.apply(asQuantity(sourceType, input), unitArg);
      final Column result =
          when(unitArg.isNotNull(), when(canConvert, converted.isNotNull()).otherwise(lit(false)))
              .otherwise(canConvert);
      // Propagate null at runtime: empty input → empty result per FHIRPath spec §5.7
      return when(input.isNotNull(), result);
    };
  }

  /** Converts input to quantity, returning identity if already QUANTITY. */
  @Nonnull
  private static Column asQuantity(
      @Nonnull final SystemType sourceType, @Nonnull final Column input) {
    return (sourceType == SystemType.QUANTITY) ? input : convertToQuantity(sourceType, input);
  }

  @FunctionalInterface
  private interface ConversionFunction {
    Column convert(SystemType sourceType, Column value);
  }

  @FunctionalInterface
  private interface ValidationFunction {
    Column validate(SystemType sourceType, Column value);
  }

  // ========== Conversion Functions ==========

  /**
   * Converts to Boolean per FHIRPath spec (case-insensitive for strings). String:
   * 'true'/'t'/'yes'/'y'/'1'/'1.0' → true; 'false'/'f'/'no'/'n'/'0'/'0.0' → false. Integer: 1→true,
   * 0→false. Decimal: 1.0→true, 0.0→false.
   */
  @Nonnull
  private static Column convertToBoolean(
      @Nonnull final SystemType sourceType, @Nonnull final Column value) {
    return switch (sourceType) {
      case STRING -> {
        final Column lower = functions.lower(value);
        yield when(lower.isin("true", "t", "yes", "y", "1", "1.0"), lit(true))
            .when(lower.isin("false", "f", "no", "n", "0", "0.0"), lit(false));
      }
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
      @Nonnull final SystemType sourceType, @Nonnull final Column value) {
    return switch (sourceType) {
      case BOOLEAN -> value.try_cast(DataTypes.IntegerType);
      case STRING -> when(value.rlike(INTEGER_REGEX), value.try_cast(DataTypes.IntegerType));
      default -> lit(null);
    };
  }

  /** Converts to Decimal. Boolean/Integer/String: cast to decimal. */
  @Nonnull
  private static Column convertToDecimal(
      @Nonnull final SystemType sourceType, @Nonnull final Column value) {
    return switch (sourceType) {
      case BOOLEAN, INTEGER -> value.try_cast(SparkTypeMapper.DECIMAL_TYPE);
      case STRING -> when(value.rlike(DECIMAL_REGEX), value.try_cast(SparkTypeMapper.DECIMAL_TYPE));
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
      @Nonnull final SystemType sourceType, @Nonnull final Column value) {
    return switch (sourceType) {
      case BOOLEAN, INTEGER, DATE, DATE_TIME, TIME -> value.try_cast(DataTypes.StringType);
      case DECIMAL -> decimalToString(value);
      case QUANTITY -> quantityToString(value);
      case CODING -> codingToString(value);
      default -> lit(null);
    };
  }

  /** Converts to Date. Only String with valid date format. */
  @Nonnull
  private static Column convertToDate(
      @Nonnull final SystemType sourceType, @Nonnull final Column value) {
    return switch (sourceType) {
      case STRING -> when(value.rlike(DATE_REGEX), value);
      case DATE_TIME -> extractDateFromDateTime(value);
      default -> lit(null);
    };
  }

  /** Converts to DateTime. String with valid dateTime format. Date is implicitly converted. */
  @Nonnull
  private static Column convertToDateTime(
      @Nonnull final SystemType sourceType, @Nonnull final Column value) {
    return switch (sourceType) {
      case STRING -> when(value.rlike(DATETIME_REGEX), value);
      case DATE -> value; // Date→DateTime: date string is valid dateTime (partial precision)
      default -> lit(null);
    };
  }

  /** Converts to Time. Only String with valid time format. */
  @Nonnull
  private static Column convertToTime(
      @Nonnull final SystemType sourceType, @Nonnull final Column value) {
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
      @Nonnull final SystemType sourceType, @Nonnull final Column value) {
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
      @Nonnull final SystemType sourceType, @Nonnull final Column value) {
    return switch (sourceType) {
      case BOOLEAN -> lit(true);
      case STRING -> {
        final Column lower = functions.lower(value);
        yield lower.isin("true", "t", "yes", "y", "1", "1.0", "false", "f", "no", "n", "0", "0.0");
      }
      case INTEGER -> value.equalTo(lit(0)).or(value.equalTo(lit(1)));
      case DECIMAL -> value.equalTo(lit(0.0)).or(value.equalTo(lit(1.0)));
      default -> lit(false);
    };
  }

  /** Validates conversion to Integer. */
  @Nonnull
  private static Column validateToInteger(
      @Nonnull final SystemType sourceType, @Nonnull final Column value) {
    return switch (sourceType) {
      case INTEGER, BOOLEAN -> lit(true);
      case STRING -> value.rlike(INTEGER_REGEX);
      default -> lit(false);
    };
  }

  /** Validates conversion to Decimal. */
  @Nonnull
  private static Column validateToDecimal(
      @Nonnull final SystemType sourceType, @Nonnull final Column value) {
    return switch (sourceType) {
      case DECIMAL, BOOLEAN, INTEGER -> lit(true);
      case STRING -> value.rlike(DECIMAL_REGEX);
      default -> lit(false);
    };
  }

  /** Validates conversion to String. All primitive types can convert to String. */
  @Nonnull
  private static Column validateToString(
      @Nonnull final SystemType sourceType, @Nonnull final Column value) {
    return switch (sourceType) {
      case STRING, BOOLEAN, INTEGER, DECIMAL, DATE, DATE_TIME, TIME, QUANTITY, CODING -> lit(true);
      default -> lit(false);
    };
  }

  /** Validates conversion to Date. */
  @Nonnull
  private static Column validateToDate(
      @Nonnull final SystemType sourceType, @Nonnull final Column value) {
    return switch (sourceType) {
      case DATE, DATE_TIME -> lit(true);
      case STRING -> value.rlike(DATE_REGEX);
      default -> lit(false);
    };
  }

  /** Validates conversion to DateTime. */
  @Nonnull
  private static Column validateToDateTime(
      @Nonnull final SystemType sourceType, @Nonnull final Column value) {
    return switch (sourceType) {
      case DATE_TIME, DATE -> lit(true);
      case STRING -> value.rlike(DATETIME_REGEX);
      default -> lit(false);
    };
  }

  /** Validates conversion to Time. */
  @Nonnull
  private static Column validateToTime(
      @Nonnull final SystemType sourceType, @Nonnull final Column value) {
    return switch (sourceType) {
      case TIME -> lit(true);
      case STRING -> value.rlike(TIME_REGEX);
      default -> lit(false);
    };
  }

  /** Validates conversion to Quantity. */
  @Nonnull
  private static Column validateToQuantity(
      @Nonnull final SystemType sourceType, @Nonnull final Column value) {
    return switch (sourceType) {
      case QUANTITY, BOOLEAN, INTEGER, DECIMAL -> lit(true);
      case STRING -> value.rlike(QUANTITY_REGEX);
      default -> lit(false);
    };
  }

  // ========== Helper Methods ==========

  /**
   * Converts a decimal value to string, stripping trailing zeros and any resulting trailing dot.
   * For example, {@code 1.500000} becomes {@code "1.5"} and {@code 1.000000} becomes {@code "1"}.
   */
  @Nonnull
  private static Column decimalToString(@Nonnull final Column value) {
    // Spark's cast to string for Decimal includes trailing zeros (e.g., "1.000000").
    // Single regex strips trailing zeros and any resulting trailing dot.
    final Column str = value.cast(DataTypes.StringType);
    return when(str.contains(lit(".")), functions.regexp_replace(str, "\\.?0+$", ""))
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

  /**
   * Converts a Coding struct to its FHIRPath literal string representation. Format: {@code
   * system|code[|version[|display[|userSelected]]]}, omitting trailing null components.
   */
  @Nonnull
  private static Column codingToString(@Nonnull final Column value) {
    final Column system = functions.coalesce(value.getField("system"), lit(""));
    final Column code = functions.coalesce(value.getField("code"), lit(""));
    final Column version = value.getField("version");
    final Column display = value.getField("display");
    final Column userSelected = value.getField("userSelected");

    // Build incrementally, inserting empty segments for null intermediate fields.
    // Format: system|code[|version[|display[|userSelected]]]
    final Column base = functions.concat(system, lit("|"), code);
    final Column versionOrEmpty = functions.coalesce(version, lit(""));
    final Column displayOrEmpty = functions.coalesce(display, lit(""));

    final Column withVersion = functions.concat(base, lit("|"), version);
    final Column withDisplay = functions.concat(base, lit("|"), versionOrEmpty, lit("|"), display);
    final Column withUserSelected =
        functions.concat(
            base,
            lit("|"),
            versionOrEmpty,
            lit("|"),
            displayOrEmpty,
            lit("|"),
            userSelected.cast(DataTypes.StringType));

    return when(userSelected.isNotNull(), withUserSelected)
        .when(display.isNotNull(), withDisplay)
        .when(version.isNotNull(), withVersion)
        .otherwise(base);
  }

  /** Builds a Quantity struct column with the given field expressions. */
  @Nonnull
  private static Column quantityStruct(
      @Nonnull final Column value,
      @Nonnull final Column unit,
      @Nonnull final Column system,
      @Nonnull final Column code) {
    return functions.struct(
        value.as("value"), unit.as("unit"), system.as("system"), code.as("code"));
  }

  /** Wraps a numeric value as a Quantity struct with default unit '1'. Returns null for null. */
  @Nonnull
  private static Column numericToQuantity(@Nonnull final Column decimalValue) {
    return when(
        decimalValue.isNotNull(),
        quantityStruct(
            decimalValue,
            lit(QuantityValue.DEFAULT_UNIT),
            lit(QuantityValue.UCUM_SYSTEM),
            lit(QuantityValue.DEFAULT_UNIT)));
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

    // Normalize calendar unit to singular form (e.g. "years" → "year")
    final Column normalizedCalendarUnit = functions.regexp_replace(calendarUnit, "s$", "");

    // Determine the effective unit code
    final Column hasQuotedUnit = quotedUnit.notEqual(lit(""));
    final Column hasCalendarUnit = calendarUnit.notEqual(lit(""));
    final Column unitCode =
        when(hasQuotedUnit, quotedUnit)
            .when(hasCalendarUnit, normalizedCalendarUnit)
            .otherwise(lit(QuantityValue.DEFAULT_UNIT));

    // Determine the system URI
    final Column system =
        when(hasCalendarUnit, lit(QuantityValue.CALENDAR_SYSTEM))
            .otherwise(lit(QuantityValue.UCUM_SYSTEM));

    return when(matches, quantityStruct(decimalValue, unitCode, system, unitCode));
  }

  /**
   * Extracts the date portion from a DateTime string. DateTime strings start with the date part
   * (YYYY, YYYY-MM, or YYYY-MM-DD) followed optionally by 'T' and time components.
   */
  @Nonnull
  private static Column extractDateFromDateTime(@Nonnull final Column value) {
    // Extract everything before 'T' (the date part)
    // If no 'T', substring_index returns the full string unchanged
    return functions.substring_index(value, "T", 1);
  }
}
