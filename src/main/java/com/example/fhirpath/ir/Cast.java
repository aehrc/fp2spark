package com.example.fhirpath.ir;

import com.example.fhirpath.typing.SparkTypeMapper;
import com.example.fhirpath.typing.Type;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.types.DataType;

import static com.example.fhirpath.eval.EvalHelper.valueOf;

public record Cast(IRNode child, Type targetType) implements IRNode {
    @Override
    public Type getType() {
        return targetType;
    }

    @Override
    public boolean isSingular() {
        return !targetType.isCollection();
    }

    @Override
    public Column eval() {
        final DataType sparkType = SparkTypeMapper.toSparkDataType(targetType);
        // Cast the column to the target Spark type
        // valueOf handles both singular and collection cases
        return valueOf(child).apply(
                a -> a.cast(sparkType),
                s -> s.cast(sparkType)
        );
    }
}
