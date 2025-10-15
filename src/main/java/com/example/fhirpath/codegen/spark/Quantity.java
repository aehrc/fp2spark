package com.example.fhirpath.codegen.spark;

import org.apache.spark.sql.Column;
import org.apache.spark.sql.functions;

import javax.annotation.Nonnull;

public record Quantity(@Nonnull Column target) {
    @Nonnull
    public Column abs() {
        // either call a UDF or construct the expression
        return functions.struct(
                functions.abs(value()).alias("value"),
                unit().alias("unit"),
                system().alias("system"),
                code().alias("code")
        );
    }

    @Nonnull
    public Column value() {
        return target.getField("value");
    }

    @Nonnull
    public Column unit() {
        return target.getField("unit");
    }

    @Nonnull
    public Column system() {
        return target.getField("system");
    }

    @Nonnull
    public Column code() {
        return target.getField("code");
    }

    @Nonnull
    public static Quantity quantity(@Nonnull final Column quantityColumn) {
        return new Quantity(quantityColumn);
    }

    @Nonnull
    public Column gt(@Nonnull final Quantity quantity) {
        throw new UnsupportedOperationException("Not supported yet: Quantity::gt");
    }

    @Nonnull
    public Column lt(@Nonnull final Quantity quantity) {
        throw new UnsupportedOperationException("Not supported yet: Quantity::lt");
    }

    @Nonnull
    public Column geq(@Nonnull final Quantity quantity) {
        throw new UnsupportedOperationException("Not supported yet: Quantity::geq");
    }

    public Column plus(Quantity quantity) {
        throw new UnsupportedOperationException("Not supported yet: Quantity::plus");
    }

    public Column divide(Quantity quantity) {
        throw new UnsupportedOperationException("Not supported yet: Quantity::divide");
    }
}
