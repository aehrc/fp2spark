/*
 * Copyright © 2025-2026 Commonwealth Scientific and Industrial Research
 * Organisation (CSIRO) ABN 41 687 119 230.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package au.csiro.fhirpath;

import static au.csiro.fhirpath.test.FhirPathTestBuilder.context;

import au.csiro.fhirpath.test.FhirPathTestBase;
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
 *   <li>%resource field access and nested collection traversal
 *   <li>%context nested access with where(), count(), and field navigation
 *   <li>%context and %resource equivalence when context is the resource root
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
        .testEquals(1, "%context.count()", context("'x'"), "Single value context")
        .testEquals(0, "%context.count()", context("{}"), "Empty context")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testImplicitContext() {
    return builder()
        .group("Implicit context for functions")
        .testTrue("exists()", context("'x'"), "Implicit exists on context")
        .testFalse("%context.exists()", context("{}"), "Empty context has no existence")
        .build();
  }

  @TestFactory
  Stream<DynamicTest> testArithmeticWithContext() {
    return builder()
        .group("Arithmetic operations with context")
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
        .testEquals(1, "Patient.count()", "Type specifier shorthand counts resource")
        .testTrue("Patient.exists()", "Type specifier shorthand confirms resource exists")
        .testEquals("male", "Patient.gender", "Type specifier field traversal")
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
