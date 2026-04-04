package com.example.fhirpath;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.fhirpath.codegen.spark.SparkTypeMapper;
import com.example.fhirpath.typing.SystemType;
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
    assertEquals("Date", SystemType.DATE.getName());
    assertEquals("DateTime", SystemType.DATE_TIME.getName());
    assertEquals("Time", SystemType.TIME.getName());
  }

  @Test
  void typeProperties() {
    assertTrue(SystemType.DATE.isPrimitive());
    assertTrue(SystemType.DATE_TIME.isPrimitive());
    assertTrue(SystemType.TIME.isPrimitive());
    assertFalse(SystemType.DATE.isComplex());
    assertFalse(SystemType.DATE_TIME.isComplex());
    assertFalse(SystemType.TIME.isComplex());
  }

  @Test
  void typeConstants() {
    assertEquals(SystemType.DATE, Types.DATE);
    assertEquals(SystemType.DATE_TIME, Types.DATE_TIME);
    assertEquals(SystemType.TIME, Types.TIME);
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

  @Test
  void temporalTypesInComparable() {
    assertTrue(TypeSets.COMPARABLE.contains(Types.DATE));
    assertTrue(TypeSets.COMPARABLE.contains(Types.DATE_TIME));
    assertTrue(TypeSets.COMPARABLE.contains(Types.TIME));
  }

  @Test
  void temporalTypesInEquatable() {
    assertTrue(TypeSets.EQUATABLE.contains(Types.DATE));
    assertTrue(TypeSets.EQUATABLE.contains(Types.DATE_TIME));
    assertTrue(TypeSets.EQUATABLE.contains(Types.TIME));
  }
}
