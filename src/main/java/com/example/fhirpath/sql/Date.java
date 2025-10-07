package com.example.fhirpath.sql;

import org.apache.spark.sql.Column;

import javax.annotation.Nonnull;

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
