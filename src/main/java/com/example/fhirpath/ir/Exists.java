package com.example.fhirpath.ir;

import com.example.fhirpath.typing.Type;
import org.apache.spark.sql.Column;

import static com.example.fhirpath.eval.EvalHelper.cons;
import static com.example.fhirpath.eval.EvalHelper.valueOf;
import static org.apache.spark.sql.functions.lit;

public record Exists(IRNode child) implements SingularIRNode {
    @Override
    public Type getType() {
        return Type.BOOLEAN;
    }

    @Override
    public Column eval() {
        return valueOf(child).applyNonNull(
                cons(true), // technically we should check for size
                cons(true),
                lit(false)
        );
    }
}
