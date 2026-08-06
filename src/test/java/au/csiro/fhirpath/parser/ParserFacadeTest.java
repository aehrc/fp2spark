package au.csiro.fhirpath.parser;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.antlr.v4.runtime.misc.ParseCancellationException;
import org.junit.jupiter.api.Test;

/** Tests for {@link ParserFacade} error handling. */
class ParserFacadeTest {

  @Test
  void validExpressionParses() {
    assertDoesNotThrow(() -> ParserFacade.parse("1 + 2"));
  }

  @Test
  void lexerErrorThrowsIllegalArgumentException() {
    // @T is not a valid token (TIME requires digits after @T)
    final IllegalArgumentException ex =
        assertThrows(IllegalArgumentException.class, () -> ParserFacade.parse("@T"));
    assertTrue(ex.getMessage().contains("Invalid FHIRPath expression"));
  }

  @Test
  void parserErrorThrowsParseCancellationException() {
    // Lexically valid tokens but syntactically invalid
    assertThrows(ParseCancellationException.class, () -> ParserFacade.parse("1 2"));
  }
}
