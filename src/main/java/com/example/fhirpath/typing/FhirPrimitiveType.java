package com.example.fhirpath.typing;

import jakarta.annotation.Nonnull;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents a FHIR primitive type that wraps a FHIRPath System type.
 *
 * <p>FHIR defines its own set of primitive types (e.g., {@code FHIR.date}, {@code FHIR.string})
 * that map to FHIRPath System types. This class models that relationship, allowing the type system
 * to distinguish between FHIR-level and System-level types while delegating to the underlying
 * System type for operations.
 *
 * <p>Uses the flyweight pattern to ensure reference equality ({@code ==}) works correctly, which is
 * required by {@link com.example.fhirpath.operation.OverloadResolver}.
 */
public final class FhirPrimitiveType implements Type {

  private static final Map<String, FhirPrimitiveType> CACHE = new ConcurrentHashMap<>();

  private static final Map<String, SystemType> FHIR_TO_SYSTEM =
      Map.ofEntries(
          Map.entry("boolean", SystemType.BOOLEAN),
          Map.entry("string", SystemType.STRING),
          Map.entry("uri", SystemType.STRING),
          Map.entry("url", SystemType.STRING),
          Map.entry("canonical", SystemType.STRING),
          Map.entry("code", SystemType.STRING),
          Map.entry("oid", SystemType.STRING),
          Map.entry("id", SystemType.STRING),
          Map.entry("uuid", SystemType.STRING),
          Map.entry("markdown", SystemType.STRING),
          Map.entry("base64Binary", SystemType.STRING),
          Map.entry("integer", SystemType.INTEGER),
          Map.entry("unsignedInt", SystemType.INTEGER),
          Map.entry("positiveInt", SystemType.INTEGER),
          Map.entry("decimal", SystemType.DECIMAL),
          Map.entry("date", SystemType.DATE),
          Map.entry("dateTime", SystemType.DATE_TIME),
          Map.entry("instant", SystemType.DATE_TIME),
          Map.entry("time", SystemType.TIME));

  private final String fhirName;
  private final SystemType systemType;

  private FhirPrimitiveType(@Nonnull final String fhirName, @Nonnull final SystemType systemType) {
    this.fhirName = fhirName;
    this.systemType = systemType;
  }

  /**
   * Returns the cached FhirPrimitiveType for the given FHIR type name.
   *
   * @param fhirName the FHIR primitive type name (e.g., "date", "string", "code")
   * @return the cached FhirPrimitiveType instance, or {@code null} if the name is not a known FHIR
   *     primitive
   */
  @Nonnull
  public static FhirPrimitiveType of(@Nonnull final String fhirName) {
    return CACHE.computeIfAbsent(
        fhirName,
        name -> {
          final SystemType systemType = FHIR_TO_SYSTEM.get(name);
          if (systemType == null) {
            throw new IllegalArgumentException("Unknown FHIR primitive type: " + name);
          }
          return new FhirPrimitiveType(name, systemType);
        });
  }

  /**
   * Returns whether the given name is a known FHIR primitive type.
   *
   * @param fhirName the FHIR type name to check
   * @return true if the name maps to a known FHIR primitive type
   */
  public static boolean isKnown(@Nonnull final String fhirName) {
    return FHIR_TO_SYSTEM.containsKey(fhirName);
  }

  /**
   * Returns the System type that the given FHIR type name maps to, if known.
   *
   * @param fhirName the FHIR type name (e.g., "string", "date", "code")
   * @return the corresponding SystemType, or empty if the name is not a known FHIR primitive
   */
  @Nonnull
  public static Optional<SystemType> systemTypeFor(@Nonnull final String fhirName) {
    return Optional.ofNullable(FHIR_TO_SYSTEM.get(fhirName));
  }

  /**
   * Returns the FHIR type name (e.g., "string", "boolean", "date").
   *
   * @return the unqualified FHIR type name
   */
  @Nonnull
  public String getFhirName() {
    return fhirName;
  }

  /**
   * Returns the underlying FHIRPath System type.
   *
   * @return the System type that this FHIR type maps to
   */
  @Nonnull
  public SystemType getSystemType() {
    return systemType;
  }

  @Override
  public String getName() {
    return "FHIR." + fhirName;
  }

  /**
   * Returns {@code false} because this is a FHIR-layer type, not a FHIRPath System primitive. The
   * underlying System type (accessible via {@link #getSystemType()}) is the actual primitive.
   */
  @Override
  public boolean isPrimitive() {
    return false;
  }

  @Override
  public boolean isComplex() {
    return false;
  }

  @Override
  public String toString() {
    return getName();
  }
}
