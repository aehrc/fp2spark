package com.example.fhirpath.compat.yaml;

import static com.example.fhirpath.compat.yaml.YamlTestDefinition.TestCase.ANY_ERROR;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.fhirpath.FhirPath;
import com.example.fhirpath.compat.yaml.YamlSubjectFactory.ResolvedSubject;
import com.example.fhirpath.compat.yaml.YamlTestDefinition.TestCase;
import com.example.fhirpath.compat.yaml.format.ExcludeRule;
import com.example.fhirpath.typing.ResourceType;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.opentest4j.TestAbortedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs a single YAML-derived FHIRPath test case: compiles the expression, executes it against a
 * Spark dataset produced by {@link YamlSubjectFactory}, and compares the result to the expected
 * value declared in YAML.
 *
 * <p>Deliberately simpler than Pathling's {@code DefaultYamlTestExecutor}: fp2sql's result
 * extraction already produces plain Java values, so there is no need to build a parallel Spark
 * column for the expected value.
 */
public final class DefaultYamlTestExecutor implements YamlTestExecutor {

  private static final Logger log = LoggerFactory.getLogger(DefaultYamlTestExecutor.class);

  @Nonnull private final TestCase spec;
  @Nonnull private final YamlSubjectResolver subjectResolver;
  @Nonnull private final Optional<ExcludeRule> exclusion;
  @Nonnull private final String displayName;

  public DefaultYamlTestExecutor(
      @Nonnull final TestCase spec,
      @Nonnull final YamlSubjectResolver subjectResolver,
      @Nonnull final Optional<ExcludeRule> exclusion) {
    this.spec = spec;
    this.subjectResolver = subjectResolver;
    this.exclusion = exclusion;
    this.displayName = buildDisplayName(spec);
  }

  @Override
  @Nonnull
  public String getDisplayName() {
    return displayName;
  }

  @Override
  @Nonnull
  public Optional<ExcludeRule> getExclusion() {
    return exclusion;
  }

  @Override
  public void check(@Nonnull final SparkSession spark) {
    // fp2sql has no public hook for injecting YAML-level variables into the compiler; skip those
    // tests. The exclusion framework cannot match "has variables" from config.yaml alone.
    if (spec.variables() != null && !spec.variables().isEmpty()) {
      throw new TestAbortedException(
          "YAML test variables are not supported in fp2sql (expression: "
              + spec.expression()
              + ")");
    }

    final ResolvedSubject subject;
    try {
      subject = subjectResolver.resolve(spark, spec.inputFile());
    } catch (final Exception e) {
      // Subject loading can fail for synthetic (non-FHIR) resource types, resources unsupported
      // by Pathling's encoders (e.g. Bundle, StructureDefinition), or JSON parse errors. None of
      // those are fp2sql bugs in the FHIRPath compiler itself, so skip rather than fail.
      throw new TestAbortedException(
          "Subject load failed (expression: " + spec.expression() + "): " + e.getMessage(), e);
    }

    final Object actual;
    try {
      actual = evaluate(spark, subject);
    } catch (final Throwable t) {
      if (spec.isError()) {
        log.debug("Received expected error: {}", t.toString());
        if (!ANY_ERROR.equals(spec.errorMsg())) {
          final String rootCauseMsg = rootCauseMessage(t);
          assertTrue(
              rootCauseMsg != null && rootCauseMsg.contains(spec.errorMsg()),
              () ->
                  "Error message mismatch for expression '"
                      + spec.expression()
                      + "'. Expected to contain: '"
                      + spec.errorMsg()
                      + "', but got: '"
                      + rootCauseMsg
                      + "'");
        }
        return;
      }
      if (t instanceof RuntimeException re) {
        throw re;
      }
      if (t instanceof Error err) {
        throw err;
      }
      throw new RuntimeException(t);
    }

    if (spec.isError()) {
      throw new AssertionError(
          "Expected an error for expression '" + spec.expression() + "' but got: " + actual);
    }
    if (spec.isExpressionOnly()) {
      return;
    }

    assertResultEquals(spec.expression(), spec.result(), actual);
  }

