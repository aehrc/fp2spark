package com.example.fhirpath;

import static com.example.fhirpath.test.FhirPathTestBuilder.context;

import com.example.fhirpath.test.FhirPathTestBase;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Tests for FHIRPath variables and context: %context, %resource, and default empty context.
 *
 * <p>Based on FHIRPath specification section 3 (Path selection) and section 6.7 (Variables).
 *
 * <p>Covers:
 *
 * <ul>
 *   <li>Default empty context operations (count(), %resource, field access)
 *   <li>%context.count() with explicit context values
 *   <li>Implicit context for functions (exists())
 *   <li>Arithmetic operations with %context
 *   <li>Complex context expressions with multi-value contexts
 *   <li>%resource on resource subjects
 * </ul>
 */
public class VariablesAndContextTest extends FhirPathTestBase {

  // ========== Default empty context ==========

  @TestFactory
  Stream<DynamicTest> testDefaultEmptyContext() {
    return builder()
        .group("Empty context operations")
        .testEquals(0, "count()", "count() on empty context")
        .testFalse("%resource.exists()", "%resource does not exist")
        .testEmpty("%resource.foo", "Field access on empty resource")
        .testEmpty("bar", "Field access on empty implicit context")
        .build();
  }

  // ========== %context with explicit values ==========

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

  // ========== %resource on resource subjects ==========

  @TestFactory
  Stream<DynamicTest> testResourceWithContext() {
    return builder()
        .group("Resource with context")
        .withSubject(
            "Patient",
            p ->
                p.string("gender", "male")
                    .elementArray(
                        "name", n -> n.string("use", "official"), n -> n.string("use", "alias")))
        .testEquals(1, "%resource.count()")
        .testTrue("exists()")
        .build();
  }
}
