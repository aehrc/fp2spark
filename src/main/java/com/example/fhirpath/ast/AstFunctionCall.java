package com.example.fhirpath.ast;

import java.util.List;

public class AstFunctionCall implements AstNode {
    private final String functionName;
    private final List<AstNode> arguments;

    public AstFunctionCall(String functionName, List<AstNode> arguments) {
        this.functionName = functionName;
        this.arguments = arguments;
    }

    public String getFunctionName() { return functionName; }
    public List<AstNode> getArguments() { return arguments; }

    @Override
    public int getId() { return System.identityHashCode(this); }
}

