package com.example.fhirpath.ir;

import com.example.fhirpath.typing.SparkTypeMapper;
import com.example.fhirpath.typing.Type;
import com.example.fhirpath.typing.fhir.FhirType;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.types.DataType;
import org.apache.spark.sql.types.DataTypes;

import static com.example.fhirpath.eval.EvalHelper.valueOf;

public record GetValue(IRNode child) implements IRNode {
    @Override
    public Type getType() {
        return ((FhirType) child.getType()).systemType();
    }

    @Override
    public boolean isSingular() {
        return child.isSingular();
    }

    @Override
    public Column eval() {
        final DataType sparkType = SparkTypeMapper.toSparkDataType(getType());
        return valueOf(child).apply(
                a -> a.cast(DataTypes.createArrayType(sparkType)),
                s -> s.cast(sparkType)
        );
    }
}
