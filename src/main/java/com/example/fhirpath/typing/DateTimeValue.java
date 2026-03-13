package com.example.fhirpath.typing;

/**
 * Wrapper for FHIRPath DateTime literal values (e.g. {@code @2014-01-25T14:30:14.559}).
 *
 * @param value the ISO 8601 date-time string with {@code @} prefix stripped
 */
public record DateTimeValue(String value) {}
