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

import au.csiro.fhirpath.ast.AstNode;
import org.antlr.v4.runtime.BailErrorStrategy;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;

/** Facade for parsing FHIRPath expressions into AST nodes. */
public final class ParserFacade {
  private ParserFacade() {}

  /**
   * Error listener that throws on any lexer error, converting token recognition failures into
   * exceptions rather than silently skipping bad tokens.
   */
  private static final BaseErrorListener THROWING_ERROR_LISTENER =
      new BaseErrorListener() {
        @Override
        public void syntaxError(
            final Recognizer<?, ?> recognizer,
            final Object offendingSymbol,
            final int line,
            final int charPositionInLine,
            final String msg,
            final RecognitionException e) {
          throw new IllegalArgumentException(
              "Invalid FHIRPath expression at line "
                  + line
                  + ":"
                  + charPositionInLine
                  + " - "
                  + msg,
              e);
        }
      };

  /**
   * Parses a FHIRPath expression string into an AST node.
   *
   * @param expr the FHIRPath expression to parse
   * @return the root AST node of the parsed expression
   */
  public static AstNode parse(final String expr) {
    final FhirPathLexer lexer = new FhirPathLexer(CharStreams.fromString(expr));
    lexer.removeErrorListeners();
    lexer.addErrorListener(THROWING_ERROR_LISTENER);
    final FhirPathParser parser = new FhirPathParser(new CommonTokenStream(lexer));
    parser.setErrorHandler(new BailErrorStrategy());
    return new AstBuilderVisitor().visit(parser.entireExpression());
  }
}
