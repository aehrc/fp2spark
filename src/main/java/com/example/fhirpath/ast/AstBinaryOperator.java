package com.example.fhirpath.ast;

public record AstBinaryOperator(String operator, AstNode left, AstNode right) implements AstNode {
}
