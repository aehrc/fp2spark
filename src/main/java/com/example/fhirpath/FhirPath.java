package com.example.fhirpath;

import com.example.fhirpath.analyzer.Analyzer;
import com.example.fhirpath.ast.AstNode;
import com.example.fhirpath.codegen.spark.SparkCodeGenerator;
import com.example.fhirpath.ir.IRNode;
import com.example.fhirpath.parser.ParserFacade;
import com.example.fhirpath.typing.ResourceType;
import lombok.extern.slf4j.Slf4j;
import org.apache.spark.sql.Column;

import javax.annotation.Nonnull;

@Slf4j
public final class FhirPath {
    private FhirPath() {
    }

    /**
     * Compile a FHIRPath expression into a Spark SQL Column using direct IR evaluation.
     * Note: aggregate functions like count() must be used in an aggregation context.
     */
    @Nonnull
    public static Column toColumn(@Nonnull final String expr) {
        return compile(expr, null, null);
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
        return compile(expr, context, null);
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
        return compile(expr, null, resourceSpec);
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
        return compile(expr, context, resourceSpec);
    }

    /**
     * Core compilation logic that parses, analyzes, and generates code for a FHIRPath expression.
     *
     * @param expr         The FHIRPath expression to compile
     * @param context      Optional context expression (may be null)
     * @param resourceSpec Optional resource specification (may be null)
     * @return A Spark SQL Column representing the compiled expression
     */
    private static Column compile(@Nonnull final String expr, final String context, final ResourceType resourceSpec) {
        log.debug("Compiling FHIRPath expression: {}", expr);
        if (context != null) {
            log.debug("  with context: {}", context);
        }
        if (resourceSpec != null) {
            log.debug("  with resource spec: {}", resourceSpec);
        }

        // Parse expression to AST
        AstNode ast = ParserFacade.parse(expr);
        log.debug("AST: {}", ast);

        // Parse context if provided
        AstNode contextAst = context != null ? ParserFacade.parse(context) : null;

        // Create analyzer with appropriate parameters
        Analyzer analyzer = createAnalyzer(contextAst, resourceSpec);

        // Analyze AST to produce IR
        IRNode ir = analyzer.analyze(ast);
        log.debug("IR: {}", ir);

        // Generate Spark SQL Column from IR
        return ir.accept(new SparkCodeGenerator());
    }

    /**
     * Create an analyzer with the appropriate configuration based on context and resource spec.
     *
     * @param contextAst   Optional context AST (may be null)
     * @param resourceSpec Optional resource specification (may be null)
     * @return Configured analyzer instance
     */
    private static Analyzer createAnalyzer(final AstNode contextAst, final ResourceType resourceSpec) {
        if (contextAst != null && resourceSpec != null) {
            return new Analyzer(contextAst, resourceSpec);
        } else if (contextAst != null) {
            return new Analyzer(contextAst);
        } else if (resourceSpec != null) {
            return new Analyzer(resourceSpec);
        } else {
            return new Analyzer();
        }
    }
}
