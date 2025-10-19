package com.example.fhirpath.codegen.spark;

import org.apache.spark.sql.Column;

import jakarta.annotation.Nonnull;

/**
 * Wrapper for FHIRPath Time values in Spark SQL.
 *
 * <p>Provides typed operations for FHIRPath time values, including
 * comparisons and conversions. The underlying Spark representation
 * stores time values according to the FHIRPath specification.
 *
 * @param target The Spark SQL column containing the time value
 */
public record Time(@Nonnull Column target) {
    @Nonnull
    public static Time time(@Nonnull final Column timeColumn) {
        return new Time(timeColumn);
    }

    @Nonnull
    public Column gt(@Nonnull final Time other) {
        throw new UnsupportedOperationException("Not supported yet: Time::gt");
    }

    @Nonnull
    public Column lt(@Nonnull final Time other) {
        throw new UnsupportedOperationException("Not supported yet: Time::lt");
    }

    @Nonnull
    public Column geq(@Nonnull final Time other) {
        throw new UnsupportedOperationException("Not supported yet: Time::geq");
    }
}
