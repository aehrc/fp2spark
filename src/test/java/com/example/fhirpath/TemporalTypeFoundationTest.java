package com.example.fhirpath;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.fhirpath.codegen.spark.SparkTypeMapper;
import com.example.fhirpath.typing.PrimitiveType;
import com.example.fhirpath.typing.Shape;
import com.example.fhirpath.typing.TypeSets;
import com.example.fhirpath.typing.TypeSystem;
import com.example.fhirpath.typing.Types;
import org.apache.spark.sql.types.DataTypes;
import org.junit.jupiter.api.Test;

/**
 * Tests for temporal type foundation (Date, DateTime, Time).
 *
 * <p>Verifies type properties, cast rules, Spark mappings, and type set membership. No temporal
 * operations are tested here — this covers only the type system infrastructure.
 */
public class TemporalTypeFoundationTest {

  @Test
  void typeNames() {
    assertEquals("date", PrimitiveType.DATE.getName());
    assertEquals("dateTime", PrimitiveType.DATE_TIME.getName());
    assertEquals("time", PrimitiveType.TIME.getName());
  }

  @Test
  void typeProperties() {
    assertTrue(PrimitiveType.DATE.isPrimitive());
    assertTrue(PrimitiveType.DATE_TIME.isPrimitive());
    assertTrue(PrimitiveType.TIME.isPrimitive());
    assertFalse(PrimitiveType.DATE.isComplex());
    assertFalse(PrimitiveType.DATE_TIME.isComplex());
    assertFalse(PrimitiveType.TIME.isComplex());
  }

  @Test
  void typeConstants() {
    assertEquals(PrimitiveType.DATE, Types.DATE);
    assertEquals(PrimitiveType.DATE_TIME, Types.DATE_TIME);
    assertEquals(PrimitiveType.TIME, Types.TIME);
  }

  @Test
  void dateToDateTimeCast() {
    assertTrue(TypeSystem.canCast(Types.DATE, Types.DATE_TIME));
  }

  @Test
  void dateTimeToDatCastNotAllowed() {
    assertFalse(TypeSystem.canCast(Types.DATE_TIME, Types.DATE));
  }

  @Test
  void temporalSelfCast() {
    assertTrue(TypeSystem.canCast(Types.DATE, Types.DATE));
    assertTrue(TypeSystem.canCast(Types.DATE_TIME, Types.DATE_TIME));
    assertTrue(TypeSystem.canCast(Types.TIME, Types.TIME));
  }

  @Test
  void temporalToUnrelatedCastNotAllowed() {
    assertFalse(TypeSystem.canCast(Types.DATE, Types.STRING));
    assertFalse(TypeSystem.canCast(Types.DATE_TIME, Types.INTEGER));
    assertFalse(TypeSystem.canCast(Types.TIME, Types.BOOLEAN));
    assertFalse(TypeSystem.canCast(Types.TIME, Types.DATE));
    assertFalse(TypeSystem.canCast(Types.TIME, Types.DATE_TIME));
  }

  @Test
  void nullCastsToTemporalTypes() {
    assertTrue(TypeSystem.canCast(Types.NULL, Types.DATE));
    assertTrue(TypeSystem.canCast(Types.NULL, Types.DATE_TIME));
    assertTrue(TypeSystem.canCast(Types.NULL, Types.TIME));
  }

  @Test
  void sparkMappingIsStringType() {
    assertEquals(DataTypes.StringType, SparkTypeMapper.toSparkDataType(Shape.single(Types.DATE)));
    assertEquals(
        DataTypes.StringType, SparkTypeMapper.toSparkDataType(Shape.single(Types.DATE_TIME)));
    assertEquals(DataTypes.StringType, SparkTypeMapper.toSparkDataType(Shape.single(Types.TIME)));
  }

  // Temporal types will be added to COMPARABLE and EQUATABLE in Step 3
  // when the code generator supports temporal comparison/equality.
  @Test
  void temporalTypesNotYetInComparable() {
    assertFalse(TypeSets.COMPARABLE.contains(Types.DATE));
    assertFalse(TypeSets.COMPARABLE.contains(Types.DATE_TIME));
    assertFalse(TypeSets.COMPARABLE.contains(Types.TIME));
  }

  @Test
  void temporalTypesNotYetInEquatable() {
    assertFalse(TypeSets.EQUATABLE.contains(Types.DATE));
    assertFalse(TypeSets.EQUATABLE.contains(Types.DATE_TIME));
    assertFalse(TypeSets.EQUATABLE.contains(Types.TIME));
  }
}
