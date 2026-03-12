package com.example.fhirpath.parser;

import static java.util.Map.entry;

import jakarta.annotation.Nonnull;
import java.util.Map;
import org.apache.commons.text.translate.AggregateTranslator;
import org.apache.commons.text.translate.CharSequenceTranslator;
import org.apache.commons.text.translate.LookupTranslator;
import org.apache.commons.text.translate.UnicodeUnescaper;

/**
 * Utility for processing FHIRPath string literal escape sequences.
 *
 * <p>Implements escape sequence processing as defined in FHIRPath specification section 3.1.
 *
 * <p>Supported escape sequences:
 *
 * <ul>
 *   <li>{@code \'} - Single-quote
 *   <li>{@code \"} - Double-quote
 *   <li>{@code \`} - Backtick
 *   <li>{@code \r} - Carriage Return
 *   <li>{@code \n} - Line Feed
 *   <li>{@code \t} - Tab
 *   <li>{@code \f} - Form Feed
 *   <li>{@code \\} - Backslash
 *   <li>{@code \}{@code uXXXX} - Unicode character (4 hex digits)
 * </ul>
 *
 * <p>Implementation uses Apache Commons Text translators for cleaner, more maintainable code.
 *
 * @see <a href="https://hl7.org/fhirpath/index.html#string">FHIRPath String Literals</a>
 */
public final class StringEscapeUtils {

  private StringEscapeUtils() {
    // Utility class - no instantiation
  }

  private static final Map<CharSequence, CharSequence> FHIR_CTRL_UNESCAPE_MAP =
      Map.ofEntries(entry("\\n", "\n"), entry("\\t", "\t"), entry("\\f", "\f"), entry("\\r", "\r"));

  private static final Map<CharSequence, CharSequence> FHIR_CHAR_UNESCAPE_MAP =
      Map.ofEntries(entry("\\`", "`"), entry("\\'", "'"), entry("\\\"", "\""), entry("\\\\", "\\"));

  private static final CharSequenceTranslator UNESCAPE_FHIR =
      new AggregateTranslator(
          new UnicodeUnescaper(),
          new LookupTranslator(FHIR_CTRL_UNESCAPE_MAP),
          new LookupTranslator(FHIR_CHAR_UNESCAPE_MAP));

  /**
   * Unescapes a FHIRPath string literal value according to the FHIRPath specification.
   *
   * <p>Processes all escape sequences defined in FHIRPath spec section 3.1 using Apache Commons
   * Text translators for robust handling of Unicode and standard escapes.
   *
   * @param escaped The escaped string (with quotes already removed)
   * @return The unescaped string with all escape sequences processed
   */
  @Nonnull
  public static String unescapeFhirPathString(@Nonnull final String escaped) {
    return UNESCAPE_FHIR.translate(escaped);
  }
}
