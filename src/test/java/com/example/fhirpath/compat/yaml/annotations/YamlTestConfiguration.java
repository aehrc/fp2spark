package com.example.fhirpath.compat.yaml.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Class-level configuration for YAML-driven FHIRPath reference tests.
 *
 * <p>Specifies the exclusion config file and the base directory for {@code inputfile:} resource
 * references used by test cases.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface YamlTestConfiguration {

  /** Classpath resource path to the exclusion {@code config.yaml}. Empty disables exclusions. */
  String config() default "";

  /** Classpath resource base for {@code inputfile:} references. */
  String resourceBase() default "";
}
