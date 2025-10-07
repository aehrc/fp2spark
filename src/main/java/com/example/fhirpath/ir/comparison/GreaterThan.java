package com.example.fhirpath.ir.comparison;

import com.example.fhirpath.ir.IRNode;
import org.apache.spark.sql.Column;

import javax.annotation.Nonnull;

import static com.example.fhirpath.sql.Date.date;
import static com.example.fhirpath.sql.DateTime.dateTime;
import static com.example.fhirpath.sql.Quantity.quantity;
import static com.example.fhirpath.sql.Time.time;

public record GreaterThan(IRNode left, IRNode right) implements ComparisonOperator {

    @Override
    @Nonnull
    public Column evalColumns(@Nonnull Column left, @Nonnull Column right) {
        return switch (getInputType()) {
            case INTEGER, DECIMAL, STRING -> left.gt(right);
            case QUANTITY -> quantity(left).gt(quantity(right));
            case DATE_TIME -> dateTime(left).gt(dateTime(right));
            case DATE -> date(left).gt(date(right));
            case TIME -> time(left).gt(time(right));
            default -> throw new IllegalArgumentException("Unsupported type for Greater: " + getInputType());
        };
    }
}
