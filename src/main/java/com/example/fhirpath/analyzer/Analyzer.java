package com.example.fhirpath.analyzer;

import com.example.fhirpath.ast.*;
import com.example.fhirpath.ir.*;
import com.example.fhirpath.typing.Type;

public class Analyzer {

    public IRNode analyze(AstNode node) {
        if (node instanceof AstLiteral lit) {
            return new Literal(lit.value(), inferType(lit.value()));
        }
        if (node instanceof AstTraversal trav) {
            return new Traversal(trav.path());
        }
        if (node instanceof AstFunctionCall call) {
            return FunctionRegistry.resolve(this, call);
        }
        throw new IllegalArgumentException("Unsupported AST node: " + node);
    }

    private Type inferType(Object value) {
        if (value instanceof Integer) return Type.INTEGER;
        if (value instanceof Long) return Type.INTEGER;
        if (value instanceof Double || value instanceof Float) return Type.DECIMAL;
        if (value instanceof Boolean) return Type.BOOLEAN;
        if (value instanceof String) return Type.STRING;
        return Type.UNKNOWN;
    }
}
