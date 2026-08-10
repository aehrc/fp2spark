/*
 * Copyright © 2025-2026 Commonwealth Scientific and Industrial Research
 * Organisation (CSIRO) ABN 41 687 119 230.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
