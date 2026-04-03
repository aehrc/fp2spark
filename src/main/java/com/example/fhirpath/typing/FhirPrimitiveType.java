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

  private static final Map<String, PrimitiveType> FHIR_TO_SYSTEM =
      Map.ofEntries(
          Map.entry("boolean", PrimitiveType.BOOLEAN),
          Map.entry("string", PrimitiveType.STRING),
          Map.entry("uri", PrimitiveType.STRING),
          Map.entry("url", PrimitiveType.STRING),
          Map.entry("canonical", PrimitiveType.STRING),
          Map.entry("code", PrimitiveType.STRING),
          Map.entry("oid", PrimitiveType.STRING),
          Map.entry("id", PrimitiveType.STRING),
          Map.entry("uuid", PrimitiveType.STRING),
          Map.entry("markdown", PrimitiveType.STRING),
          Map.entry("base64Binary", PrimitiveType.STRING),
          Map.entry("integer", PrimitiveType.INTEGER),
          Map.entry("unsignedInt", PrimitiveType.INTEGER),
          Map.entry("positiveInt", PrimitiveType.INTEGER),
          Map.entry("decimal", PrimitiveType.DECIMAL),
          Map.entry("date", PrimitiveType.DATE),
          Map.entry("dateTime", PrimitiveType.DATE_TIME),
          Map.entry("instant", PrimitiveType.DATE_TIME),
          Map.entry("time", PrimitiveType.TIME));

  private final String fhirName;
  private final PrimitiveType systemType;

  private FhirPrimitiveType(
      @Nonnull final String fhirName, @Nonnull final PrimitiveType systemType) {
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
          final PrimitiveType systemType = FHIR_TO_SYSTEM.get(name);
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
   * @return the corresponding PrimitiveType, or empty if the name is not a known FHIR primitive
   */
  @Nonnull
  public static Optional<PrimitiveType> systemTypeFor(@Nonnull final String fhirName) {
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
  public PrimitiveType getSystemType() {
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
