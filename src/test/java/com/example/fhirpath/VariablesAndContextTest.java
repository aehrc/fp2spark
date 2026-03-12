package com.example.fhirpath;

import static com.example.fhirpath.test.FhirPathTestBuilder.context;

import com.example.fhirpath.test.FhirPathTestBase;
import java.util.List;
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

  @TestFactory
  Stream<DynamicTest> testResourceFieldAccess() {
    return builder()
        .group("%resource field access")
        .withSubject(
            "Patient",
            p ->
                p.string("gender", "male")
                    .elementArray(
                        "name",
                        n -> n.string("family", "Szul").string("use", "official"),
                        n -> n.string("family", "Brown").string("use", "alias")))
        .testEquals("male", "%resource.gender", "Direct field access")
        .testEquals(2, "%resource.name.count()", "Nested collection count")
        .testEquals(
            List.of("official", "alias"),
            "%resource.name.use",
            "Nested field traversal returns flat list")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testContextNestedAccess() {
    return builder()
        .group("%context nested access")
        .withSubject(
            "Patient",
            p ->
                p.string("gender", "male")
                    .elementArray(
                        "name",
                        n -> n.string("family", "Szul").string("use", "official"),
                        n -> n.string("family", "Brown").string("use", "alias")))
        .testEquals(
            List.of("official", "alias"), "%context.name.use", "Nested field through context")
        .testEquals(2, "%context.name.count()", "Collection count through context")
        .testEquals(
            "Szul",
            "%context.name.where(use = 'official').family.first()",
            "Collection ops on context")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testContextResourceEquivalence() {
    return builder()
        .group("%context and %resource equivalence")
        .withSubject(
            "Patient",
            p ->
                p.string("gender", "male")
                    .elementArray(
                        "name",
                        n -> n.string("family", "Szul").string("use", "official"),
                        n -> n.string("family", "Brown").string("use", "alias")))
        .testTrue(
            "%context.gender = %resource.gender",
            "Context and resource refer to same value when context is resource")
        .build();
  }
}
