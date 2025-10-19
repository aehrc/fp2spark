package com.example.fhirpath.codegen.spark;

import org.apache.spark.sql.Column;
import org.apache.spark.sql.functions;

import jakarta.annotation.Nonnull;

/**
 * Wrapper for FHIRPath Quantity values in Spark SQL.
 *
 * <p>In FHIRPath, a Quantity is a structured type containing a numeric value,
 * a unit of measure, and optional system and code fields. This record provides
 * typed access to Quantity fields and operations on Quantity values.
 *
 * <p>The underlying Spark representation is a struct with fields:
 * {@code value}, {@code unit}, {@code system}, {@code code}.
 *
 * @param target The Spark SQL column containing the Quantity struct
 */
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
