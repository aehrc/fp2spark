package com.example.fhirpath.compat.yaml;

import com.example.fhirpath.test.FhirTestEncoders;
import com.example.fhirpath.test.ResourceDatasetConverter;
import com.example.fhirpath.test.ResourceTestData;
import com.example.fhirpath.typing.ResourceType;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.hl7.fhir.instance.model.api.IBaseResource;

/**
 * Builds a Spark dataset + FHIRPath {@link ResourceType} for a YAML test case subject.
 *
 * <p>Supports four inputs:
 *
 * <ul>
 *   <li>{@code inputfile:} — a classpath JSON resource parsed as a HAPI FHIR resource
 *   <li>An embedded subject map with a valid FHIR {@code resourceType} — serialised then
 *       HAPI-parsed
 *   <li>An embedded subject map with a non-FHIR {@code resourceType} — schema inferred from the
 *       YAML map values, Dataset built via Spark JSON reader
 *   <li>No subject — a single-row dummy dataset with {@code null} resource type (literal-only
 *       evaluation)
 * </ul>
 */
public final class YamlSubjectFactory {

  private static final ObjectMapper JSON = new ObjectMapper();

  /**
   * Non-FHIR resource types for which arbitrary subject loading is enabled. Other non-FHIR types
   * will fall through to HAPI parsing which fails with a {@code TestAbortedException}, preserving
   * the previous skip behavior until those files are explicitly triaged.
   */
  private static final Set<String> ENABLED_ARBITRARY_SUBJECTS =
      Set.of("Functions", "Math", "MathTestData");

  private YamlSubjectFactory() {}

  /** A resolved subject: the input dataset plus the FHIRPath resource type (may be null). */
  public record ResolvedSubject(
      @Nonnull Dataset<Row> dataset, @Nullable ResourceType resourceType) {}

  /**
   * Resolves a subject for a YAML test case.
   *
   * @param spark active Spark session
   * @param defaultSubject the {@code subject:} map from the YAML file (may be null)
   * @param inputFile the {@code inputfile:} value from the test case (may be null)
   * @param resourceBase classpath prefix for {@code inputfile:} lookups (may be empty)
   */
  @Nonnull
  public static ResolvedSubject resolve(
      @Nonnull final SparkSession spark,
      @Nullable final Map<Object, Object> defaultSubject,
      @Nullable final String inputFile,
      @Nonnull final String resourceBase) {
    if (inputFile != null) {
      return loadInputFile(spark, inputFile, resourceBase);
    }
    if (defaultSubject != null && defaultSubject.get("resourceType") instanceof final String rt) {
      if (isFhirResourceType(rt)) {
        return loadFhirSubject(spark, defaultSubject);
      }
      if (ENABLED_ARBITRARY_SUBJECTS.contains(rt)) {
        return loadArbitrarySubject(spark, rt, defaultSubject);
      }
      // Fall through to FHIR parsing which will fail — the exception is caught by
      // DefaultYamlTestExecutor and converted to TestAbortedException (skip).
      return loadFhirSubject(spark, defaultSubject);
    }
    return new ResolvedSubject(spark.range(1).toDF(), null);
  }

  /**
   * Returns true if the given name is a known FHIR R4 resource type. Uses a broad catch because
   * HAPI may throw different exception types depending on the context version and input.
   */
  private static boolean isFhirResourceType(@Nonnull final String resourceTypeName) {
    try {
      FhirTestEncoders.FHIR_CONTEXT.getResourceDefinition(resourceTypeName);
      return true;
    } catch (final Exception e) {
      return false;
    }
  }

  /**
   * Loads an arbitrary (non-FHIR) subject by inferring the schema from the YAML map values and
   * creating a Spark Dataset via JSON.
   */
  @Nonnull
  private static ResolvedSubject loadArbitrarySubject(
      @Nonnull final SparkSession spark,
      @Nonnull final String resourceTypeName,
      @Nonnull final Map<Object, Object> subject) {
    // SnakeYAML produces Map<Object, Object>; convert to String keys for type inference
    final Map<String, Object> stringKeyedMap = toStringKeyedMap(subject);
    // resourceType is schema metadata, not a data field for FHIRPath evaluation
    stringKeyedMap.remove("resourceType");

    final ResourceTestData testData = ResourceTestData.of(resourceTypeName, stringKeyedMap);
    final ResourceType resourceType = testData.inferResourceType();
    // Use the explicit-type factory so toDataset() reuses the already-inferred ResourceType
    final Dataset<Row> dataset =
        ResourceDatasetConverter.toDataset(
            spark, ResourceTestData.of(resourceType, stringKeyedMap));
    return new ResolvedSubject(dataset, resourceType);
  }

  @Nonnull
  private static Map<String, Object> toStringKeyedMap(@Nonnull final Map<Object, Object> map) {
    final Map<String, Object> result = new LinkedHashMap<>();
    for (final Map.Entry<Object, Object> entry : map.entrySet()) {
      result.put(String.valueOf(entry.getKey()), entry.getValue());
    }
    return result;
  }

  @Nonnull
  private static ResolvedSubject loadInputFile(
      @Nonnull final SparkSession spark,
      @Nonnull final String inputFile,
      @Nonnull final String resourceBase) {
    final String classpathPath =
        resourceBase.isEmpty() ? inputFile : resourceBase + "/" + inputFile;
    final String json;
    try (var stream =
        YamlSubjectFactory.class.getClassLoader().getResourceAsStream(classpathPath)) {
      if (stream == null) {
        throw new IllegalArgumentException(
            "YAML inputfile not found on classpath: " + classpathPath);
      }
      json = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    } catch (final IOException e) {
      throw new IllegalStateException("Failed to read YAML inputfile: " + classpathPath, e);
    }
    return parseAndBuild(spark, json);
  }

  @Nonnull
  private static ResolvedSubject loadFhirSubject(
      @Nonnull final SparkSession spark, @Nonnull final Map<Object, Object> subject) {
    final String json;
    try {
      json = JSON.writeValueAsString(subject);
    } catch (final IOException e) {
      throw new IllegalStateException("Failed to serialise YAML subject to JSON", e);
    }
    return parseAndBuild(spark, json);
  }

  @Nonnull
  private static ResolvedSubject parseAndBuild(
      @Nonnull final SparkSession spark, @Nonnull final String json) {
    final IBaseResource resource =
        FhirTestEncoders.FHIR_CONTEXT.newJsonParser().parseResource(json);
    return new ResolvedSubject(
        FhirTestEncoders.toDataset(spark, resource), FhirTestEncoders.resourceTypeOf(resource));
  }
}
