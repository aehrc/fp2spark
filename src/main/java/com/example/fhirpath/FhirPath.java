package com.example.fhirpath;

import com.example.fhirpath.analyzer.Analyzer;
import com.example.fhirpath.ast.AstNode;
import com.example.fhirpath.ir.IRNode;
import com.example.fhirpath.parser.ParserFacade;
import org.apache.spark.sql.Column;

public final class FhirPath {
    private FhirPath() {}

    /**
     * Compile a FHIRPath expression into a Spark SQL Column using direct IR evaluation.
     * Note: aggregate functions like count() must be used in an aggregation context.
     */
    public static Column toColumn(String expr) {
        AstNode ast = ParserFacade.parse(expr);
        IRNode ir = new Analyzer().analyze(ast);
        return ir.eval();
    }
}

