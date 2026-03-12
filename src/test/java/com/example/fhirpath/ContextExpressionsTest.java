package com.example.fhirpath;

import static com.example.fhirpath.test.FhirPathTestBuilder.context;

import com.example.fhirpath.test.FhirPathTestBase;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Tests for FHIRPath expressions with context support.
 *
 * <p>These tests verify that expressions can reference the %context variable and that functions
 * like count() and exists() use context as implicit target when no explicit target is provided.
 *
 * <p>Ported from {@code FhirPathIntegrationTest#testFhirPathExpressionsWithContext}.
 */
public class ContextExpressionsTest extends FhirPathTestBase {

  @TestFactory
  Stream<DynamicTest> testContextCount() {
    return builder()
        .group("Context count operations")
        .testEquals(1, "%context.count()", context("'x'"))
        .testEquals(1, "%context.count()", context("'x'"), "Single value context")
        .testEquals(0, "%context.count()", context("{}"), "Empty context")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testImplicitContext() {
    return builder()
        .group("Implicit context for functions")
        .testTrue("exists()", context("'x'"))
        .testTrue("exists()", context("'x'"), "Implicit exists on context")
        .testFalse("%context.exists()", context("{}"))
        .testFalse("%context.exists()", context("{}"), "Empty context has no existence")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testArithmeticWithContext() {
    return builder()
        .group("Arithmetic operations with context")
        .testEquals(15, "5 + %context", context("10"))
        .testEquals(15, "5 + %context", context("10"), "Add to context value")
        .testEquals(50, "5 * %context", context("10"), "Multiply by context")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testComplexContextExpressions() {
    return builder()
        .group("Complex context expressions")
        .testTrue(
            "count() = 3",
            context("10 ; 20 ; 30 ; %context.foo"),
            "Multi-value context with field access")
        .build();
  }
}
