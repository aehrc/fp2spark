package com.example.fhirpath.codegen.spark.handler;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as a handler for a FHIRPath operation.
 * <p>
 * The method will be invoked when the specified operation needs code generation.
 * Arguments are automatically boxed from SparkSQL Column to domain wrappers.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Operation {
    /**
     * The FHIRPath operation name this method handles.
     */
    String value();
}
