package com.example.fhirpath.typing;

import jakarta.annotation.Nonnull;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.hl7.fhir.r4.model.Enumerations.FHIRDefinedType;

/**
 * Represents a FHIRPath type specifier with namespace and type name.
 *
 * <p>Handles qualified ({@code FHIR.string}, {@code System.String}) and unqualified ({@code
 * String}, {@code decimal}) type specifiers. For unqualified names, the FHIR namespace is searched
 * first, then System — following the Pathling reference implementation.
 *
 * <p>Provides namespace-aware type matching via {@link #matchesType(Type)} and FHIR variant name
 * resolution via {@link #toFhirVariantName()} for choice type operations.
 */
public final class TypeSpecifier {

  /** The namespace identifier for FHIRPath System types. */
  public static final String SYSTEM_NAMESPACE = "System";

  /** The namespace identifier for FHIR types. */
  public static final String FHIR_NAMESPACE = "FHIR";

  /** Pre-built set of valid FHIR type codes, avoiding exception-driven control flow. */
  private static final Set<String> VALID_FHIR_TYPES =
      Stream.of(FHIRDefinedType.values())
          .filter(t -> t != FHIRDefinedType.NULL)
          .map(FHIRDefinedType::toCode)
          .collect(Collectors.toUnmodifiableSet());

  /**
   * Maps System type names to their default FHIR variant names. Used by {@link
   * #toFhirVariantName()} to convert System namespace specifiers to FHIR column names for choice
   * type resolution.
   *
   * <p>This cannot be derived by inverting the FHIR→System mapping because that mapping is
   * many-to-one (e.g., "uri", "code", "markdown" all map to System.String). A canonical FHIR name
   * must be chosen per System type.
   */
  private static final Map<String, String> SYSTEM_TO_FHIR_VARIANT =
      Map.of(
          "String", "string",
          "Integer", "integer",
          "Decimal", "decimal",
          "Boolean", "boolean",
          "Date", "date",
          "DateTime", "dateTime",
          "Time", "time",
          "Quantity", "Quantity",
          "Coding", "Coding");

  private final String namespace;
  private final String typeName;

  private TypeSpecifier(@Nonnull final String namespace, @Nonnull final String typeName) {
    this.namespace = namespace;
    this.typeName = typeName;
  }

  /**
   * Parses a type specifier expression into a validated TypeSpecifier.
   *
   * <p>Handles three forms:
   *
   * <ul>
   *   <li>Qualified FHIR: {@code "FHIR.string"}, {@code "FHIR.Quantity"}
   *   <li>Qualified System: {@code "System.String"}, {@code "System.Integer"}
   *   <li>Unqualified: {@code "String"}, {@code "decimal"}, {@code "HumanName"}
   * </ul>
   *
   * <p>Unqualified names are resolved by searching the FHIR namespace first, then System.
   *
   * @param expression the raw type specifier string
   * @return the resolved TypeSpecifier
   * @throws IllegalArgumentException if the type is invalid or doesn't exist in the claimed
   *     namespace
   */
  @Nonnull
  public static TypeSpecifier fromExpression(@Nonnull final String expression) {
    final int dotIndex = expression.indexOf('.');
    if (dotIndex > 0) {
      final String ns = expression.substring(0, dotIndex);
      final String name = expression.substring(dotIndex + 1);
      if (FHIR_NAMESPACE.equals(ns) || SYSTEM_NAMESPACE.equals(ns)) {
        return ofQualified(ns, name);
      }
    }
    return ofUnqualified(expression);
  }

  /**
   * Returns the namespace of this type specifier.
   *
   * @return {@value #FHIR_NAMESPACE} or {@value #SYSTEM_NAMESPACE}
   */
  @Nonnull
  public String getNamespace() {
    return namespace;
  }

  /**
   * Returns the unqualified type name (e.g., "String", "boolean", "HumanName").
   *
   * @return the type name without namespace prefix
   */
  @Nonnull
  public String getTypeName() {
    return typeName;
  }

  /**
   * Returns whether this is a FHIR namespace type specifier.
   *
   * @return true if the namespace is {@value #FHIR_NAMESPACE}
   */
  public boolean isFhirType() {
    return FHIR_NAMESPACE.equals(namespace);
  }

  /**
   * Returns whether this is a System namespace type specifier.
   *
   * @return true if the namespace is {@value #SYSTEM_NAMESPACE}
   */
  public boolean isSystemType() {
    return SYSTEM_NAMESPACE.equals(namespace);
  }

