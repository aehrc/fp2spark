package com.example.fhirpath.typing;

/**
 * Wrapper for FHIRPath Time literal values (e.g. {@code @T14:30:14.559}).
 *
 * @param value the ISO 8601 time string with {@code @T} prefix stripped
 */
public record TimeValue(String value) {}
