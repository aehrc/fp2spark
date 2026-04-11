package com.example.fhirpath.compat.yaml;

import com.example.fhirpath.compat.yaml.format.ExcludeRule;
import com.example.fhirpath.test.SparkSessionFactory;
import jakarta.annotation.Nonnull;
import org.apache.spark.sql.SparkSession;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.opentest4j.TestAbortedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class for YAML-driven FHIRPath reference compatibility tests.
 *
 * <p>Provides a cached Spark session (one per test class) and the {@link #run(YamlTestExecutor)}
 * entry point called from {@code @YamlTest} methods. Exclusion semantics mirror Pathling:
 *
 * <ul>
 *   <li>{@code outcome: null} (YAML absent) → test is skipped via {@link TestAbortedException}
 *   <li>{@code outcome: error} → exception expected; anything else fails
 *   <li>{@code outcome: failure} → assertion failure expected (XFAIL); unexpected pass fails
 *   <li>{@code outcome: pass} → test must still pass (regression guard)
 * </ul>
 *
 * <p>Whenever an excluded test completes its expected outcome, {@link TestAbortedException} is
 * thrown at the end so JUnit reports it as skipped rather than passed. That keeps "exclusion"
 * visible in test reports and matches Pathling's behaviour.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class YamlTestBase {

  private static final Logger log = LoggerFactory.getLogger(YamlTestBase.class);

  protected SparkSession spark;

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

  /** Entry point used by {@code @YamlTest} methods. */
  public void run(@Nonnull final YamlTestExecutor executor) {
    final var exclusionOpt = executor.getExclusion();
    if (exclusionOpt.isPresent() && exclusionOpt.get().getOutcome() == null) {
      throw new TestAbortedException("Skipped by YAML exclusion: " + exclusionOpt.get());
    }

    if (exclusionOpt.isEmpty()) {
      executor.check(spark);
      return;
    }

    final ExcludeRule rule = exclusionOpt.get();
    final String outcome = rule.getOutcome();
    try {
      executor.check(spark);
    } catch (final TestAbortedException e) {
      // Executor bailed out (e.g. unsupported variables) — propagate the skip regardless of
      // the declared outcome.
      throw e;
    } catch (final AssertionError e) {
      if (ExcludeRule.OUTCOME_FAILURE.equals(outcome)) {
        log.debug("[XFAIL] expected failure for {}: {}", executor.getDisplayName(), e.getMessage());
        throw new TestAbortedException("Expected failure (XFAIL) absorbed: " + rule, e);
      }
      throw e;
    } catch (final RuntimeException e) {
      if (ExcludeRule.OUTCOME_ERROR.equals(outcome)) {
        log.debug("[XERR] expected error for {}: {}", executor.getDisplayName(), e.toString());
        throw new TestAbortedException("Expected error absorbed: " + rule, e);
      }
      throw e;
    }

    if (!ExcludeRule.OUTCOME_PASS.equals(outcome)) {
      throw new AssertionError(
          "Excluded test passed unexpectedly (expected outcome="
              + outcome
              + "). Remove the exclusion: "
              + rule);
    }
    throw new TestAbortedException("Test passed but is flagged as pass-exclusion: " + rule);
  }
}
