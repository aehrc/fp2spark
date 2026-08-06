package au.csiro.fhirpath.ast;

/**
 * Represents a binary operator expression in FHIRPath (e.g., {@code a + b}, {@code x = y}).
 *
 * @param operator the operator symbol (e.g., "+", "=", "and")
 * @param left the left operand
 * @param right the right operand
 */
public record AstBinaryOperator(String operator, AstNode left, AstNode right) implements AstNode {}
