package com.example.fhirpath.ir;

import com.example.fhirpath.typing.Type;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.functions;

import static com.example.fhirpath.eval.EvalHelper.cons;
import static com.example.fhirpath.eval.EvalHelper.valueOf;
import static org.apache.spark.sql.functions.lit;

public record Count(IRNode child) implements SingularIRNode {
    @Override
    public Type getType() {
        return Type.INTEGER;
    }

    @Override
    public Column eval() {
        return valueOf(child).applyNonNull(
                functions::size,
                cons(1),
                lit(0)
        );
    }
}
