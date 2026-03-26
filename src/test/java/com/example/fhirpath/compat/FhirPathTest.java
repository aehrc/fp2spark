package com.example.fhirpath.compat;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.TestFactory;

/**
 * Meta-annotation equivalent to {@link TestFactory} for Pathling compatibility tests.
 *
 * <p>Mirrors Pathling's {@code @FhirPathTest} annotation to minimize changes when importing
 * Pathling DSL test files.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@TestFactory
public @interface FhirPathTest {}
