package com.example.fhirpath.ir.string;

import com.example.fhirpath.analyzer.FunctionSignature;
import com.example.fhirpath.ir.IRNode;
import com.example.fhirpath.typing.Type;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.functions;

import javax.annotation.Nullable;
import java.util.List;

import static org.apache.spark.sql.functions.lit;

public record Substring(IRNode target, IRNode pos, @Nullable IRNode length) implements IRNode {

    public static final List<FunctionSignature> SIGNATURES = List.of(
            //  add minimal arrity 2 (STRING, INTEGER)
            new FunctionSignature(List.of(Type.STRING, Type.INTEGER, Type.INTEGER),
                    Type.STRING, 2)
    );

    @Override
    public Type getType() {
        return Type.STRING;
    }

    @Override
    public Column eval() {

        // try null save evaluation

        final Column targetColumn = target.eval();
        // adjust the offset
        final Column posColumn = pos.eval().plus(lit(1));

        // missing length is treated the same as empty length
        final Column lengthColumn = length != null ? length.eval() : functions.lit(null);
        final Column nonNullLengthColumn = functions.coalesce(lengthColumn, functions.lit(Integer.MAX_VALUE));

        final Column nullPropagationCondition = targetColumn.isNull()
                .or(posColumn.isNull());

        final Column posOutOfBoundsCondition = posColumn.leq(0)
                .or(posColumn.gt(functions.length(targetColumn)));

        final Column nullCondition = nullPropagationCondition
                .or(posOutOfBoundsCondition);

        return functions.when(functions.not(nullCondition),
                functions.substr(targetColumn, posColumn, nonNullLengthColumn));
    }
}
