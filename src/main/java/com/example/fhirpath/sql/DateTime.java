package com.example.fhirpath.sql;

import org.apache.spark.sql.Column;

import javax.annotation.Nonnull;

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
