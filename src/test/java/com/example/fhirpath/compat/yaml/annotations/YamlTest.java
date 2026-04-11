package com.example.fhirpath.compat.yaml.annotations;

import com.example.fhirpath.compat.yaml.YamlTestArgumentProvider;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;

/**
 * Marks a test method as a YAML-driven FHIRPath reference test.
 *
 * <p>The method is expanded into a JUnit 5 parameterized test, one per YAML test case in the
 * referenced file. The method must accept a single {@link
 * com.example.fhirpath.compat.yaml.YamlTestExecutor} argument and delegate to {@code run(executor)}
 * on its enclosing {@link com.example.fhirpath.compat.yaml.YamlTestBase}.
 *
 * @see YamlTestConfiguration for class-level configuration (config.yaml, resource base)
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@ParameterizedTest(name = "{0}")
@ArgumentsSource(YamlTestArgumentProvider.class)
public @interface YamlTest {

  /** Classpath resource path to the YAML test file. */
  String value();
}
