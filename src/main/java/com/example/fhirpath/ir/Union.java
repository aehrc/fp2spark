package com.example.fhirpath.ir;

import com.example.fhirpath.analyzer.FunctionSignature;
import com.example.fhirpath.typing.CollectionType;
import com.example.fhirpath.typing.Type;
import com.example.fhirpath.typing.TypeSystem;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.functions;

import java.util.List;

import static com.example.fhirpath.eval.EvalHelper.valueOf;

public record Union(IRNode left, IRNode right) implements IRNode {

    // Static signatures for Union operation - overloaded for all defined types
    public static final List<FunctionSignature> SIGNATURES =
            TypeSystem.definedTypes()
                    .map(CollectionType::new)
                    .map(FunctionSignature::biOperator)
                    .toList();

    @Override
    public Type getType() {
        // we may get singluar value here collection but the result is always a collection
        // except for null cases.
        return new CollectionType(left.getType());
    }

    @Override
    public Column eval() {
        // NOTE: THIS only works for primitive SQL types
        // That do not need a custom comparator
        return functions.array_union(
                valueOf(left).asArray(),
                valueOf(right).asArray()
        );
    }
}
