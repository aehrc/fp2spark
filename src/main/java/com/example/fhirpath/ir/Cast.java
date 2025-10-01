package com.example.fhirpath.ir;

import com.example.fhirpath.typing.Type;
import com.example.fhirpath.typing.SparkTypeMapper;
import org.apache.spark.sql.Column;

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
        String sparkType = SparkTypeMapper.toSparkTypeName(targetType);
        return child.eval().cast(sparkType);
    }
}
