package com.example.fhirpath.ast;

import java.util.List;

public record AstFunctionCall(String functionName, List<AstNode> arguments) implements AstNode {
}
