package com.example.fhirpath.compat.yaml;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.example.fhirpath.compat.yaml.YamlSubjectFactory.ResolvedSubject;
import com.example.fhirpath.test.SparkSessionFactory;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.spark.sql.SparkSession;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * Verifies that {@link YamlSubjectResolver} resolves each distinct {@code inputfile} (or the
 * default/no-subject case) exactly once per instance.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class YamlSubjectResolverTest {

  private static final String RESOURCE_BASE = "fhirpath-js/resources";

  private SparkSession spark;

  @BeforeAll
  void setupSpark() {
    spark = SparkSessionFactory.createTestSession();
  }

  @AfterAll
  void teardownSpark() {
    if (spark != null) {
      spark.stop();
    }
  }

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
