package au.csiro.fhirpath.compat.yaml.format;

import au.csiro.fhirpath.compat.yaml.YamlTestDefinition.TestCase;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

/**
 * Parsed representation of a {@code config.yaml} exclusion file.
 *
 * <p>Ported from Pathling's {@code au.csiro.pathling.test.yaml.format.YamlTestFormat}. Public
 * fields with setters are required for SnakeYAML's JavaBean deserialization.
 */
public class YamlTestFormat {

  private static final ConcurrentMap<String, YamlTestFormat> CACHE = new ConcurrentHashMap<>();

  @Nullable private List<ExcludeSet> excludeSet;

  @Nullable
  public List<ExcludeSet> getExcludeSet() {
    return excludeSet;
  }

  public void setExcludeSet(@Nullable final List<ExcludeSet> excludeSet) {
    this.excludeSet = excludeSet;
  }

  /**
   * Finds the first exclusion rule (across all sets whose glob matches {@code testFilePath}) that
   * applies to the given test case.
   */
  @Nonnull
  public Optional<ExcludeRule> findExclusion(
      @Nonnull final String testFilePath, @Nonnull final TestCase testCase) {
    if (excludeSet == null) {
      return Optional.empty();
    }
    for (final ExcludeSet set : excludeSet) {
      if (!set.matchesFile(testFilePath)) {
        continue;
      }
      final Optional<ExcludeRule> match = set.findMatch(testCase);
      if (match.isPresent()) {
        return match;
      }
    }
    return Optional.empty();
  }

  @Nonnull
  public static YamlTestFormat fromYaml(@Nonnull final String yamlData) {
    final LoaderOptions options = new LoaderOptions();
    options.setAllowDuplicateKeys(true);
    options.setMaxAliasesForCollections(1000);
    final Constructor constructor = new Constructor(YamlTestFormat.class, options);
    final YamlTestFormat loaded = new Yaml(constructor).loadAs(yamlData, YamlTestFormat.class);
    return loaded != null ? loaded : getDefault();
  }

  /**
   * Returns a cached {@link YamlTestFormat} for {@code cacheKey}. {@code loader} is invoked only on
   * the first call per key — subsequent lookups share the parsed rules so the per-method JUnit
   * argument provider does not re-parse a 600-line config file 27 times per run.
   */
  @Nonnull
  public static YamlTestFormat cached(
      @Nonnull final String cacheKey, @Nonnull final java.util.function.Supplier<String> loader) {
    return CACHE.computeIfAbsent(cacheKey, k -> fromYaml(loader.get()));
  }

  @Nonnull
  public static YamlTestFormat getDefault() {
    final YamlTestFormat f = new YamlTestFormat();
    f.setExcludeSet(Collections.emptyList());
    return f;
  }
}
