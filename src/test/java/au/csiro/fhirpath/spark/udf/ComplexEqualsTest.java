package au.csiro.fhirpath.spark.udf;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.catalyst.expressions.GenericRowWithSchema;
import org.apache.spark.sql.types.DataType;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.StructField;
import org.apache.spark.sql.types.StructType;
import org.junit.jupiter.api.Test;

/**
 * Pure-Java tests for {@link ComplexEquals#equals(Row, Row)}.
 *
 * <p>Exercises the structural-equality semantics in isolation (no Spark session): synthetic {@code
 * _}-prefixed fields are skipped at every depth, primitives compared with null propagation, arrays
 * element-wise, and BigDecimals compared scale-insensitively via {@link BigDecimal#compareTo}.
 */
class ComplexEqualsTest {

  private static final StructType NAME_SCHEMA =
      DataTypes.createStructType(
          new StructField[] {
            DataTypes.createStructField("family", DataTypes.StringType, true),
            DataTypes.createStructField(
                "given", DataTypes.createArrayType(DataTypes.StringType), true),
            DataTypes.createStructField("_fid", DataTypes.IntegerType, true),
          });

  private static final StructType PERIOD_SCHEMA =
      DataTypes.createStructType(
          new StructField[] {
            DataTypes.createStructField("start", DataTypes.StringType, true),
            DataTypes.createStructField("end", DataTypes.StringType, true),
            DataTypes.createStructField("_fid", DataTypes.IntegerType, true),
          });

  private static final StructType NESTED_SCHEMA =
      DataTypes.createStructType(
          new StructField[] {
            DataTypes.createStructField("family", DataTypes.StringType, true),
            DataTypes.createStructField("period", PERIOD_SCHEMA, true),
            DataTypes.createStructField("_fid", DataTypes.IntegerType, true),
          });

  private static Row row(final StructType schema, final Object... values) {
    return new GenericRowWithSchema(values, schema);
  }

  private static Row name(final String family, final List<String> given, final Integer fid) {
    return row(NAME_SCHEMA, family, given, fid);
  }

  @Test
  void equalWhenOnlyFidDiffers() {
    final Row left = name("Smith", List.of("John"), 1);
    final Row right = name("Smith", List.of("John"), 2);

    assertTrue(ComplexEquals.equals(left, right));
  }

  @Test
  void notEqualWhenPrimitiveFieldDiffers() {
    final Row left = name("Smith", List.of("John"), 1);
    final Row right = name("Jones", List.of("John"), 1);

    assertFalse(ComplexEquals.equals(left, right));
  }

  @Test
  void notEqualWhenArrayElementDiffers() {
    final Row left = name("Smith", List.of("John"), 1);
    final Row right = name("Smith", List.of("Jane"), 1);

    assertFalse(ComplexEquals.equals(left, right));
  }

  @Test
  void notEqualWhenArrayLengthDiffers() {
    final Row left = name("Smith", List.of("John", "David"), 1);
    final Row right = name("Smith", List.of("John"), 1);

    assertFalse(ComplexEquals.equals(left, right));
  }

  @Test
  void equalWhenNestedStructOnlyDiffersInFid() {
    final Row leftPeriod = row(PERIOD_SCHEMA, "2020-01-01", "2020-12-31", 10);
    final Row rightPeriod = row(PERIOD_SCHEMA, "2020-01-01", "2020-12-31", 20);

    final Row left = row(NESTED_SCHEMA, "Smith", leftPeriod, 1);
    final Row right = row(NESTED_SCHEMA, "Smith", rightPeriod, 2);

    assertTrue(ComplexEquals.equals(left, right));
  }

  @Test
  void notEqualWhenNestedStructContentDiffers() {
    final Row leftPeriod = row(PERIOD_SCHEMA, "2020-01-01", "2020-12-31", 10);
    final Row rightPeriod = row(PERIOD_SCHEMA, "2020-01-01", "2021-12-31", 20);

    final Row left = row(NESTED_SCHEMA, "Smith", leftPeriod, 1);
    final Row right = row(NESTED_SCHEMA, "Smith", rightPeriod, 2);

    assertFalse(ComplexEquals.equals(left, right));
  }

  @Test
  void equalWhenMatchingPrimitiveNulls() {
    final Row left = name(null, null, 1);
    final Row right = name(null, null, 2);

    assertTrue(ComplexEquals.equals(left, right));
  }

  @Test
  void notEqualWhenOneSideHasNullPrimitive() {
    final Row left = name("Smith", List.of("John"), 1);
    final Row right = name(null, List.of("John"), 1);

    assertFalse(ComplexEquals.equals(left, right));
  }

  @Test
  void nullTopLevelRowsPropagateToNull() {
    final Row nonNull = name("Smith", List.of("John"), 1);

    assertNull(ComplexEquals.equals(null, nonNull));
    assertNull(ComplexEquals.equals(nonNull, null));
    assertNull(ComplexEquals.equals(null, null));
  }

  @Test
  void bigDecimalCompareIsScaleInsensitive() {
    final StructType schema =
        DataTypes.createStructType(
            new StructField[] {
              DataTypes.createStructField("amount", DataTypes.createDecimalType(38, 6), true),
              DataTypes.createStructField("_fid", DataTypes.IntegerType, true),
            });

    final Row left = row(schema, new BigDecimal("1.50"), 1);
    final Row right = row(schema, new BigDecimal("1.500000"), 2);

    assertTrue(ComplexEquals.equals(left, right));
  }

  @Test
  void allUnderscorePrefixedFieldsSkipped() {
    final StructType schema =
        DataTypes.createStructType(
            new StructField[] {
              DataTypes.createStructField("family", DataTypes.StringType, true),
              DataTypes.createStructField("_fid", DataTypes.IntegerType, true),
              DataTypes.createStructField("_value_canonicalized", DataTypes.StringType, true),
              DataTypes.createStructField("_extension_placeholder", DataTypes.StringType, true),
            });

    final Row left = row(schema, "Smith", 1, "foo", "ext1");
    final Row right = row(schema, "Smith", 2, "bar", "ext2");

    assertTrue(ComplexEquals.equals(left, right));
  }

  @Test
  void nullArraysOnBothSidesAreEqual() {
    final Row left = name("Smith", null, 1);
    final Row right = name("Smith", null, 2);

    assertTrue(ComplexEquals.equals(left, right));
  }

  @Test
  void nullArrayVsNonNullArrayIsNotEqual() {
    final Row left = name("Smith", null, 1);
    final Row right = name("Smith", List.of(), 1);

    assertFalse(ComplexEquals.equals(left, right));
  }

  @Test
  void arrayOfStructsComparedElementWiseIgnoringFid() {
    final DataType arrayOfNameType = DataTypes.createArrayType(NAME_SCHEMA);
    final StructType arrayOfNameSchema =
        DataTypes.createStructType(
            new StructField[] {
              DataTypes.createStructField("names", arrayOfNameType, true),
              DataTypes.createStructField("_fid", DataTypes.IntegerType, true),
            });

    final List<Row> leftNames =
        List.of(name("Smith", List.of("John"), 10), name("Jones", List.of("Mary"), 11));
    final List<Row> rightNames =
        List.of(name("Smith", List.of("John"), 100), name("Jones", List.of("Mary"), 101));

    final Row left = row(arrayOfNameSchema, leftNames, 1);
    final Row right = row(arrayOfNameSchema, rightNames, 2);

    assertTrue(ComplexEquals.equals(left, right));
  }
}
