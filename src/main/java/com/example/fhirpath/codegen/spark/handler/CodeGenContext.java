package com.example.fhirpath.codegen.spark.handler;

import jakarta.annotation.Nullable;
import org.apache.spark.sql.SparkSession;

/**
 * Context for code generation, passed to all handlers.
 * <p>
 * Currently minimal - just holds SparkSession for creating UDFs if needed.
 * SparkSession is optional; handlers that don't need UDFs can work with null.
 * Can be extended with additional context as needed (e.g., resource schema).
 */
public record CodeGenContext(@Nullable SparkSession spark) {

    /**
     * Creates a code generation context.
     *
     * @param spark The Spark session (nullable for handlers that don't need UDFs)
     */
    public CodeGenContext {
        // Compact constructor - validation if needed
    }
}
