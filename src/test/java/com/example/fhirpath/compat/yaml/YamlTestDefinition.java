package com.example.fhirpath.compat.yaml;

import static java.util.Objects.nonNull;
import static java.util.Objects.requireNonNull;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

/**
 * Parsed representation of a YAML FHIRPath test file.
 *
 * <p>Ported from Pathling's {@code au.csiro.pathling.test.yaml.YamlTestDefinition}. Each YAML file
 * contains an optional {@code subject} (a FHIR resource or arbitrary object) and a list of {@code
 * tests}, which may be organised into nested groups.
 */
@SuppressWarnings("unchecked")
public record YamlTestDefinition(
    @Nullable Map<Object, Object> subject, @Nonnull List<TestCase> cases) {

  private static final Logger log = LoggerFactory.getLogger(YamlTestDefinition.class);

  /** Individual test case parsed from YAML. */
  public record TestCase(
      @Nullable String description,
      @Nonnull String expression,
      @Nullable String errorMsg,
      @Nullable Object result,
      @Nullable String inputFile,
      @Nullable String model,
      @Nullable String context,
      boolean disable,
      @Nullable Map<String, Object> variables) {

    /** Marker used when an error is expected but the message is unspecified. */
    public static final String ANY_ERROR = "*";

    public boolean isError() {
      return nonNull(errorMsg);
    }

    public boolean isExpressionOnly() {
      return errorMsg == null && result == null;
    }
  }

  @Nonnull
  public static YamlTestDefinition fromYaml(@Nonnull final String yamlData) {
    final Map<String, Object> yamlOM = new Yaml().load(yamlData);
    final Object rawSubject = yamlOM.get("subject");
    // Some fhirpath.js YAML files use a list-valued subject which fp2sql cannot translate into
    // a FHIR resource. Tolerate that by ignoring the subject and letting the individual test
    // cases fail or be excluded.
    final Map<Object, Object> subjectMap =
        rawSubject instanceof Map<?, ?> m ? (Map<Object, Object>) m : null;
    return new YamlTestDefinition(
        subjectMap, buildCases((List<Object>) requireNonNull(yamlOM.get("tests"))));
  }

  @Nonnull
  private static List<TestCase> buildCases(@Nonnull final Collection<Object> cases) {
    return buildCases(cases, null);
  }

  @Nonnull
  private static List<TestCase> buildCases(
      @Nonnull final Collection<Object> cases, @Nullable final String groupName) {
    return cases.stream()
        .map(c -> (Map<Object, Object>) c)
        .flatMap(caseOrGroup -> mapCaseOrGroup(caseOrGroup, groupName))
        .toList();
  }

  @Nonnull
  private static Stream<TestCase> mapCaseOrGroup(
      @Nonnull final Map<Object, Object> caseOrGroup, @Nullable final String groupName) {
    if (caseOrGroup.containsKey("expression")) {
      final List<String> expressions = toExpressions(requireNonNull(caseOrGroup.get("expression")));
      return expressions.stream()
          .map(
              expr ->
                  new TestCase(
                      createDescription((String) caseOrGroup.get("desc"), groupName),
                      expr,
                      (boolean) caseOrGroup.getOrDefault("error", false)
                          ? TestCase.ANY_ERROR
                          : null,
                      caseOrGroup.get("result"),
                      (String) caseOrGroup.get("inputfile"),
                      (String) caseOrGroup.get("model"),
                      (String) caseOrGroup.get("context"),
                      (boolean) caseOrGroup.getOrDefault("disable", false),
                      (Map<String, Object>) caseOrGroup.get("variables")));
    } else if (caseOrGroup.size() == 1) {
      final String currentGroupName = String.valueOf(caseOrGroup.keySet().iterator().next());
      final Object singleValue = caseOrGroup.values().iterator().next();
      return singleValue instanceof final List<?> lst
          ? buildCases((List<Object>) lst, currentGroupName).stream()
          : Stream.empty();
    }
    return Stream.empty();
  }

  @Nonnull
  private static List<String> toExpressions(@Nonnull final Object expressionObj) {
    if (expressionObj instanceof String s) {
      return List.of(s);
    } else if (expressionObj instanceof List<?> l) {
      return (List<String>) l;
    } else {
      log.warn("Unexpected expression object: {}", expressionObj);
      return List.of("FAIL: " + expressionObj);
    }
  }

  @Nullable
  private static String createDescription(
      @Nullable final String description, @Nullable final String groupName) {
    if (groupName != null && description != null) {
      return groupName + " - " + description;
    } else if (groupName != null) {
      return groupName;
    }
    return description;
  }
}