  @Nullable
  private Object evaluate(
      @Nonnull final SparkSession spark, @Nonnull final ResolvedSubject subject) {
    final ResourceType resourceType = subject.resourceType();
    final String context = spec.context();
    final Column column;
    if (resourceType != null && context != null) {
      column = FhirPath.toColumn(spec.expression(), context, resourceType);
    } else if (resourceType != null) {
      column = FhirPath.toColumn(spec.expression(), resourceType);
    } else if (context != null) {
      column = FhirPath.toColumn(spec.expression(), context);
    } else {
      column = FhirPath.toColumn(spec.expression());
    }

    final Dataset<Row> result = subject.dataset().select(column.alias("result"));
    return extractResult(result);
  }

  @Nullable
  private static Object extractResult(@Nonnull final Dataset<Row> dataset) {
    final List<Row> rows = dataset.collectAsList();
    if (rows.isEmpty() || rows.get(0).isNullAt(0)) {
      return null;
    }
    return normaliseValue(rows.get(0).get(0));
  }

  @Nullable
  private static Object normaliseValue(@Nullable final Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof scala.collection.Seq<?> seq) {
      final List<?> javaList = scala.jdk.javaapi.CollectionConverters.asJava(seq);
      final List<Object> normalised = new ArrayList<>(javaList.size());
      for (final Object item : javaList) {
        normalised.add(normaliseValue(item));
      }
      return normalised;
    }
    if (value instanceof BigDecimal bd) {
      return bd.stripTrailingZeros();
    }
    return value;
  }

  /**
   * Compares expected (from YAML) to actual (from Spark). Both are normalised to a List before
   * element-wise comparison; a null actual and an empty-list expected are considered equal (both
   * mean "empty collection" in FHIRPath).
   */
  static void assertResultEquals(
      @Nonnull final String expression,
      @Nullable final Object expected,
      @Nullable final Object actual) {
    final List<Object> expectedList = toList(expected);
    final List<Object> actualList = toList(actual);
    assertEquals(
        expectedList.size(),
        actualList.size(),
        () ->
            "["
                + expression
                + "] Result size mismatch. Expected: "
                + expectedList
                + ", Actual: "
                + actualList);
    for (int i = 0; i < expectedList.size(); i++) {
      assertValueEquals(
          expression, expectedList.get(i), actualList.get(i), i, expectedList, actualList);
    }
  }

  @Nonnull
  private static List<Object> toList(@Nullable final Object value) {
    if (value == null) {
      return List.of();
    }
    if (value instanceof List<?> list) {
      return new ArrayList<>(list);
    }
    final List<Object> single = new ArrayList<>(1);
    single.add(value);
    return single;
  }

  private static void assertValueEquals(
      @Nonnull final String expression,
      @Nullable final Object expected,
      @Nullable final Object actual,
      final int index,
      @Nonnull final List<Object> expectedList,
      @Nonnull final List<Object> actualList) {
    if (expected == null || actual == null) {
      assertEquals(
          expected,
          actual,
          () ->
              "["
                  + expression
                  + "] Element "
                  + index
                  + " mismatch. Expected: "
                  + expectedList
                  + ", Actual: "
                  + actualList);
      return;
    }

    // Quantity comparison: YAML expects values like "2 'mo'" (UCUM) or "2 years" (calendar),
    // while fp2sql returns a struct Row(value, unit, system, code). Bridge by formatting the
    // Row in the FHIRPath Quantity literal shape.
    if (expected instanceof String expectedStr && actual instanceof Row actualRow) {
      final String formatted = tryFormatQuantity(actualRow);
      if (formatted != null) {
        assertEquals(
            expectedStr,
            formatted,
            () ->
                "["
                    + expression
                    + "] Element "
                    + index
                    + " quantity mismatch. Expected: "
                    + expectedStr
                    + ", Actual: "
                    + formatted);
        return;
      }
    }

    // Numeric comparison — YAML parses integers as Integer/Long and decimals as Double; fp2sql
    // returns Integer/Long/BigDecimal.
    if (expected instanceof Number expectedNum && actual instanceof Number actualNum) {
      final BigDecimal expectedBd = toBigDecimal(expectedNum);
      final BigDecimal actualBd = toBigDecimal(actualNum);
      assertTrue(
          expectedBd.compareTo(actualBd) == 0,
          () ->
              "["
                  + expression
                  + "] Element "
                  + index
                  + " numeric mismatch. Expected: "
                  + expectedBd.toPlainString()
                  + ", Actual: "
                  + actualBd.toPlainString());
      return;
    }

    assertEquals(
        expected,
        actual,
        () ->
            "["
                + expression
                + "] Element "
                + index
                + " mismatch. Expected: "
                + expectedList
                + ", Actual: "
                + actualList);
  }

  /**
   * Formats a Spark Row as a FHIRPath Quantity literal string. Returns {@code null} if the row does
   * not look like a Quantity struct (value + unit/code/system).
   *
   * <p>Handles two FHIRPath flavours:
   *
   * <ul>
   *   <li>UCUM units — {@code <value> '<code>'} (e.g. {@code "2 'mo'"})
   *   <li>Calendar durations — {@code <value> <unit>} (e.g. {@code "2 years"}), where the {@code
   *       system} is the FHIRPath calendar system
   * </ul>
   */
  @Nullable
  private static String tryFormatQuantity(@Nonnull final Row row) {
    final String[] names = row.schema().fieldNames();
    Object value = null;
    String unit = null;
    String system = null;
    String code = null;
    for (int i = 0; i < names.length; i++) {
      if (row.isNullAt(i)) {
        continue;
      }
      switch (names[i]) {
        case "value" -> value = row.get(i);
        case "unit" -> unit = String.valueOf(row.get(i));
        case "system" -> system = String.valueOf(row.get(i));
        case "code" -> code = String.valueOf(row.get(i));
        default -> {
          // ignore other fields
        }
      }
    }
    if (value == null || (unit == null && code == null)) {
      return null;
    }
    final BigDecimal valueBd =
        value instanceof BigDecimal bd ? bd.stripTrailingZeros() : new BigDecimal(value.toString());
    final String valueStr = valueBd.toPlainString();
    final boolean calendar = system != null && system.contains("fhirpath/calendar");
    if (calendar) {
      return valueStr + " " + (unit != null ? unit : code);
    }
    final String codeOrUnit = code != null ? code : unit;
    return valueStr + " '" + codeOrUnit + "'";
  }

  @Nonnull
  private static BigDecimal toBigDecimal(@Nonnull final Number number) {
    if (number instanceof BigDecimal bd) {
      return bd;
    }
    if (number instanceof Integer || number instanceof Long) {
      return BigDecimal.valueOf(number.longValue());
    }
    return BigDecimal.valueOf(number.doubleValue());
  }

  @Nullable
  private static String rootCauseMessage(@Nonnull final Throwable t) {
    Throwable current = t;
    while (current.getCause() != null && current.getCause() != current) {
      current = current.getCause();
    }
    return current.getMessage();
  }

  @Override
  public String toString() {
    return displayName;
  }

  @Nonnull
  private static String buildDisplayName(@Nonnull final TestCase spec) {
    final String description = spec.description();
    final String expr = spec.expression();
    final String label;
    if (description != null && !description.isBlank()) {
      // Include both description and expression for easy triage.
      label = description + " | " + expr;
    } else {
      label = expr;
    }
    // Normalise characters that confuse the JUnit parameterized display-name template.
    return label.replace('\n', ' ').replace('\r', ' ');
  }
}
