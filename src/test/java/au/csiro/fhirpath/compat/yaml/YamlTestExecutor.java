package au.csiro.fhirpath.compat.yaml;

import au.csiro.fhirpath.compat.yaml.format.ExcludeRule;
import jakarta.annotation.Nonnull;
import java.util.Optional;
import org.apache.spark.sql.SparkSession;

/** Runs a single YAML-derived FHIRPath test case against a Spark session. */
public interface YamlTestExecutor {

  /** Human-readable display name for this test case (shown in JUnit output). */
  @Nonnull
  String getDisplayName();

  /** The exclusion rule matching this test case, if any. */
  @Nonnull
  Optional<ExcludeRule> getExclusion();

  /** Executes the test case. May throw AssertionError / RuntimeException on failure. */
  void check(@Nonnull SparkSession spark);
}
