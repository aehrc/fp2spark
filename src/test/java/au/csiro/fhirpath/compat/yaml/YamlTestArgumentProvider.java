package au.csiro.fhirpath.compat.yaml;

import au.csiro.fhirpath.compat.yaml.annotations.YamlTest;
import au.csiro.fhirpath.compat.yaml.annotations.YamlTestConfiguration;
import au.csiro.fhirpath.compat.yaml.format.ExcludeRule;
import au.csiro.fhirpath.compat.yaml.format.YamlTestFormat;
import jakarta.annotation.Nonnull;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;

/**
 * Junit 5 {@link ArgumentsProvider} backing the {@link YamlTest} annotation.
 *
 * <p>Loads the referenced YAML test file, parses it into a {@link YamlTestDefinition}, applies the
 * class-level {@link YamlTestConfiguration} to resolve exclusions, and emits one {@link Arguments}
 * per non-disabled test case containing a {@link DefaultYamlTestExecutor}.
 */
public class YamlTestArgumentProvider implements ArgumentsProvider {

  @Override
  public Stream<? extends Arguments> provideArguments(@Nonnull final ExtensionContext context)
      throws Exception {
    final YamlTest annotation = context.getRequiredTestMethod().getAnnotation(YamlTest.class);
    if (annotation == null) {
      throw new IllegalStateException("Missing @YamlTest annotation");
    }
    final String testFilePath = annotation.value();
    final YamlTestConfiguration config =
        context.getRequiredTestClass().getAnnotation(YamlTestConfiguration.class);
    final String configPath = config != null ? config.config() : "";
    final String resourceBase = config != null ? config.resourceBase() : "";

    final YamlTestDefinition definition = YamlTestDefinition.fromYaml(loadResource(testFilePath));
    final YamlTestFormat format =
        configPath.isEmpty()
            ? YamlTestFormat.getDefault()
            : YamlTestFormat.cached(configPath, () -> loadResourceUnchecked(configPath));
    final YamlSubjectResolver subjectResolver =
        new YamlSubjectResolver(definition.subject(), resourceBase);

    return definition.cases().stream()
        .filter(tc -> !tc.disable())
        .map(
            tc -> {
              final Optional<ExcludeRule> exclusion = format.findExclusion(testFilePath, tc);
              final DefaultYamlTestExecutor executor =
                  new DefaultYamlTestExecutor(tc, subjectResolver, exclusion);
              return Arguments.of((Object) executor);
            });
  }

  @Nonnull
  private static String loadResource(@Nonnull final String path) throws IOException {
    try (var stream = YamlTestArgumentProvider.class.getClassLoader().getResourceAsStream(path)) {
      if (stream == null) {
        throw new IllegalArgumentException("YAML resource not found on classpath: " + path);
      }
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  @Nonnull
  private static String loadResourceUnchecked(@Nonnull final String path) {
    try {
      return loadResource(path);
    } catch (final IOException e) {
      throw new IllegalStateException("Failed to load YAML resource: " + path, e);
    }
  }
}
