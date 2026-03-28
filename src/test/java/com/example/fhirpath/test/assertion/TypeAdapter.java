package com.example.fhirpath.test.assertion;

import com.example.fhirpath.typing.CodingValue;
import com.example.fhirpath.typing.QuantityValue;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.spark.sql.Row;
import scala.collection.JavaConverters;

/**
 * Adapts expected test values to match actual value types returned by Spark.
 *
 * <p>This class handles type mismatches between convenient test value types (int, double,
 * CodingValue, QuantityValue) and actual Spark return types (BigDecimal, Row).
 *
 * <p><b>Adaptation Rules:</b>
 *
 * <ul>
 *   <li>If actual is BigDecimal, adapt expected int/long/double/float to BigDecimal
 *   <li>CodingValue → Map with {system, code, version, display, userSelected}
 *   <li>QuantityValue → Map with {value, unit, system, code}
 *   <li>Spark Rows → Map (via {@link #convertScalaToJava})
 *   <li>For lists, recursively adapt nested elements
 * </ul>
 */
class TypeAdapter {

  /**
   * Adapt expected value to match the type of actual value.
   *
   * <p>This method recursively adapts collections and their elements.
   *
   * @param expected The expected value from the test
   * @param actual The actual value returned by Spark
   * @return The adapted expected value, or original if no adaptation needed
   */
  @Nullable
  Object adaptToActualType(@Nullable Object expected, @Nullable Object actual) {
    if (expected == null || actual == null) {
      return expected;
    }

    // Convert Scala collections and Rows to Java equivalents for comparison
    Object convertedActual = convertScalaToJava(actual);

    // Handle lists: adapt each element to match actual list's element type
    if (expected instanceof List<?> expectedList && convertedActual instanceof List<?> actualList) {
      // Recursively adapt nested elements
      return expectedList.stream()
          .map(
              e -> {
                // Find corresponding actual element to determine target type
                int index = expectedList.indexOf(e);
                if (index >= 0 && index < actualList.size()) {
                  return adaptToActualType(e, actualList.get(index));
                }
                return e;
              })
          .toList();
    }

    // Adapt CodingValue to Map when actual is a Row (converted to Map)
    if (expected instanceof CodingValue cv) {
      return codingValueToMap(cv);
    }

    // Adapt QuantityValue to Map when actual is a Row (converted to Map)
    if (expected instanceof QuantityValue qv) {
      return quantityValueToMap(qv);
    }

    // Handle scalar values: adapt to actual type
    return adaptValue(expected, convertedActual.getClass());
  }

  /**
   * Convert Scala collections and Spark Rows to Java equivalents recursively.
   *
   * @param value The value to convert
   * @return Java collection/Map if value is Scala collection/Row, otherwise original value
   */
  @Nullable
  Object convertScalaToJava(@Nullable Object value) {
    if (value == null) {
      return null;
    }

    // Convert Scala Seq to Java List
    if (value instanceof scala.collection.Seq<?> scalaSeq) {
      List<?> javaList = JavaConverters.seqAsJavaList(scalaSeq);
      // Recursively convert nested collections
      return javaList.stream().map(this::convertScalaToJava).toList();
    }

    // Convert Spark Row to Map for comparison with CodingValue/QuantityValue Maps
    if (value instanceof Row row) {
      return rowToMap(row);
    }

    return value;
  }

  /** Converts a CodingValue to a Map matching the Spark Coding struct layout. */
  @Nonnull
  private static Map<String, Object> codingValueToMap(@Nonnull final CodingValue cv) {
    final Map<String, Object> map = new HashMap<>();
    map.put("system", cv.system());
    map.put("code", cv.code());
    map.put("version", cv.version());
    map.put("display", cv.display());
    map.put("userSelected", cv.userSelected());
    return map;
  }

  /** Converts a QuantityValue to a Map matching the Spark Quantity struct layout. */
  @Nonnull
  private static Map<String, Object> quantityValueToMap(@Nonnull final QuantityValue qv) {
    final Map<String, Object> map = new HashMap<>();
    map.put("value", qv.value().stripTrailingZeros());
    map.put("unit", qv.unit());
    map.put("system", qv.system());
    map.put("code", qv.code());
    return map;
  }

  /** Converts a Spark Row to a Map for comparison, normalizing BigDecimal values. */
  @Nonnull
  private static Map<String, Object> rowToMap(@Nonnull final Row row) {
    final Map<String, Object> map = new HashMap<>();
    for (final String field : row.schema().fieldNames()) {
      Object value = row.getAs(field);
      // Normalize BigDecimal values (e.g., Spark DECIMAL(38,6) → stripped trailing zeros)
      if (value instanceof BigDecimal bd) {
        value = bd.stripTrailingZeros();
      }
      map.put(field, value);
    }
    return map;
  }

  /**
   * Adapt a single value to the target type.
   *
   * @param value The value to adapt
   * @param targetType The target type to adapt to
   * @return The adapted value, or original if no adaptation possible
   */
  @Nullable
  private Object adaptValue(@Nullable Object value, @Nonnull Class<?> targetType) {
    if (value == null) {
      return null;
    }

    // If already correct type, return as-is
    if (targetType.isInstance(value)) {
      // For BigDecimal, normalize by stripping trailing zeros
      if (value instanceof BigDecimal bd) {
        return bd.stripTrailingZeros();
      }
      return value;
    }

    // Adapt numeric types to BigDecimal (FHIRPath DECIMAL type)
    if (targetType == BigDecimal.class) {
      return adaptToBigDecimal(value);
    }

    // No adaptation possible or needed
    return value;
  }

  /**
   * Adapt a numeric value to BigDecimal.
   *
   * @param value The numeric value to adapt
   * @return BigDecimal representation, or original value if not numeric
   */
  @Nullable
  private Object adaptToBigDecimal(@Nonnull Object value) {
    if (value instanceof Integer i) {
      return BigDecimal.valueOf(i);
    }
    if (value instanceof Long l) {
      return BigDecimal.valueOf(l);
    }
    if (value instanceof Double d) {
      return BigDecimal.valueOf(d).stripTrailingZeros();
    }
    if (value instanceof Float f) {
      return BigDecimal.valueOf(f).stripTrailingZeros();
    }
    // Not a numeric type we can adapt
    return value;
  }
}
