package com.example.fhirpath.parser;

import com.example.fhirpath.ast.AstNode;
import org.antlr.v4.runtime.BailErrorStrategy;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;

/** Facade for parsing FHIRPath expressions into AST nodes. */
public final class ParserFacade {
  private ParserFacade() {}

  /**
   * Parses a FHIRPath expression string into an AST node.
   *
   * @param expr the FHIRPath expression to parse
   * @return the root AST node of the parsed expression
   */
  public static AstNode parse(final String expr) {
    final FhirPathLexer lexer = new FhirPathLexer(CharStreams.fromString(expr));
    final FhirPathParser parser = new FhirPathParser(new CommonTokenStream(lexer));
    parser.setErrorHandler(new BailErrorStrategy());
    return new AstBuilderVisitor().visit(parser.entireExpression());
  }
}
