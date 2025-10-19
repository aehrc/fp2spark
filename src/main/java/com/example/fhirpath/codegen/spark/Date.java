package com.example.fhirpath.codegen.spark;

import org.apache.spark.sql.Column;

import jakarta.annotation.Nonnull;

/**
 * Wrapper for FHIRPath Date values in Spark SQL.
 *
 * <p>Provides typed operations for FHIRPath date values (dates without time),
 * including comparisons and conversions. The underlying Spark representation
 * stores date values according to the FHIRPath specification.
 *
 * @param target The Spark SQL column containing the date value
 */
public record Date(@Nonnull Column target) {
    @Nonnull
    public static Date date(@Nonnull final Column dateTimeColumn) {
        return new Date(dateTimeColumn);
    }

    @Nonnull
    public Column gt(@Nonnull final Date other) {
        throw new UnsupportedOperationException("Not supported yet: Date::gt");
    }

    @Nonnull
    public Column lt(@Nonnull final Date other) {
        throw new UnsupportedOperationException("Not supported yet: Date::lt");
    }

    @Nonnull
    public Column geq(@Nonnull final Date other) {
        throw new UnsupportedOperationException("Not supported yet: Date::geq");
    }

}
