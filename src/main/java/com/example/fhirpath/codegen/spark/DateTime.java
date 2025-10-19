package com.example.fhirpath.codegen.spark;

import org.apache.spark.sql.Column;

import jakarta.annotation.Nonnull;

/**
 * Wrapper for FHIRPath DateTime values in Spark SQL.
 *
 * <p>Provides typed operations for FHIRPath datetime values (dates with time),
 * including comparisons, conversions, and arithmetic. The underlying Spark
 * representation stores datetime values according to the FHIRPath specification.
 *
 * @param target The Spark SQL column containing the datetime value
 */
public record DateTime(@Nonnull Column target) {
    @Nonnull
    public static DateTime dateTime(@Nonnull final Column dateTimeColumn) {
        return new DateTime(dateTimeColumn);
    }

    @Nonnull
    public Column gt(@Nonnull final DateTime other) {
        throw new UnsupportedOperationException("Not supported yet: DateTime::gt");
    }

    @Nonnull
    public Column lt(@Nonnull final DateTime other) {
        throw new UnsupportedOperationException("Not supported yet: DateTime::lt");
    }

    @Nonnull
    public Column geq(@Nonnull final DateTime other) {
        throw new UnsupportedOperationException("Not supported yet: DateTime::geq");
    }

    public Column plus(@Nonnull final Quantity quantity) {
        throw new UnsupportedOperationException("Not supported yet: DateTime::plus");
    }
}
