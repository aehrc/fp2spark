package com.example.fhirpath.typing;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.Map;
import java.util.Optional;

/**
 * Wrapper for FHIRPath Coding literal values (Pathling extension).
 *
 * <p>A Coding is a representation of a defined concept using a symbol from a defined code system.
 * The literal syntax is: {@code system|code[|version][|display[|userSelected]]}
 *
 * <p>Fields follow the FHIR Coding structure:
 *
 * <ul>
 *   <li>{@code system} — the code system URI
 *   <li>{@code code} — the code value
 *   <li>{@code version} — the code system version (nullable)
 *   <li>{@code display} — the human-readable display text (nullable)
 *   <li>{@code userSelected} — whether the coding was chosen by the user (nullable)
 * </ul>
 *
 * @param system the code system URI
 * @param code the code value
 * @param version the code system version (nullable)
 * @param display the human-readable display text (nullable)
 * @param userSelected whether the coding was chosen by the user (nullable)
 */
public record CodingValue(
    @Nonnull String system,
    @Nonnull String code,
    @Nullable String version,
    @Nullable String display,
    @Nullable Boolean userSelected) {

  private static final Map<String, FieldSpec> FIELDS =
      Map.of(
          "system", new FieldSpec("system", Shape.single(PrimitiveType.STRING)),
          "code", new FieldSpec("code", Shape.single(PrimitiveType.STRING)),
          "version", new FieldSpec("version", Shape.single(PrimitiveType.STRING)),
          "display", new FieldSpec("display", Shape.single(PrimitiveType.STRING)),
          "userSelected", new FieldSpec("userSelected", Shape.single(PrimitiveType.BOOLEAN)));

  /**
   * Resolves a Coding field by name.
   *
   * @param fieldName the field name to resolve
   * @return the field specification, or empty if not a valid Coding field
   */
  @Nonnull
  public static Optional<FieldSpec> resolveField(@Nonnull final String fieldName) {
    return Optional.ofNullable(FIELDS.get(fieldName));
  }
}
