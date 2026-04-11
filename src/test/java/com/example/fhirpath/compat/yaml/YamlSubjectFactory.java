package com.example.fhirpath.compat.yaml;

import com.example.fhirpath.test.FhirTestEncoders;
import com.example.fhirpath.typing.ResourceType;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.hl7.fhir.instance.model.api.IBaseResource;

/**
 * Builds a Spark dataset + FHIRPath {@link ResourceType} for a YAML test case subject.
 *
 * <p>Supports three inputs:
 *
 * <ul>
 *   <li>{@code inputfile:} — a classpath JSON resource parsed as a HAPI FHIR resource
 *   <li>An embedded subject map with a {@code resourceType} field — serialised then HAPI-parsed
 *   <li>No subject — a single-row dummy dataset with {@code null} resource type (literal-only
 *       evaluation)
 * </ul>
 *
 * <p>Arbitrary (non-FHIR) subjects used by a handful of Pathling YAML tests are not supported —
 * those cases must be excluded via {@code config.yaml}.
 */
public final class YamlSubjectFactory {

  private static final ObjectMapper JSON = new ObjectMapper();

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
    if (defaultSubject != null && defaultSubject.get("resourceType") instanceof String) {
      return loadFhirSubject(spark, defaultSubject);
    }
    return new ResolvedSubject(spark.range(1).toDF(), null);
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
