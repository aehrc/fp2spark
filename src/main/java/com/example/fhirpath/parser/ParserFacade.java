package com.example.fhirpath.parser;

import com.example.fhirpath.ast.AstNode;
import org.antlr.v4.runtime.BailErrorStrategy;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;

public final class ParserFacade {
    private ParserFacade() {}

    public static AstNode parse(String expr) {
        FhirPathLexer lexer = new FhirPathLexer(CharStreams.fromString(expr));
        FhirPathParser parser = new FhirPathParser(new CommonTokenStream(lexer));
        parser.setErrorHandler(new BailErrorStrategy());
        return new AstBuilderVisitor().visit(parser.parse());
    }
}

