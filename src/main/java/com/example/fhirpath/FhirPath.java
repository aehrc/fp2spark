package com.example.fhirpath;

import com.example.fhirpath.analyzer.Analyzer;
import com.example.fhirpath.ast.AstNode;
import com.example.fhirpath.codegen.spark.SparkCodeGenerator;
import com.example.fhirpath.ir.IRNode;
import com.example.fhirpath.parser.ParserFacade;
import com.example.fhirpath.typing.ResourceType;
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
        return ir.accept(new SparkCodeGenerator());
    }

    /**
     * Compile a FHIRPath expression into a Spark SQL Column with a provided context.
     * The context becomes available as the %context variable and serves as the implicit
     * target for functions like count() when no explicit target is provided.
     *
     * @param expr    The FHIRPath expression to compile
     * @param context The FHIRPath expression to use as %context
     * @return A Spark SQL Column representing the compiled expression
     */
    @Nonnull
    public static Column toColumn(@Nonnull final String expr, @Nonnull final String context) {
        AstNode ast = ParserFacade.parse(expr);
        IRNode ir = new Analyzer(ParserFacade.parse(context)).analyze(ast);
        return ir.accept(new SparkCodeGenerator());
    }

    /**
     * Compile a FHIRPath expression with resource specification support.
     * This enables traversal of complex resource fields with proper type checking.
     *
     * @param expr         The FHIRPath expression to compile
     * @param resourceSpec The resource specification defining the structure
     * @return A Spark SQL Column representing the compiled expression
     */
    @Nonnull
    public static Column toColumn(@Nonnull final String expr, @Nonnull final ResourceType resourceSpec) {
        AstNode ast = ParserFacade.parse(expr);
        IRNode ir = new Analyzer(resourceSpec).analyze(ast);
        return ir.accept(new SparkCodeGenerator());

    }

    /**
     * Compile a FHIRPath expression with both context and resource specification.
     *
     * @param expr         The FHIRPath expression to compile
     * @param context      The FHIRPath expression to use as %context
     * @param resourceSpec The resource specification defining the structure
     * @return A Spark SQL Column representing the compiled expression
     */
    @Nonnull
    public static Column toColumn(@Nonnull final String expr, @Nonnull final String context, @Nonnull final ResourceType resourceSpec) {
        AstNode ast = ParserFacade.parse(expr);
        IRNode ir = new Analyzer(ParserFacade.parse(context), resourceSpec).analyze(ast);
        return ir.accept(new SparkCodeGenerator());
    }
}
