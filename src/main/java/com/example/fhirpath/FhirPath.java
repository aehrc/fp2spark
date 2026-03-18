package com.example.fhirpath;

import com.example.fhirpath.analyzer.Analyzer;
import com.example.fhirpath.ast.AstNode;
import com.example.fhirpath.codegen.spark.SparkCodeGenerator;
import com.example.fhirpath.codegen.spark.SparkOperationRegistry;
import com.example.fhirpath.ir.IRNode;
import com.example.fhirpath.parser.ParserFacade;
import com.example.fhirpath.typing.ResourceType;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.spark.sql.Column;

/**
 * Main API for compiling FHIRPath expressions to Apache Spark SQL columns.
 *
 * <p>This class provides static methods to parse, analyze, and generate Spark SQL code from
 * FHIRPath expressions. It serves as the primary entry point for the FHIRPath to SQL translation
 * system.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * Column result = FhirPath.toColumn("Patient.name.given");
 * Column filtered = FhirPath.toColumn("Patient.name.where(use = 'official')");
 * Column withContext = FhirPath.toColumn("name.given", "Patient.name");
 * }</pre>
 *
 * <p>The compilation process follows a pipeline architecture:
 *
 * <ol>
 *   <li>Parse the FHIRPath expression into an Abstract Syntax Tree (AST)
 *   <li>Analyze the AST to produce a typed Intermediate Representation (IR)
 *   <li>Generate a Spark SQL Column from the IR
 * </ol>
 */
@Slf4j
public final class FhirPath {
  private FhirPath() {}

  /**
   * Compile a FHIRPath expression into a Spark SQL Column using direct IR evaluation. Note:
   * aggregate functions like count() must be used in an aggregation context.
   */
  @Nonnull
  public static Column toColumn(@Nonnull final String expr) {
    return compile(expr, null, null);
  }

  /**
   * Compile a FHIRPath expression into a Spark SQL Column with a provided context. The context
   * becomes available as the %context variable and serves as the implicit target for functions like
   * count() when no explicit target is provided.
   *
   * @param expr The FHIRPath expression to compile
   * @param context The FHIRPath expression to use as %context
   * @return A Spark SQL Column representing the compiled expression
   */
  @Nonnull
  public static Column toColumn(@Nonnull final String expr, @Nonnull final String context) {
    return compile(expr, context, null);
  }

  /**
   * Compile a FHIRPath expression with resource specification support. This enables traversal of
   * complex resource fields with proper type checking.
   *
   * @param expr The FHIRPath expression to compile
   * @param resourceSpec The resource specification defining the structure
   * @return A Spark SQL Column representing the compiled expression
   */
  @Nonnull
  public static Column toColumn(
      @Nonnull final String expr, @Nonnull final ResourceType resourceSpec) {
    return compile(expr, null, resourceSpec);
  }

  /**
   * Compile a FHIRPath expression with both context and resource specification.
   *
   * @param expr The FHIRPath expression to compile
   * @param context The FHIRPath expression to use as %context
   * @param resourceSpec The resource specification defining the structure
   * @return A Spark SQL Column representing the compiled expression
   */
  @Nonnull
  public static Column toColumn(
      @Nonnull final String expr,
      @Nonnull final String context,
      @Nonnull final ResourceType resourceSpec) {
    return compile(expr, context, resourceSpec);
  }

  /**
   * Compiles a FHIRPath expression into an IR node without generating target-specific code. This
   * allows two-step compilation: compile once, then generate with different root bindings.
   *
   * @param expr the FHIRPath expression to compile
   * @param resourceSpec the resource type specification
   * @param variables named variables available as %name in FHIRPath expressions
   * @return an IR node representing the compiled expression
   */
  @Nonnull
  public static IRNode compileToIr(
      @Nonnull final String expr,
      @Nonnull final ResourceType resourceSpec,
      @Nonnull final Map<String, IRNode> variables) {
    log.debug("Compiling FHIRPath expression to IR: {}", expr);
    final AstNode ast = ParserFacade.parse(expr);
    final Analyzer analyzer = new Analyzer(resourceSpec, variables);
    return analyzer.analyze(ast);
  }

  /**
   * Generates a Spark SQL Column from a pre-compiled IR node, optionally with a root column
   * binding. When rootColumn is null, resource-level field access uses top-level dataset columns.
   * When rootColumn is set, field access is relative to the root column.
   *
   * @param ir the pre-compiled IR node
   * @param rootColumn the root column for field access, or null for dataset root
   * @return a Spark SQL Column representing the IR
   */
  @Nonnull
  public static Column generate(@Nonnull final IRNode ir, @Nullable final Column rootColumn) {
    final SparkCodeGenerator gen =
        new SparkCodeGenerator(SparkOperationRegistry.standard()).withRootColumn(rootColumn);
    return ir.accept(gen);
  }

  /**
   * Core compilation logic that parses, analyzes, and generates code for a FHIRPath expression.
   *
   * @param expr The FHIRPath expression to compile
   * @param context Optional context expression (may be null)
   * @param resourceSpec Optional resource specification (may be null)
   * @return A Spark SQL Column representing the compiled expression
   */
  private static Column compile(
      @Nonnull final String expr, final String context, final ResourceType resourceSpec) {
    log.debug("Compiling FHIRPath expression: {}", expr);
    if (context != null) {
      log.debug("  with context: {}", context);
    }
    if (resourceSpec != null) {
      log.debug("  with resource spec: {}", resourceSpec);
    }

    // Parse expression to AST
    final AstNode ast = ParserFacade.parse(expr);
    log.debug("AST: {}", ast);

    // Parse context if provided
    final AstNode contextAst = context != null ? ParserFacade.parse(context) : null;

    // Create analyzer with appropriate parameters
    final Analyzer analyzer = createAnalyzer(contextAst, resourceSpec);

    // Analyze AST to produce IR
    final IRNode ir = analyzer.analyze(ast);
    log.debug("IR: {}", ir);

    // Generate Spark SQL Column from IR
    final Column column = ir.accept(new SparkCodeGenerator(SparkOperationRegistry.standard()));
    log.debug("SQL: {}", column);

    return column;
  }

  /**
   * Create an analyzer with the appropriate configuration.
   *
   * @param contextAst Optional context AST (may be null)
   * @param resourceSpec Optional resource specification (may be null)
   * @return Configured analyzer instance
   */
  private static Analyzer createAnalyzer(
      final AstNode contextAst, final ResourceType resourceSpec) {
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
