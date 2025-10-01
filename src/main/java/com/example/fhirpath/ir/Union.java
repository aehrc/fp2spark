package com.example.fhirpath.ir;

import com.example.fhirpath.analyzer.FunctionSignature;
import com.example.fhirpath.analyzer.OverloadResolver;
import com.example.fhirpath.typing.Type;
import com.example.fhirpath.typing.TypeSystem;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.functions;

import java.util.List;

import static com.example.fhirpath.eval.EvalHelper.valueOf;

public record Union(IRNode left, IRNode right, Type resultType) implements IRNode {

    // Static signatures for Union operation - overloaded for all defined types
    public static final List<FunctionSignature> UNION_SIGNATURES =
            TypeSystem.definedTypes()
                    .map(FunctionSignature::biOperator)
                    .toList();

    // Factory method to resolve and create Union with proper types
    public static Union create(IRNode left, IRNode right) {
        OverloadResolver.ResolvedCall resolved =
                OverloadResolver.resolveBinary(UNION_SIGNATURES, left, right);
        return new Union(resolved.left(), resolved.right(), resolved.resultType());
    }

    @Override
    public Type getType() {
        return resultType;
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
