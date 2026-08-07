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

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import au.csiro.fhirpath.compat.yaml.YamlSubjectFactory.ResolvedSubject;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Verifies that {@link YamlSubjectResolver} resolves each distinct {@code inputfile} (or the
 * default/no-subject case) exactly once per instance.
 */
class YamlSubjectResolverTest extends YamlTestBase {

  private static final String RESOURCE_BASE = "fhirpath-js/resources";

  @Test
  void reusesResolvedSubjectForNoSubjectPath() {
    final YamlSubjectResolver resolver = new YamlSubjectResolver(null, "");

    final ResolvedSubject first = resolver.resolve(spark, null);
    final ResolvedSubject second = resolver.resolve(spark, null);

    assertSame(first, second);
  }

  @Test
  void reusesResolvedSubjectForDefaultFhirSubject() {
    final Map<Object, Object> subject = new LinkedHashMap<>();
    subject.put("resourceType", "Patient");
    subject.put("id", "example");
    final YamlSubjectResolver resolver = new YamlSubjectResolver(subject, "");

    final ResolvedSubject first = resolver.resolve(spark, null);
    final ResolvedSubject second = resolver.resolve(spark, null);

    assertSame(first, second);
  }

  @Test
  void reusesResolvedSubjectForSameInputFile() {
    final YamlSubjectResolver resolver = new YamlSubjectResolver(null, RESOURCE_BASE);

    final ResolvedSubject first = resolver.resolve(spark, "patient-example.json");
    final ResolvedSubject second = resolver.resolve(spark, "patient-example.json");

    assertSame(first, second);
  }

  @Test
  void returnsDistinctSubjectsForDifferentInputFiles() {
    final YamlSubjectResolver resolver = new YamlSubjectResolver(null, RESOURCE_BASE);

    final ResolvedSubject patient = resolver.resolve(spark, "patient-example.json");
    final ResolvedSubject observation = resolver.resolve(spark, "observation-example.json");

    assertNotSame(patient, observation);
  }
}
