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
package au.csiro.fhirpath.compat.yaml.annotations;

import au.csiro.fhirpath.compat.yaml.YamlTestArgumentProvider;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;

/**
 * Marks a test method as a YAML-driven FHIRPath reference test.
 *
 * <p>The method is expanded into a JUnit 5 parameterized test, one per YAML test case in the
 * referenced file. The method must accept a single {@link
 * au.csiro.fhirpath.compat.yaml.YamlTestExecutor} argument and delegate to {@code run(executor)} on
 * its enclosing {@link au.csiro.fhirpath.compat.yaml.YamlTestBase}.
 *
 * @see YamlTestConfiguration for class-level configuration (config.yaml, resource base)
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@ParameterizedTest(name = "{0}")
@ArgumentsSource(YamlTestArgumentProvider.class)
public @interface YamlTest {

  /** Classpath resource path to the YAML test file. */
  String value();
}
