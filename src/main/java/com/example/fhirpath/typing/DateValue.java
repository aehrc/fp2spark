package com.example.fhirpath.typing;

/**
 * Wrapper for FHIRPath Date literal values (e.g. {@code @2014-01-25}).
 *
 * @param value the ISO 8601 date string with {@code @} prefix stripped
 */
public record DateValue(String value) {}
