package com.example.fhirpath.ir;

import com.example.fhirpath.typing.SparkTypeMapper;
import com.example.fhirpath.typing.Type;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.types.DataType;
import org.apache.spark.sql.types.DataTypes;

import static com.example.fhirpath.eval.EvalHelper.valueOf;

public record Cast(IRNode child, Type targetType) implements IRNode {
    @Override
    public Type getType() {
        return targetType;
    }

    @Override
    public boolean isSingular() {
        return child.isSingular();
    }

    @Override
    public Column eval() {
        final DataType sparkType = SparkTypeMapper.toSparkDataType(targetType);
        // TODO: This only works for primitive SQL types.
        return valueOf(child).apply(
                a -> a.cast(DataTypes.createArrayType(sparkType)),
                s -> s.cast(sparkType)
        );
    }
}
