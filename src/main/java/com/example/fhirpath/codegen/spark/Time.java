package com.example.fhirpath.codegen.spark;

import org.apache.spark.sql.Column;

import javax.annotation.Nonnull;

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
