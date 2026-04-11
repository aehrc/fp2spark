package com.example.fhirpath.compat.yaml.format;

import com.example.fhirpath.compat.yaml.YamlTestDefinition.TestCase;
import jakarta.annotation.Nullable;
import java.nio.file.FileSystems;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * A named group of exclusion rules scoped by a file-name glob.
 *
 * <p>Ported from Pathling's {@code au.csiro.pathling.test.yaml.format.ExcludeSet}. Public fields
 * with setters are required for SnakeYAML's JavaBean deserialization.
 */
public class ExcludeSet {

  @Nullable private String title;
  @Nullable private String comment;
  @Nullable private String glob;
  @Nullable private List<ExcludeRule> exclude;

  @Nullable
  public String getTitle() {
    return title;
  }

  public void setTitle(@Nullable final String title) {
    this.title = title;
  }

  @Nullable
  public String getComment() {
    return comment;
  }

  public void setComment(@Nullable final String comment) {
    this.comment = comment;
  }

  @Nullable
  public String getGlob() {
    return glob;
  }

  public void setGlob(@Nullable final String glob) {
    this.glob = glob;
  }

  @Nullable
  public List<ExcludeRule> getExclude() {
    return exclude;
  }

  public void setExclude(@Nullable final List<ExcludeRule> exclude) {
    this.exclude = exclude;
  }

  /**
   * Returns {@code true} when this set's glob matches the basename of the given test file path.
   * Unset or empty globs match everything.
   */
  boolean matchesFile(final String testFilePath) {
    if (glob == null || glob.isEmpty()) {
      return true;
    }
    final PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + glob);
    // Match the basename rather than the full classpath path so "*.yaml" works as expected.
    return matcher.matches(Paths.get(Paths.get(testFilePath).getFileName().toString()));
  }

  /** Finds the first rule in this set that matches the given test case. */
  Optional<ExcludeRule> findMatch(final TestCase testCase) {
    if (exclude == null) {
      return Optional.empty();
    }
    for (final ExcludeRule rule : exclude) {
      final Predicate<TestCase> predicate = rule.toPredicate();
      if (predicate.test(testCase)) {
        return Optional.of(rule);
      }
    }
    return Optional.empty();
  }
}
