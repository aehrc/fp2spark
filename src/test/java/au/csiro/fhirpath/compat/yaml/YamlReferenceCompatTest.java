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
package au.csiro.fhirpath.compat.yaml;

import au.csiro.fhirpath.compat.yaml.annotations.YamlTest;
import au.csiro.fhirpath.compat.yaml.annotations.YamlTestConfiguration;

/**
 * YAML-driven FHIRPath reference compatibility suite.
 *
 * <p>Ports Pathling's {@code YamlReferenceImplTest} (which itself mirrors the fhirpath.js 3.16.4
 * reference tests) to fp2sql. Each {@code @YamlTest} method expands into one JUnit parameterized
 * test per case in the referenced YAML file; {@code config.yaml} drives skip / XFAIL / expected-
 * pass behaviour via the {@link au.csiro.fhirpath.compat.yaml.format.ExcludeRule} schema.
 *
 * <p>Upstream source: https://github.com/hl7/fhirpath.js (tag 3.16.4), under the {@code
 * test/cases/} directory. Resources and exclusion config are copied verbatim from Pathling at
 * {@code .local/pathling/fhirpath/src/test/resources/fhirpath-js/} and should be refreshed together
 * when Pathling updates its snapshot.
 */
@YamlTestConfiguration(config = "fhirpath-js/config.yaml", resourceBase = "fhirpath-js/resources")
public class YamlReferenceCompatTest extends YamlTestBase {

  @YamlTest("fhirpath-js/cases/3.2_paths.yaml")
  void testPaths(final YamlTestExecutor testCase) {
    run(testCase);
  }

  @YamlTest("fhirpath-js/cases/4.1_literals.yaml")
  void testLiterals(final YamlTestExecutor testCase) {
    run(testCase);
  }

  @YamlTest("fhirpath-js/cases/5.1_existence.yaml")
  void testExistence(final YamlTestExecutor testCase) {
    run(testCase);
  }

  @YamlTest("fhirpath-js/cases/5.2_filtering_and_projection.yaml")
  void testFilteringAndProjection(final YamlTestExecutor testCase) {
    run(testCase);
  }

  @YamlTest("fhirpath-js/cases/5.2.3_repeat.yaml")
  void testRepeat(final YamlTestExecutor testCase) {
    run(testCase);
  }

  @YamlTest("fhirpath-js/cases/5.3_subsetting.yaml")
  void testSubsetting(final YamlTestExecutor testCase) {
    run(testCase);
  }

  @YamlTest("fhirpath-js/cases/5.4_combining.yaml")
  void testCombining(final YamlTestExecutor testCase) {
    run(testCase);
  }

  @YamlTest("fhirpath-js/cases/5.5_conversion.yaml")
  void testConversion(final YamlTestExecutor testCase) {
    run(testCase);
  }

  @YamlTest("fhirpath-js/cases/5.6_string_manipulation.yaml")
  void testStringManipulation(final YamlTestExecutor testCase) {
    run(testCase);
  }

  @YamlTest("fhirpath-js/cases/5.7_math.yaml")
  void testMath(final YamlTestExecutor testCase) {
    run(testCase);
  }

  @YamlTest("fhirpath-js/cases/5.8_tree_navigation.yaml")
  void testTreeNavigation(final YamlTestExecutor testCase) {
    run(testCase);
  }

  @YamlTest("fhirpath-js/cases/5.9_utility_functions.yaml")
  void testUtilityFunctions(final YamlTestExecutor testCase) {
    run(testCase);
  }

  @YamlTest("fhirpath-js/cases/6.1_equality.yaml")
  void testEquality(final YamlTestExecutor testCase) {
    run(testCase);
  }

  @YamlTest("fhirpath-js/cases/6.2_comparision.yaml")
  void testComparison(final YamlTestExecutor testCase) {
    run(testCase);
  }

  @YamlTest("fhirpath-js/cases/6.3_types.yaml")
  void testTypes(final YamlTestExecutor testCase) {
    run(testCase);
  }

  @YamlTest("fhirpath-js/cases/6.4_collection.yaml")
  void testCollection(final YamlTestExecutor testCase) {
    run(testCase);
  }

  @YamlTest("fhirpath-js/cases/6.4_collections.yaml")
  void testCollections(final YamlTestExecutor testCase) {
    run(testCase);
  }

  @YamlTest("fhirpath-js/cases/6.5_boolean_logic.yaml")
  void testBooleanLogic(final YamlTestExecutor testCase) {
    run(testCase);
  }

  @YamlTest("fhirpath-js/cases/6.6_math.yaml")
  void testAdvancedMath(final YamlTestExecutor testCase) {
    run(testCase);
  }

  @YamlTest("fhirpath-js/cases/7_aggregate.yaml")
  void testAggregate(final YamlTestExecutor testCase) {
    run(testCase);
  }

  @YamlTest("fhirpath-js/cases/8_variables.yaml")
  void testVariables(final YamlTestExecutor testCase) {
    run(testCase);
  }

  @YamlTest("fhirpath-js/cases/extensions.yaml")
  void testExtensions(final YamlTestExecutor testCase) {
    run(testCase);
  }

  @YamlTest("fhirpath-js/cases/factory.yaml")
  void testFactory(final YamlTestExecutor testCase) {
    run(testCase);
  }

  @YamlTest("fhirpath-js/cases/fhir-r4.yaml")
  void testFhirR4(final YamlTestExecutor testCase) {
    run(testCase);
  }

  @YamlTest("fhirpath-js/cases/fhir-quantity.yaml")
  void testFhirQuantity(final YamlTestExecutor testCase) {
    run(testCase);
  }

  @YamlTest("fhirpath-js/cases/hasValue.yaml")
  void testHasValue(final YamlTestExecutor testCase) {
    run(testCase);
  }

  @YamlTest("fhirpath-js/cases/simple.yaml")
  void testSimple(final YamlTestExecutor testCase) {
    run(testCase);
  }
}
