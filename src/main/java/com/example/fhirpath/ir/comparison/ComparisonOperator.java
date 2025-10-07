package com.example.fhirpath.ir.comparison;

import com.example.fhirpath.analyzer.FunctionSignature;
import com.example.fhirpath.ir.IRNode;
import com.example.fhirpath.typing.PrimitiveType;
import com.example.fhirpath.typing.Type;
import org.apache.spark.sql.Column;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.stream.Stream;

public interface ComparisonOperator extends IRNode {

    @Nonnull
    IRNode left();

    @Nonnull
    IRNode right();

    // comparable types
    List<FunctionSignature> SIGNATURES = Stream.of(
                    Type.INTEGER,
                    Type.DECIMAL,
                    Type.STRING,
                    Type.QUANTITY,
                    Type.DATE,
                    Type.DATE_TIME,
                    Type.TIME
            )
            .map(t -> FunctionSignature.biOperator(t, Type.BOOLEAN))
            .toList();

    @Override
    @Nonnull
    default Type getType() {
        return Type.BOOLEAN;
    }

    @Nonnull
    Column evalColumns(@Nonnull Column left, @Nonnull Column right);

    @Nonnull
    default PrimitiveType getInputType() {
        return (PrimitiveType) left().getType();
    }

    @Override
    @Nonnull
    default Column eval() {
        return evalColumns(left().eval(), right().eval());
    }
}
