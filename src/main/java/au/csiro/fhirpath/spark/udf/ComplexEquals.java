package au.csiro.fhirpath.spark.udf;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.api.java.UDF2;
import org.apache.spark.sql.expressions.UserDefinedFunction;
import org.apache.spark.sql.functions;
import org.apache.spark.sql.types.ArrayType;
import org.apache.spark.sql.types.DataType;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.MapType;
import org.apache.spark.sql.types.StructField;
import org.apache.spark.sql.types.StructType;

/**
 * Spark UDF for structural equality of two composite (struct) values while ignoring synthetic
 * fields injected by Pathling's flat-schema encoder.
 *
 * <p>Any field whose name starts with {@code _} (e.g. {@code _fid}, {@code _extension}) is skipped
 * at every depth of recursion. Remaining fields are compared structurally: primitives via {@link
 * Objects#equals} (with {@link BigDecimal#compareTo} for scale-insensitive decimal equality),
 * nested structs by recursing, arrays element-wise, and maps key/value-wise.
 *
 * <p>Null propagation follows FHIRPath semantics at the top level: if either input row is {@code
 * null} the result is {@code null}. Within the recursion, two nulls in matching positions are
 * considered equal; one null against a non-null is unequal.
 *
 * <p>Used by {@link au.csiro.fhirpath.spark.ops.EqualityOps} as the comparator for FHIR complex
 * types (HumanName, Address, Period, Identifier, etc.). Pure structural — does not defer to {@code
 * QuantitySupport}, {@code CodingSupport} or {@code TemporalSupport} for sub-fields.
 */
public final class ComplexEquals {

  /**
   * Prefix marking Pathling-encoder synthetic fields (currently {@code _fid}, {@code _extension},
   * {@code _value_canonicalized}). These are not user data and must not participate in structural
   * equality.
   */
  private static final String SYNTHETIC_FIELD_PREFIX = "_";

  private ComplexEquals() {}

  /** Spark UDF: {@code (Row, Row) → Boolean}. */
  @Nonnull
  public static final UserDefinedFunction UDF =
      functions.udf((UDF2<Row, Row, Boolean>) ComplexEquals::equals, DataTypes.BooleanType);

  @Nullable
  static Boolean equals(@Nullable final Row left, @Nullable final Row right) {
    if (left == null || right == null) {
      return null;
    }
    return structEquals(left, right);
  }

  private static boolean structEquals(@Nonnull final Row left, @Nonnull final Row right) {
    final StructType schema = left.schema();
    final StructField[] fields = schema.fields();
    for (int i = 0; i < fields.length; i++) {
      final StructField field = fields[i];
      if (field.name().startsWith(SYNTHETIC_FIELD_PREFIX)) {
        continue;
      }
      final boolean leftNull = left.isNullAt(i);
      final boolean rightNull = right.isNullAt(i);
      if (leftNull && rightNull) {
        continue;
      }
      if (leftNull || rightNull) {
        return false;
      }
      if (!valueEquals(left.get(i), right.get(i), field.dataType())) {
        return false;
      }
    }
    return true;
  }

  private static boolean valueEquals(
      @Nonnull final Object left, @Nonnull final Object right, @Nonnull final DataType type) {
    if (type instanceof StructType) {
      return structEquals((Row) left, (Row) right);
    }
    if (type instanceof ArrayType arrayType) {
      return arrayEquals(asList(left), asList(right), arrayType.elementType());
    }
    if (type instanceof MapType mapType) {
      return mapEquals(asMap(left), asMap(right), mapType.valueType());
    }
    if (left instanceof BigDecimal leftDec && right instanceof BigDecimal rightDec) {
      return leftDec.compareTo(rightDec) == 0;
    }
    return Objects.equals(left, right);
  }

  private static boolean arrayEquals(
      @Nonnull final List<Object> left,
      @Nonnull final List<Object> right,
      @Nonnull final DataType elementType) {
    if (left.size() != right.size()) {
      return false;
    }
    for (int i = 0; i < left.size(); i++) {
      if (!nullableValueEquals(left.get(i), right.get(i), elementType)) {
        return false;
      }
    }
    return true;
  }

  // Keys are compared via Map.containsKey / Object.equals (primitive keys only); no keyType
  // parameter. Pathling's schema uses MapType only for the synthetic _extension map, which is
  // filtered by the `_`-prefix rule and never reaches this method in practice.
  private static boolean mapEquals(
      @Nonnull final Map<Object, Object> left,
      @Nonnull final Map<Object, Object> right,
      @Nonnull final DataType valueType) {
    if (left.size() != right.size()) {
      return false;
    }
    for (final Map.Entry<Object, Object> entry : left.entrySet()) {
      if (!right.containsKey(entry.getKey())) {
        return false;
      }
      if (!nullableValueEquals(entry.getValue(), right.get(entry.getKey()), valueType)) {
        return false;
      }
    }
    return true;
  }

  private static boolean nullableValueEquals(
      @Nullable final Object left, @Nullable final Object right, @Nonnull final DataType type) {
    if (left == null && right == null) {
      return true;
    }
    if (left == null || right == null) {
      return false;
    }
    return valueEquals(left, right, type);
  }

  /**
   * Normalises an array field value to {@link List}. Spark delivers ArrayType columns as {@link
   * scala.collection.Seq} at runtime; unit tests may supply a {@link List} or {@code Object[]}.
   */
  @SuppressWarnings("unchecked")
  @Nonnull
  private static List<Object> asList(@Nonnull final Object value) {
    if (value instanceof List<?> list) {
      return (List<Object>) list;
    }
    if (value instanceof scala.collection.Seq<?> seq) {
      return scala.jdk.javaapi.CollectionConverters.asJava((scala.collection.Seq<Object>) seq);
    }
    if (value instanceof Object[] array) {
      return Arrays.asList(array);
    }
    throw new IllegalStateException(
        "Unsupported array representation: " + value.getClass().getName());
  }

  /**
   * Normalises a map field value to {@link Map}. Spark delivers MapType columns as {@link
   * scala.collection.Map} at runtime; unit tests may supply a {@link Map}.
   */
  @SuppressWarnings("unchecked")
  @Nonnull
  private static Map<Object, Object> asMap(@Nonnull final Object value) {
    if (value instanceof Map<?, ?> map) {
      return (Map<Object, Object>) map;
    }
    if (value instanceof scala.collection.Map<?, ?> smap) {
      return scala.jdk.javaapi.CollectionConverters.asJava(
          (scala.collection.Map<Object, Object>) smap);
    }
    throw new IllegalStateException(
        "Unsupported map representation: " + value.getClass().getName());
  }
}