  /**
   * Returns the FHIR variant name for choice type column resolution.
   *
   * <p>For FHIR namespace, returns the type name directly (e.g., "string", "Quantity"). For System
   * namespace, reverse-maps to the default FHIR equivalent (e.g., "String" → "string", "Quantity" →
   * "Quantity").
   *
   * @return the FHIR variant name for use with {@link ChoiceTypeLike#resolveVariant(String)}
   * @throws IllegalStateException if no FHIR variant mapping exists for a System type
   */
  @Nonnull
  public String toFhirVariantName() {
    if (isFhirType()) {
      return typeName;
    }
    final String fhirName = SYSTEM_TO_FHIR_VARIANT.get(typeName);
    if (fhirName == null) {
      throw new IllegalStateException("No FHIR variant mapping for System type: " + typeName);
    }
    return fhirName;
  }

  /**
   * Checks whether a type matches this type specifier.
   *
   * <p>Handles cross-namespace equivalences: {@code FHIR.string} matches a type whose System type
   * is STRING, and {@code System.String} matches a FHIR type that maps to STRING.
   *
   * @param type the type to check
   * @return true if the type matches this specifier
   */
  public boolean matchesType(@Nonnull final Type type) {
    if (isSystemType()) {
      return matchesSystemType(type);
    }
    return matchesFhirType(type);
  }

  private boolean matchesSystemType(@Nonnull final Type type) {
    final PrimitiveType expected = PrimitiveType.fromName(typeName).orElse(null);
    if (expected == null) {
      return false;
    }
    if (type instanceof PrimitiveType pt) {
      return pt == expected;
    }
    if (type instanceof FhirPrimitiveType fpt) {
      return fpt.getSystemType() == expected;
    }
    return false;
  }

  private boolean matchesFhirType(@Nonnull final Type type) {
    if (type instanceof FhirPrimitiveType fpt) {
      return typeName.equals(fpt.getFhirName());
    }
    if (type instanceof PrimitiveType pt) {
      // Inline subjects use PrimitiveType directly — map FHIR name to PrimitiveType
      // via FhirPrimitiveType (for primitives like "string"→STRING) with fallback to
      // PrimitiveType.fromName() (for Coding/Quantity which share names across namespaces).
      final PrimitiveType mapped =
          FhirPrimitiveType.systemTypeFor(typeName)
              .orElseGet(() -> PrimitiveType.fromName(typeName).orElse(null));
      return mapped != null && mapped == pt;
    }
    if (type instanceof ComplexType ct) {
      return typeName.equals(ct.getName());
    }
    return false;
  }

  @Override
  public String toString() {
    return namespace + "." + typeName;
  }

  @Override
  public boolean equals(final Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof TypeSpecifier other)) {
      return false;
    }
    return namespace.equals(other.namespace) && typeName.equals(other.typeName);
  }

  @Override
  public int hashCode() {
    return 31 * namespace.hashCode() + typeName.hashCode();
  }

  // --- Private construction helpers ---

  @Nonnull
  private static TypeSpecifier ofQualified(
      @Nonnull final String namespace, @Nonnull final String typeName) {
    if (FHIR_NAMESPACE.equals(namespace)) {
      if (!isValidFhirType(typeName)) {
        throw new IllegalArgumentException("Invalid FHIR type: " + namespace + "." + typeName);
      }
    } else {
      assert SYSTEM_NAMESPACE.equals(namespace) : "Unexpected namespace: " + namespace;
      if (!isValidSystemType(typeName)) {
        throw new IllegalArgumentException("Invalid System type: " + namespace + "." + typeName);
      }
    }
    return new TypeSpecifier(namespace, typeName);
  }

  @Nonnull
  private static TypeSpecifier ofUnqualified(@Nonnull final String typeName) {
    // Search FHIR namespace first, then System (per Pathling/FHIRPath spec)
    if (isValidFhirType(typeName)) {
      return new TypeSpecifier(FHIR_NAMESPACE, typeName);
    }
    if (isValidSystemType(typeName)) {
      return new TypeSpecifier(SYSTEM_NAMESPACE, typeName);
    }
    throw new IllegalArgumentException("Unknown type: " + typeName);
  }

  private static boolean isValidFhirType(@Nonnull final String typeName) {
    return VALID_FHIR_TYPES.contains(typeName);
  }

  private static boolean isValidSystemType(@Nonnull final String typeName) {
    return PrimitiveType.fromName(typeName).isPresent();
  }
}
