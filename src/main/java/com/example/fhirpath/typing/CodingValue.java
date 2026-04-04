package com.example.fhirpath.typing;

import com.example.fhirpath.parser.StringEscapeUtils;
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
          "system", new FieldSpec("system", Shape.single(SystemType.STRING)),
          "code", new FieldSpec("code", Shape.single(SystemType.STRING)),
          "version", new FieldSpec("version", Shape.single(SystemType.STRING)),
          "display", new FieldSpec("display", Shape.single(SystemType.STRING)),
          "userSelected", new FieldSpec("userSelected", Shape.single(SystemType.BOOLEAN)));

  /**
   * Parses a pipe-delimited Coding literal string. Components may be single-quoted (quotes are
   * stripped and escape sequences are processed).
   *
   * @param literal the pipe-delimited string (e.g. {@code "http://loinc.org|1234||'Display'"})
   * @return the parsed CodingValue
   */
  @Nonnull
  public static CodingValue parse(@Nonnull final String literal) {
    final String[] parts = literal.split("\\|", -1);
    final String system = parts.length > 0 ? unquote(parts[0]) : "";
    final String code = parts.length > 1 ? unquote(parts[1]) : "";
    final String version = parts.length > 2 ? nullIfEmpty(unquote(parts[2])) : null;
    final String display = parts.length > 3 ? nullIfEmpty(unquote(parts[3])) : null;
    final Boolean userSelected =
        parts.length > 4 && !parts[4].trim().isEmpty()
            ? Boolean.parseBoolean(unquote(parts[4]))
            : null;
    return new CodingValue(system, code, version, display, userSelected);
  }

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

  /** Strips surrounding single quotes and processes FHIRPath escape sequences. */
  private static String unquote(@Nonnull final String s) {
    final String trimmed = s.trim();
    if (trimmed.length() >= 2 && trimmed.startsWith("'") && trimmed.endsWith("'")) {
      return StringEscapeUtils.unescapeFhirPathString(trimmed.substring(1, trimmed.length() - 1));
    }
    return trimmed;
  }

  private static String nullIfEmpty(@Nonnull final String s) {
    return s.isEmpty() ? null : s;
  }
}
