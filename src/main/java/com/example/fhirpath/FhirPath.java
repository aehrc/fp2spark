package com.example.fhirpath;

import com.example.fhirpath.analyzer.Analyzer;
import com.example.fhirpath.ast.AstNode;
import com.example.fhirpath.ir.IRNode;
import com.example.fhirpath.parser.ParserFacade;
import org.apache.spark.sql.Column;

import javax.annotation.Nonnull;

public final class FhirPath {
    private FhirPath() {
    }

    /**
     * Compile a FHIRPath expression into a Spark SQL Column using direct IR evaluation.
     * Note: aggregate functions like count() must be used in an aggregation context.
     */
    @Nonnull
    public static Column toColumn(@Nonnull final String expr) {
        AstNode ast = ParserFacade.parse(expr);
        IRNode ir = new Analyzer().analyze(ast);
        return ir.eval();
    }

    /**
     * Compile a FHIRPath expression into a Spark SQL Column with a provided context.
     * The context becomes available as the %context variable and serves as the implicit
     * target for functions like count() when no explicit target is provided.
     *
     * @param expr          The FHIRPath expression to compile
     * @param contextColumn The Spark SQL Column to use as %context
     * @param contextType   The type of the context column
     * @return A Spark SQL Column representing the compiled expression
     */
    @Nonnull
    public static Column toColumn(@Nonnull final String expr, @Nonnull final String context) {
        AstNode ast = ParserFacade.parse(expr);
        IRNode ir = new Analyzer(ParserFacade.parse(context)).analyze(ast);
        return ir.eval();
    }
}
