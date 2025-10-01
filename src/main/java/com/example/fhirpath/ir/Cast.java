package com.example.fhirpath.ir;

import com.example.fhirpath.typing.Type;
import com.example.fhirpath.typing.SparkTypeMapper;
import org.apache.spark.sql.Column;

public class Cast implements IRNode {
    private final IRNode child;
    private final Type targetType;

    public Cast(IRNode child, Type targetType) {
        this.child = child;
        this.targetType = targetType;
    }

    public IRNode child() { return child; }

    @Override
    public Type getType() { return targetType; }

    @Override
    public Column eval() {
        String sparkType = SparkTypeMapper.toSparkTypeName(targetType);
        return child.eval().cast(sparkType);
    }
}

