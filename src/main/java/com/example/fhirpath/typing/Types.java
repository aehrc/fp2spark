package com.example.fhirpath.typing;

/**
 * Common Type constants for backward compatibility and convenience.
 *
 * <p>Phase 1: Only System types (INTEGER, DECIMAL, BOOLEAN, STRING) are supported. FHIR types
 * (Date, DateTime, Time, Quantity) are deferred to Phase 2.
 */
public final class Types {
  public static final Type INTEGER = PrimitiveType.INTEGER;
  public static final Type DECIMAL = PrimitiveType.DECIMAL;
  public static final Type BOOLEAN = PrimitiveType.BOOLEAN;
  public static final Type STRING = PrimitiveType.STRING;
  public static final Type NULL = PrimitiveType.NULL;
  public static final Type ANY = PrimitiveType.ANY;

  private Types() {
    // Utility class
  }
}
