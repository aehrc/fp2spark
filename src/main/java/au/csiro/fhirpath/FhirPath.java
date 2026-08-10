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
package au.csiro.fhirpath;

import au.csiro.fhirpath.analyzer.Analyzer;
import au.csiro.fhirpath.ast.AstNode;
import au.csiro.fhirpath.ir.IRNode;
import au.csiro.fhirpath.parser.ParserFacade;
import au.csiro.fhirpath.spark.SparkCodeGenerator;
import au.csiro.fhirpath.spark.SparkOperationRegistry;
import au.csiro.fhirpath.terminology.NoTerminologyService;
import au.csiro.fhirpath.terminology.TerminologyServiceFactory;
import au.csiro.fhirpath.typing.ResourceType;
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
    return compile(expr, null, null, NoTerminologyService.INSTANCE);
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
    return compile(expr, context, null, NoTerminologyService.INSTANCE);
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
    return compile(expr, null, resourceSpec, NoTerminologyService.INSTANCE);
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
    return compile(expr, context, resourceSpec, NoTerminologyService.INSTANCE);
  }

  /**
   * Compile a FHIRPath expression with additional compilation options, such as terminology server
   * access for expressions using terminology functions like {@code memberOf()}.
   *
   * <p>This is the general form: {@code context} and {@code resourceSpec} are both optional.
   *
   * @param expr The FHIRPath expression to compile
   * @param context The FHIRPath expression to use as %context, or null for none
   * @param resourceSpec The resource specification defining the structure, or null for none
   * @param options Additional compilation options
   * @return A Spark SQL Column representing the compiled expression
   */
  @Nonnull
  public static Column toColumn(
      @Nonnull final String expr,
      @Nullable final String context,
      @Nullable final ResourceType resourceSpec,
      @Nonnull final CompilationOptions options) {
    return compile(expr, context, resourceSpec, options.terminologyServiceFactory());
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
    return generate(ir, rootColumn, CompilationOptions.defaults());
  }

  /**
   * Generates a Spark SQL Column from a pre-compiled IR node, with additional compilation options.
   *
   * @param ir the pre-compiled IR node
   * @param rootColumn the root column for field access, or null for dataset root
   * @param options Additional compilation options
   * @return a Spark SQL Column representing the IR
   */
  @Nonnull
  public static Column generate(
      @Nonnull final IRNode ir,
      @Nullable final Column rootColumn,
      @Nonnull final CompilationOptions options) {
    final SparkCodeGenerator gen =
        new SparkCodeGenerator(SparkOperationRegistry.standard(options.terminologyServiceFactory()))
            .withRootColumn(rootColumn);
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
      @Nonnull final String expr,
      final String context,
      final ResourceType resourceSpec,
      @Nonnull final TerminologyServiceFactory terminologyServiceFactory) {
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
    final Column column =
        ir.accept(
            new SparkCodeGenerator(SparkOperationRegistry.standard(terminologyServiceFactory)));
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
