package com.example.fhirpath.codegen.spark.handler;

import jakarta.annotation.Nullable;
import org.apache.spark.sql.SparkSession;

/**
 * Context for code generation, passed to all handlers.
 * <p>
 * SparkSession is optional; handlers that do not require UDF registration can work with null.
 *
 * @param spark the Spark session, or null if UDF registration is not needed
 */
public record CodeGenContext(@Nullable SparkSession spark) {
}
