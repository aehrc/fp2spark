package com.example.fhirpath.util;

/**
 * Represents a source location (line and column) in a FHIRPath expression.
 *
 * @param line the 1-based line number
 * @param column the 1-based column number
 */
public record SourceLocation(int line, int column) {}
